package de.peakverse.peakstats;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.bukkit.Bukkit;

/** PeakStats: zaehlt Spielzeit, Kaempfe und Bau-Werte in einer SQLite-Datenbank; Verwaltung fuer Admins im Web. */
public final class PeakStatsModule implements PeakModule {
    private ModuleContext context;
    private StatsDb db;
    private StatsCollector collector;
    private HeadStore heads;
    private final java.util.concurrent.atomic.AtomicBoolean livePending = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long liveLast;

    @Override
    public String id() {
        return "peakstats";
    }

    @Override
    public String displayName() {
        return "PeakStats";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        try {
            db = new StatsDb(context.dataFolder().toPath().resolve("stats.db"));
        } catch (SQLException e) {
            context.logger().severe("PeakStats: Datenbank nicht verfuegbar, Modul bleibt ohne Funktion: " + e.getMessage());
            return;
        }
        heads = new HeadStore(context.dataFolder().toPath().resolve("heads"), context.logger());
        collector = new StatsCollector(context.host(), db, heads, this::notifyLive, context.logger(), context.config().getInt("afk-seconds", 300),
                context.config().getInt("event-days", 90));
        Bukkit.getPluginManager().registerEvents(collector, context.host());
        collector.start();
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakStatsModule.class.getResourceAsStream("/modules/peakstats/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                new StatsWeb(context.web(), db, heads).register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakStats nicht verfuegbar: " + e.getMessage());
            }
        }
    }

    /** Sagt allen offenen Web-Seiten, dass sich Werte geaendert haben, hoechstens einmal pro Sekunde (es wird nichts verschluckt: eine Meldung folgt spaeter). */
    private void notifyLive() {
        ModuleContext c = context;
        if (c == null || !c.web().enabled() || !c.host().isEnabled()) return;
        if (!livePending.compareAndSet(false, true)) return;
        long wait = Math.max(0, 1000 - (System.currentTimeMillis() - liveLast));
        Bukkit.getScheduler().runTaskLaterAsynchronously(c.host(), () -> {
            livePending.set(false);
            liveLast = System.currentTimeMillis();
            ModuleContext cc = context;
            if (cc != null) cc.web().changed("live", "");
        }, Math.max(1, wait / 50));
    }
    @Override
    public void contributePack(PackSink sink) {
        // Keine Pack-Dateien
    }

    @Override
    public void disable() {
        if (collector != null) collector.stop();
        if (db != null) db.close();
        collector = null;
        db = null;
        context = null;
    }
}
