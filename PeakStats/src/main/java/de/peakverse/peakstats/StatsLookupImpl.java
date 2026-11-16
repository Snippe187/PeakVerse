package de.peakverse.peakstats;

import de.peakverse.core.api.stats.StatsLookup;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Freigegebene Werte fuer andere Module (PeakBoard-Platzhalter). Kurz zwischengespeichert, damit die Anzeige die Datenbank nicht jede Sekunde pro Spieler abfragt. */
final class StatsLookupImpl implements StatsLookup {
    private static final long TTL_MS = 2000;
    private record Cached(long time, Map<String, Long> totals) {}
    private record CachedTop(long time, List<StatsDb.Row> rows) {}

    private final StatsDb db;
    private final Map<UUID, Cached> totals = new ConcurrentHashMap<>();
    private final Map<String, CachedTop> tops = new ConcurrentHashMap<>();
    private volatile long visTime;
    private volatile java.util.Set<String> vis = java.util.Set.of();

    StatsLookupImpl(StatsDb db) {
        this.db = db;
    }

    private java.util.Set<String> visible() throws SQLException {
        long now = System.currentTimeMillis();
        if (now - visTime > TTL_MS) {
            vis = db.visible();
            visTime = now;
        }
        return vis;
    }

    @Override
    public String value(UUID player, String word) {
        try {
            String key = StatFormat.keyFor(word);
            if (key == null || !visible().contains(key)) return "-";
            long now = System.currentTimeMillis();
            Cached c = totals.get(player);
            if (c == null || now - c.time() > TTL_MS) {
                c = new Cached(now, db.totals(player));
                totals.put(player, c);
            }
            return StatFormat.value(unit(key), c.totals().getOrDefault(key, 0L));
        } catch (SQLException e) {
            return "-";
        }
    }

    private List<StatsDb.Row> top(String key) throws SQLException {
        long now = System.currentTimeMillis();
        CachedTop c = tops.get(key);
        if (c == null || now - c.time() > TTL_MS) {
            c = new CachedTop(now, db.top(key, null, 10, java.util.Set.of()));
            tops.put(key, c);
        }
        return c.rows();
    }

    private StatsDb.Row row(String word, int rank) {
        try {
            String key = StatFormat.keyFor(word);
            if (key == null || rank < 1 || rank > 10 || !visible().contains(key)) return null;
            List<StatsDb.Row> rows = top(key);
            return rank <= rows.size() ? rows.get(rank - 1) : null;
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public String topName(String word, int rank) {
        StatsDb.Row r = row(word, rank);
        return r == null ? "-" : r.name();
    }

    @Override
    public String topValue(String word, int rank) {
        StatsDb.Row r = row(word, rank);
        return r == null ? "-" : StatFormat.value(unit(StatFormat.keyFor(word)), r.value());
    }

    private static String unit(String key) {
        return StatKeys.all().stream().filter(d -> d.key().equals(key)).map(StatKeys.Def::unit).findFirst().orElse("n");
    }
}
