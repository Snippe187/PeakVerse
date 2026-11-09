package de.peakverse.peakadmin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Sicherungen (von Hand und nach Zeitplan) und Neustart mit Countdown. Die Methoden laufen auf dem Haupt-Thread. */
public final class ServerControl {
    private final JavaPlugin plugin;
    private final Logger log;
    private final BackupStore backups;
    private final AtomicBoolean backupRunning = new AtomicBoolean();
    private BukkitTask restartTask;
    private long restartAt; // Epoch ms, 0 = kein Neustart geplant
    private volatile String lastBackupInfo = "";
    private int lastDailyDay = -1;

    public ServerControl(JavaPlugin plugin, Logger log, BackupStore backups) {
        this.plugin = plugin;
        this.log = log;
        this.backups = backups;
    }

    public BackupStore backups() {
        return backups;
    }

    public boolean backupRunning() {
        return backupRunning.get();
    }

    public String lastBackupInfo() {
        return lastBackupInfo;
    }

    /** Der Welt-Ordner (der mit level.dat; seit Paper 26 eine Ebene ueber dem Overworld-Ordner). */
    public static Path worldRoot() {
        Path p = Bukkit.getWorlds().get(0).getWorldFolder().toPath().toAbsolutePath();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("level.dat"))) return p;
        }
        return Bukkit.getWorlds().get(0).getWorldFolder().toPath().toAbsolutePath();
    }

    // ---- Sicherung ----

    /** Startet eine Sicherung im Hintergrund; {@code done} bekommt Fehlertext oder {@code null}. Liefert false, wenn schon eine laeuft. */
    public boolean backupNow(String label, int keep, Consumer<String> done) {
        if (!backupRunning.compareAndSet(false, true)) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all flush");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-off");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = null;
            try {
                BackupStore.Result r = backups.create(worldRoot(), label);
                backups.prune(keep);
                lastBackupInfo = r.backup().name() + " (" + (r.backup().size() / 1_048_576) + " MB" + (r.skipped() > 0 ? ", " + r.skipped() + " Dateien uebersprungen" : "") + ")";
                log.info("Sicherung fertig: " + lastBackupInfo);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                log.warning("Sicherung fehlgeschlagen: " + error);
            }
            String err = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-on");
                backupRunning.set(false);
                if (done != null) done.accept(err);
            });
        });
        return true;
    }

    // ---- Neustart ----

    public long restartAt() {
        return restartAt;
    }

    /** Plant einen Neustart (Server beendet sich; ein Start-Skript oder der Hoster startet ihn neu). */
    public void scheduleRestart(int minutes, String reason) {
        if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("Minuten zwischen 0 und 1440");
        cancelRestart();
        restartAt = System.currentTimeMillis() + minutes * 60_000L;
        String why = reason == null || reason.isBlank() ? "" : " (" + PlayerTools.safeText(reason) + ")";
        restartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = (restartAt - System.currentTimeMillis() + 999) / 1000;
            if (left <= 0) {
                restartTask.cancel();
                restartAt = 0;
                Bukkit.broadcast(Component.text("Der Server startet jetzt neu.", NamedTextColor.RED));
                Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.shutdown(), 20L);
                return;
            }
            boolean mark = left == 600 || left == 300 || left == 180 || left == 120 || left == 60 || left == 30 || left == 10 || left <= 5;
            if (mark) Bukkit.broadcast(Component.text("Server-Neustart in " + (left >= 60 && left % 60 == 0 ? left / 60 + " Minute(n)" : left + " Sekunde(n)") + why, NamedTextColor.YELLOW));
        }, 0L, 20L);
    }

    public boolean cancelRestart() {
        boolean was = restartTask != null && restartAt != 0;
        if (restartTask != null) restartTask.cancel();
        restartTask = null;
        restartAt = 0;
        if (was) Bukkit.broadcast(Component.text("Der Neustart wurde abgesagt.", NamedTextColor.GREEN));
        return was;
    }

    /** Prueft einmal pro Minute, ob der taegliche Neustart (HH:mm) faellig ist; plant dann einen Countdown. */
    public void tickDaily(String at, int warnMinutes) {
        if (at == null || at.isBlank() || restartAt != 0) return;
        LocalTime now = LocalTime.now();
        LocalTime target;
        try {
            target = LocalTime.parse(at.strip());
        } catch (RuntimeException e) {
            return;
        }
        LocalTime warn = target.minusMinutes(Math.max(0, warnMinutes));
        int day = java.time.LocalDate.now().getDayOfYear();
        if (day != lastDailyDay && now.getHour() == warn.getHour() && now.getMinute() == warn.getMinute()) {
            lastDailyDay = day;
            scheduleRestart(Math.max(0, warnMinutes), "taeglicher Neustart");
        }
    }

    public void shutdown() {
        if (restartTask != null) restartTask.cancel();
    }
}