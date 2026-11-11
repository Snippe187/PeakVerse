package de.peakverse.peakstats;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sammelt die Werte im Speicher und schreibt sie regelmaessig (und beim Beenden) in die Datenbank. Creative zaehlt nicht fuer Kills, Bloecke
 * und Strecke; selbst gesetzte Bloecke zaehlen beim schnellen Abbauen nicht; AFK wird getrennt von aktiver Spielzeit gezaehlt.
 */
public final class StatsCollector implements Listener {
    private static final Statistic[] MOVE = {Statistic.WALK_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.CROUCH_ONE_CM, Statistic.SWIM_ONE_CM,
            Statistic.FLY_ONE_CM, Statistic.AVIATE_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.HORSE_ONE_CM,
            Statistic.PIG_ONE_CM, Statistic.STRIDER_ONE_CM, Statistic.CLIMB_ONE_CM, Statistic.WALK_ON_WATER_ONE_CM, Statistic.WALK_UNDER_WATER_ONE_CM};
    private static final long SAMPLE_TICKS = 100L;
    private static final long PLACED_MEMORY_MS = 10 * 60_000L;

    private static final class Track {
        Location last;
        long lastMoved = System.currentTimeMillis();
        long lastSample = System.currentTimeMillis();
        long moveBase;
    }

    private final Plugin host;
    private final StatsDb db;
    private final Logger log;
    private final long afkMillis;
    private final int eventDays;
    private final Map<UUID, Map<String, Long>> pending = new HashMap<>();
    private final List<StatsDb.Event> pendingEvents = new ArrayList<>();
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<Long, long[]> placed = new HashMap<>();   // Blockposition -> {Spieler-UUID most, least, Zeit}
    private BukkitTask sampler;
    private boolean flushQueued;
    private volatile long lastPrune;

    private final HeadStore heads;

    public StatsCollector(Plugin host, StatsDb db, HeadStore heads, Logger log, int afkSeconds, int eventDays) {
        this.host = host;
        this.heads = heads;
        this.db = db;
        this.log = log;
        this.afkMillis = Math.max(30, afkSeconds) * 1000L;
        this.eventDays = Math.max(1, eventDays);
    }

    public void start() {
        sampler = host.getServer().getScheduler().runTaskTimer(host, this::sample, SAMPLE_TICKS, SAMPLE_TICKS);
        for (Player p : host.getServer().getOnlinePlayers()) begin(p);
    }

    public void stop() {
        if (sampler != null) sampler.cancel();
        flush(false);
    }

    // ---------- Sammeln ----------

    private void add(UUID id, String key, long delta) {
        if (delta == 0) return;
        pending.computeIfAbsent(id, k -> new HashMap<>()).merge(key, delta, Long::sum);
        queueFlush();
    }

    /** Jede Aenderung wird gleich geschrieben (im naechsten Tick gesammelt, damit viele Aenderungen im selben Tick nur einen Schreibvorgang ergeben). */
    private void queueFlush() {
        if (flushQueued || !host.isEnabled()) return;
        flushQueued = true;
        host.getServer().getScheduler().runTask(host, () -> {
            flushQueued = false;
            flush(true);
        });
    }

    private void event(UUID id, String type, String detail, Location l) {
        pendingEvents.add(new StatsDb.Event(System.currentTimeMillis(), id.toString(), type, detail,
                l == null ? "" : l.getWorld().getName(), l == null ? 0 : l.getBlockX(), l == null ? 0 : l.getBlockY(), l == null ? 0 : l.getBlockZ()));
        queueFlush();
    }

    private static boolean counts(Player p) {
        return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
    }

    private static long moveSum(Player p) {
        long s = 0;
        for (Statistic st : MOVE) {
            try {
                s += p.getStatistic(st);
            } catch (IllegalArgumentException ignored) {
                // Statistik gibt es in dieser Version nicht
            }
        }
        return s;
    }

