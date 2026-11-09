package de.peakverse.peakadmin;

import com.google.gson.JsonObject;
import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

/** PeakAdmin: Server-Verwaltung im Web (Spieler, Konsole, Sicherungen und Neustart, Status, Chat). */
public final class PeakAdminModule implements PeakModule, Listener {
    private ModuleContext context;
    private StatsHistory stats;
    private ChatLog chat;
    private MuteStore mutes;
    private ServerControl control;
    private ItemGuard guard;
    private BukkitTask sampler;
    private BukkitTask minuteTask;
    private long lastBackup = System.currentTimeMillis();

    @Override
    public String id() {
        return "peakadmin";
    }

    @Override
    public String displayName() {
        return "PeakAdmin";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        File data = context.dataFolder();
        stats = new StatsHistory(Math.max(10, context.config().getInt("status.history-samples", 360)));
        chat = new ChatLog(Math.max(50, context.config().getInt("chat.history", 300)));
        mutes = new MuteStore(data.toPath().resolve("mutes.json"));
        control = new ServerControl(context.host(), context.logger(), new BackupStore(backupFolder()));
        guard = new ItemGuard(data.toPath(), context.logger(), context.config().getBoolean("creative.separate-inventory", true), context.config().getInt("itemlog.keep", 2000));
        Bukkit.getPluginManager().registerEvents(this, context.host());
        Bukkit.getPluginManager().registerEvents(guard, context.host());
        sampler = Bukkit.getScheduler().runTaskTimer(context.host(), this::sample, 100L, 600L);
        minuteTask = Bukkit.getScheduler().runTaskTimer(context.host(), this::everyMinute, 1200L, 1200L);
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakAdminModule.class.getResourceAsStream("/modules/peakadmin/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                Path log = Path.of("logs", "latest.log");
                new AdminWeb(context.web(), stats, chat, mutes, new LogTail(log), control, guard, new AdminWeb.Settings() {
                    @Override
                    public JsonObject get() {
                        JsonObject o = new JsonObject();
                        o.addProperty("interval", context.config().getInt("backup.interval-hours", 0));
                        o.addProperty("keep", Math.max(1, context.config().getInt("backup.keep", 5)));
                        o.addProperty("dailyAt", context.config().getString("restart.daily-at", ""));
                        o.addProperty("warn", context.config().getInt("restart.warn-minutes", 5));
                        return o;
                    }

                    @Override
                    public void set(JsonObject o) throws IOException {
                        int interval = o.get("interval").getAsInt(), keep = o.get("keep").getAsInt(), warn = o.get("warn").getAsInt();
                        String daily = o.get("dailyAt").getAsString().strip();
                        if (interval < 0 || interval > 720) throw new IllegalArgumentException("Intervall: 0 bis 720 Stunden");
                        if (keep < 1 || keep > 200) throw new IllegalArgumentException("Behalten: 1 bis 200");
                        if (warn < 0 || warn > 60) throw new IllegalArgumentException("Vorwarnung: 0 bis 60 Minuten");
                        if (!daily.isEmpty() && !daily.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("Uhrzeit im Format HH:mm");
                        context.config().set("backup.interval-hours", interval);
                        context.config().set("backup.keep", keep);
                        context.config().set("restart.daily-at", daily);
                        context.config().set("restart.warn-minutes", warn);
                        context.config().save(new File(context.dataFolder(), "config.yml"));
                    }
                }).register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakAdmin nicht verfuegbar: " + e.getMessage());
            }
        }
    }

    private Path backupFolder() {
        String f = context.config().getString("backup.folder", "");
        return f == null || f.isBlank() ? context.dataFolder().toPath().resolve("backups") : Path.of(f);
    }

    private void sample() {
        Runtime rt = Runtime.getRuntime();
        int entities = 0, chunks = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            entities += w.getEntityCount();
            chunks += w.getChunkCount();
        }
        stats.add(new StatsHistory.Sample(System.currentTimeMillis(), Math.min(20.0, Bukkit.getTPS()[0]), Bukkit.getAverageTickTime(),
                rt.totalMemory() - rt.freeMemory(), rt.maxMemory(), Bukkit.getOnlinePlayers().size(), entities, chunks));
    }

    private void everyMinute() {
        int hours = context.config().getInt("backup.interval-hours", 0);
        if (hours > 0 && System.currentTimeMillis() - lastBackup >= hours * 3_600_000L) {
            lastBackup = System.currentTimeMillis();
            control.backupNow("auto", Math.max(1, context.config().getInt("backup.keep", 5)), null);
        }
        control.tickDaily(context.config().getString("restart.daily-at", ""), context.config().getInt("restart.warn-minutes", 5));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        MuteStore.Mute m = mutes.active(e.getPlayer().getUniqueId());
        if (m != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("Du bist stummgeschaltet" + (m.reason.isEmpty() ? "." : ": " + m.reason), NamedTextColor.RED));
            return;
        }
        chat.add(e.getPlayer().getName(), PlainTextComponentSerializer.plainText().serialize(e.message()));
    }

    @Override
    public void contributePack(PackSink sink) {
        // Keine Pack-Dateien
    }

    @Override
    public void disable() {
        if (sampler != null) sampler.cancel();
        if (minuteTask != null) minuteTask.cancel();
        if (control != null) control.shutdown();
        context = null;
    }
}