    private void begin(Player p) {
        Track t = new Track();
        t.last = p.getLocation();
        t.moveBase = moveSum(p);
        tracks.put(p.getUniqueId(), t);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        begin(p);
        UUID id = p.getUniqueId();
        add(id, StatKeys.LOGINS, 1);
        event(id, "login", "", p.getLocation());
        String name = p.getName();
        long now = System.currentTimeMillis();
        // Datenbank-Zugriff nicht auf dem Haupt-Thread; Vanilla-Werte aber hier lesen (Bukkit-API)
        Map<String, Long> baseline = null;
        try {
            if (!db.imported(id)) baseline = VanillaImport.read(p);
        } catch (SQLException ex) {
            log.warning("PeakStats: " + ex.getMessage());
        }
        String skinUrl = null;
        for (var prop : p.getPlayerProfile().getProperties()) if ("textures".equals(prop.getName())) skinUrl = HeadStore.skinUrl(prop.getValue());
        String skin = skinUrl;
        Map<String, Long> base = baseline;
        host.getServer().getScheduler().runTaskAsynchronously(host, () -> {
            try {
                db.touch(id, name, now);
                if (base != null) db.importBaseline(id, base);
                if (skin != null) heads.store(id, skin);
            } catch (SQLException ex) {
                log.warning("PeakStats: " + ex.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        sampleOne(p, System.currentTimeMillis());
        event(p.getUniqueId(), "logout", "", p.getLocation());
        tracks.remove(p.getUniqueId());
        UUID id = p.getUniqueId();
        String name = p.getName();
        long now = System.currentTimeMillis();
        host.getServer().getScheduler().runTaskAsynchronously(host, () -> {
            try {
                db.touch(id, name, now);
            } catch (SQLException ex) {
                log.warning("PeakStats: " + ex.getMessage());
            }
        });
    }

    private void sample() {
        long now = System.currentTimeMillis();
        for (Player p : host.getServer().getOnlinePlayers()) sampleOne(p, now);
    }

    private void sampleOne(Player p, long now) {
        Track t = tracks.get(p.getUniqueId());
        if (t == null) return;
        Location l = p.getLocation();
        if (t.last == null || t.last.getWorld() != l.getWorld() || t.last.distanceSquared(l) > 0.0025
                || Math.abs(t.last.getYaw() - l.getYaw()) > 1 || Math.abs(t.last.getPitch() - l.getPitch()) > 1) {
            t.lastMoved = now;
            t.last = l.clone();
        }
        long dt = Math.max(0, Math.min(now - t.lastSample, 60_000));
        t.lastSample = now;
        add(p.getUniqueId(), now - t.lastMoved < afkMillis ? StatKeys.PLAY : StatKeys.AFK, dt);
        long move = moveSum(p);
        long delta = move - t.moveBase;
        t.moveBase = move;
        if (delta > 0 && delta < 100_000 && counts(p)) add(p.getUniqueId(), StatKeys.DISTANCE, delta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!counts(p)) return;
        add(p.getUniqueId(), StatKeys.PLACED, 1);
        long now = System.currentTimeMillis();
        if (placed.size() > 50_000) placed.values().removeIf(v -> now - v[2] > PLACED_MEMORY_MS);
        placed.put(pos(e.getBlockPlaced().getLocation()), new long[] {p.getUniqueId().getMostSignificantBits(), p.getUniqueId().getLeastSignificantBits(), now});
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        long key = pos(e.getBlock().getLocation());
        long[] own = placed.remove(key);
        if (!counts(p)) return;
        if (own != null && System.currentTimeMillis() - own[2] < PLACED_MEMORY_MS
                && own[0] == p.getUniqueId().getMostSignificantBits() && own[1] == p.getUniqueId().getLeastSignificantBits()) return;   // selbst gesetzt
        String type = e.getBlock().getType().getKey().getKey();
        add(p.getUniqueId(), StatKeys.MINED, 1);
        add(p.getUniqueId(), "mine:" + type, 1);
        if (StatKeys.isOre(type)) add(p.getUniqueId(), StatKeys.ORES, 1);
    }

    private static long pos(Location l) {
        return ((long) l.getBlockX() & 0x3FFFFFFL) << 38 | ((long) l.getBlockZ() & 0x3FFFFFFL) << 12 | ((long) l.getBlockY() & 0xFFFL)
                ^ (l.getWorld().getUID().getLeastSignificantBits() * 31);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !counts(p)) return;
        int n = e.getRecipe().getResult().getAmount();
        add(p.getUniqueId(), StatKeys.CRAFTED, Math.max(1, n));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;   // Spieler-Tode in onPlayerDeath
        Player k = e.getEntity().getKiller();
        if (k == null || !counts(k)) return;
        add(k.getUniqueId(), StatKeys.KILLS_MOB, 1);
        EntityType t = e.getEntityType();
        add(k.getUniqueId(), "mob:" + t.getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = p.getUniqueId();
        add(id, StatKeys.DEATHS, 1);
        var cause = p.getLastDamageCause();
        String why = cause == null ? "unknown" : cause.getCause().name().toLowerCase(Locale.ROOT);
        Player k = p.getKiller();
        if (k != null && k != p) {
            why += " von " + k.getName();
            if (counts(k)) {
                add(k.getUniqueId(), StatKeys.KILLS_PLAYER, 1);
                event(k.getUniqueId(), "kill", p.getName(), k.getLocation());
            }
        }
        event(id, "death", why, p.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        long hp10 = Math.round(e.getFinalDamage() * 10);
        if (hp10 <= 0) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity src) d = src;
        if (d instanceof Player p && counts(p)) add(p.getUniqueId(), StatKeys.DMG_DEALT, hp10);
        if (e.getEntity() instanceof Player v) add(v.getUniqueId(), StatKeys.DMG_TAKEN, hp10);
    }

    // ---------- Schreiben ----------

    /** Schreibt den gesammelten Stand; {@code async}: im Hintergrund (nur wenn das Plugin laeuft), sonst sofort. */
    public void flush(boolean async) {
        Map<UUID, Map<String, Long>> deltas = new HashMap<>(pending);
        pending.clear();
        List<StatsDb.Event> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        LocalDate day = LocalDate.now();
        Runnable write = () -> {
            try {
                db.addAll(deltas, day);
                db.addEvents(events);
                long now = System.currentTimeMillis();
                if (now - lastPrune > 600_000L) {   // alte Ereignisse nur alle 10 Minuten aufraeumen
                    lastPrune = now;
                    db.pruneEvents(now - eventDays * 86_400_000L);
                }
            } catch (SQLException ex) {
                log.warning("PeakStats: Speichern fehlgeschlagen: " + ex.getMessage());
            }
        };
        if (async) host.getServer().getScheduler().runTaskAsynchronously(host, write);
        else write.run();
    }
}
