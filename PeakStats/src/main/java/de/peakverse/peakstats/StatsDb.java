package de.peakverse.peakstats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * SQLite-Speicher von PeakStats: Summen je Spieler und Wert, Tageswerte (fuer Bestenlisten nach Zeitraum und Verlauf), Einzelereignisse
 * (nach {@code event-days} Tagen geloescht) und die Sichtbarkeit je Wert. Alle Methoden sind synchronisiert (eine Verbindung).
 * Der Treiber kommt vom Server (Paper bringt sqlite-jdbc mit); er wird direkt angesprochen, nicht ueber DriverManager.
 */
public final class StatsDb implements AutoCloseable {
    public record Event(long time, String uuid, String type, String detail, String world, int x, int y, int z) {}

    public record Row(String uuid, String name, long value) {}

    private final Connection c;

    public StatsDb(Path file) throws SQLException {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            c = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
        } catch (java.io.IOException e) {
            throw new SQLException("Ordner nicht anlegbar: " + e.getMessage(), e);
        }
        if (c == null) throw new SQLException("SQLite-Treiber hat die Verbindung abgelehnt");
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, imported INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS totals(uuid TEXT NOT NULL, key TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(uuid, key))");
            s.execute("CREATE TABLE IF NOT EXISTS daily(day TEXT NOT NULL, uuid TEXT NOT NULL, key TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(day, uuid, key))");
            s.execute("CREATE INDEX IF NOT EXISTS daily_key ON daily(key, day)");
            s.execute("CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, uuid TEXT NOT NULL, type TEXT NOT NULL, detail TEXT NOT NULL, world TEXT, x INTEGER, y INTEGER, z INTEGER)");
            s.execute("CREATE INDEX IF NOT EXISTS events_uuid ON events(uuid, time)");
            s.execute("CREATE TABLE IF NOT EXISTS visibility(key TEXT PRIMARY KEY, visible INTEGER NOT NULL)");
        }
    }

    // ---------- Spieler ----------

    /** Legt den Spieler an oder aktualisiert Name und letzte Aktivitaet. Liefert {@code true}, wenn er neu ist. */
    public synchronized boolean touch(UUID id, String name, long now) throws SQLException {
        boolean isNew;
        try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM players WHERE uuid=?")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) { isNew = !r.next(); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO players(uuid,name,first_seen,last_seen) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen")) {
            p.setString(1, id.toString());
            p.setString(2, name);
            p.setLong(3, now);
            p.setLong(4, now);
            p.executeUpdate();
        }
        return isNew;
    }

    public synchronized boolean imported(UUID id) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT imported FROM players WHERE uuid=?")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) { return r.next() && r.getInt(1) == 1; }
        }
    }

    /** Schreibt Startwerte (Vanilla-Import) in die Summen, ohne Tageswerte, und merkt sich den Import. */
    public synchronized void importBaseline(UUID id, Map<String, Long> values) throws SQLException {
        c.setAutoCommit(false);
        try {
            for (Map.Entry<String, Long> e : values.entrySet()) {
                if (e.getValue() <= 0) continue;
                try (PreparedStatement p = c.prepareStatement("INSERT INTO totals(uuid,key,value) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET value=value+excluded.value")) {
                    p.setString(1, id.toString());
                    p.setString(2, e.getKey());
                    p.setLong(3, e.getValue());
                    p.executeUpdate();
                }
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE players SET imported=1 WHERE uuid=?")) {
                p.setString(1, id.toString());
                p.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    // ---------- Zaehler ----------

    /** Addiert Zuwaechse (Summe und Tageswert) in einer Transaktion. */
    public synchronized void addAll(Map<UUID, Map<String, Long>> deltas, LocalDate day) throws SQLException {
        if (deltas.isEmpty()) return;
        c.setAutoCommit(false);
        try (PreparedStatement t = c.prepareStatement("INSERT INTO totals(uuid,key,value) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET value=value+excluded.value");
             PreparedStatement d = c.prepareStatement("INSERT INTO daily(day,uuid,key,value) VALUES(?,?,?,?) ON CONFLICT(day,uuid,key) DO UPDATE SET value=value+excluded.value")) {
            for (Map.Entry<UUID, Map<String, Long>> p : deltas.entrySet()) {
                for (Map.Entry<String, Long> e : p.getValue().entrySet()) {
                    if (e.getValue() == 0) continue;
                    t.setString(1, p.getKey().toString());
                    t.setString(2, e.getKey());
                    t.setLong(3, e.getValue());
                    t.addBatch();
                    d.setString(1, day.toString());
                    d.setString(2, p.getKey().toString());
                    d.setString(3, e.getKey());
                    d.setLong(4, e.getValue());
                    d.addBatch();
                }
            }
            t.executeBatch();
            d.executeBatch();
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public synchronized Map<String, Long> totals(UUID id) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (PreparedStatement q = c.prepareStatement("SELECT key,value FROM totals WHERE uuid=? ORDER BY key")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) { while (r.next()) m.put(r.getString(1), r.getLong(2)); }
        }
        return m;
    }

    /** Alle Spieler mit ihren Hauptwerten (fuer Liste und Export). */
    public synchronized JsonArray players() throws SQLException {
        Map<String, JsonObject> by = new LinkedHashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT uuid,name,first_seen,last_seen FROM players ORDER BY name COLLATE NOCASE")) {
            while (r.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("uuid", r.getString(1));
                o.addProperty("name", r.getString(2));
                o.addProperty("firstSeen", r.getLong(3));
                o.addProperty("lastSeen", r.getLong(4));
                o.add("stats", new JsonObject());
                by.put(r.getString(1), o);
            }
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT uuid,key,value FROM totals WHERE key NOT LIKE 'mob:%' AND key NOT LIKE 'mine:%'")) {
            while (r.next()) {
                JsonObject o = by.get(r.getString(1));
                if (o != null) o.getAsJsonObject("stats").addProperty(r.getString(2), r.getLong(3));
            }
        }
        JsonArray a = new JsonArray();
        by.values().forEach(a::add);
        return a;
    }

    public synchronized String nameOf(UUID id) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT name FROM players WHERE uuid=?")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    public synchronized UUID idOf(String name) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT uuid FROM players WHERE name=? COLLATE NOCASE")) {
            q.setString(1, name);
            try (ResultSet r = q.executeQuery()) { return r.next() ? UUID.fromString(r.getString(1)) : null; }
        }
    }

    // ---------- Bestenlisten und Verlauf ----------

    /** Rangliste; {@code since == null} = Gesamtwerte, sonst Summe der Tageswerte ab diesem Tag. */
    public synchronized List<Row> top(String key, LocalDate since, int limit, Set<UUID> exclude) throws SQLException {
        String sql = since == null
                ? "SELECT t.uuid,p.name,t.value FROM totals t JOIN players p ON p.uuid=t.uuid WHERE t.key=? AND t.value>0 ORDER BY t.value DESC, p.name LIMIT ?"
                : "SELECT d.uuid,p.name,SUM(d.value) v FROM daily d JOIN players p ON p.uuid=d.uuid WHERE d.key=? AND d.day>=? GROUP BY d.uuid HAVING v>0 ORDER BY v DESC, p.name LIMIT ?";
        List<Row> out = new java.util.ArrayList<>();
        try (PreparedStatement q = c.prepareStatement(sql)) {
            int i = 1;
            q.setString(i++, key);
            if (since != null) q.setString(i++, since.toString());
            q.setInt(i, Math.max(1, limit + exclude.size()));
            try (ResultSet r = q.executeQuery()) {
                while (r.next() && out.size() < limit) {
                    if (exclude.contains(UUID.fromString(r.getString(1)))) continue;
                    out.add(new Row(r.getString(1), r.getString(2), r.getLong(3)));
                }
            }
        }
        return out;
    }

    /** Tageswerte eines Spielers ab einem Tag: Tag -> Wert. */
    public synchronized Map<String, Long> daily(UUID id, String key, LocalDate since) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (PreparedStatement q = c.prepareStatement("SELECT day,value FROM daily WHERE uuid=? AND key=? AND day>=? ORDER BY day")) {
            q.setString(1, id.toString());
            q.setString(2, key);
            q.setString(3, since.toString());
            try (ResultSet r = q.executeQuery()) { while (r.next()) m.put(r.getString(1), r.getLong(2)); }
        }
        return m;
    }

    /** Summe aller Spieler je Tag fuer einen Wert (Diagramme). */
    public synchronized Map<String, Long> dailyAll(String key, LocalDate since) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (PreparedStatement q = c.prepareStatement("SELECT day,SUM(value) FROM daily WHERE key=? AND day>=? GROUP BY day ORDER BY day")) {
            q.setString(1, key);
            q.setString(2, since.toString());
            try (ResultSet r = q.executeQuery()) { while (r.next()) m.put(r.getString(1), r.getLong(2)); }
        }
        return m;
    }

    // ---------- Uebersicht ----------

    /** Summe aller Spieler je Hauptwert. */
    public synchronized Map<String, Long> sumTotals() throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT key,SUM(value) FROM totals WHERE key NOT LIKE 'mob:%' AND key NOT LIKE 'mine:%' GROUP BY key")) {
            while (r.next()) m.put(r.getString(1), r.getLong(2));
        }
        return m;
    }

    /** Anzahl verschiedener Spieler mit Spielzeit je Tag. */
    public synchronized Map<String, Long> activePerDay(LocalDate since) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (PreparedStatement q = c.prepareStatement("SELECT day,COUNT(DISTINCT uuid) FROM daily WHERE key=? AND day>=? GROUP BY day ORDER BY day")) {
            q.setString(1, StatKeys.PLAY);
            q.setString(2, since.toString());
            try (ResultSet r = q.executeQuery()) { while (r.next()) m.put(r.getString(1), r.getLong(2)); }
        }
        return m;
    }

    /** Anmeldungen je Wochentag (0 = Montag) und Stunde aus den gespeicherten Ereignissen. */
    public synchronized int[][] heatmap() throws SQLException {
        int[][] h = new int[7][24];
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT CAST(strftime('%w', time/1000, 'unixepoch', 'localtime') AS INTEGER), CAST(strftime('%H', time/1000, 'unixepoch', 'localtime') AS INTEGER), COUNT(*) FROM events WHERE type='login' GROUP BY 1,2")) {
            while (r.next()) h[(r.getInt(1) + 6) % 7][r.getInt(2)] = r.getInt(3);
        }
        return h;
    }

    public synchronized int playerCount() throws SQLException {
        return count("players");
    }
    // ---------- Ereignisse ----------

    public synchronized void addEvents(List<Event> events) throws SQLException {
        if (events.isEmpty()) return;
        c.setAutoCommit(false);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO events(time,uuid,type,detail,world,x,y,z) VALUES(?,?,?,?,?,?,?,?)")) {
            for (Event e : events) {
                p.setLong(1, e.time());
                p.setString(2, e.uuid());
                p.setString(3, e.type());
                p.setString(4, e.detail());
                p.setString(5, e.world());
                p.setInt(6, e.x());
                p.setInt(7, e.y());
                p.setInt(8, e.z());
                p.addBatch();
            }
            p.executeBatch();
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public synchronized List<Event> events(UUID id, int limit) throws SQLException {
        List<Event> out = new java.util.ArrayList<>();
        try (PreparedStatement q = c.prepareStatement("SELECT time,uuid,type,detail,world,x,y,z FROM events WHERE uuid=? ORDER BY time DESC, id DESC LIMIT ?")) {
            q.setString(1, id.toString());
            q.setInt(2, Math.max(1, Math.min(1000, limit)));
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) out.add(new Event(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getInt(6), r.getInt(7), r.getInt(8)));
            }
        }
        return out;
    }

    /** Loescht Einzelereignisse, die aelter sind als die Grenze; die Tageswerte bleiben. */
    public synchronized int pruneEvents(long beforeMillis) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM events WHERE time<?")) {
            p.setLong(1, beforeMillis);
            return p.executeUpdate();
        }
    }

    // ---------- Sichtbarkeit ----------

    /** Fuer Spieler freigegebene Hauptwerte (Standard: keiner). */
    public synchronized Set<String> visible() throws SQLException {
        Set<String> s = new HashSet<>();
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT key FROM visibility WHERE visible=1")) {
            while (r.next()) s.add(r.getString(1));
        }
        return s;
    }

    public synchronized void setVisible(String key, boolean on) throws SQLException {
        if (!StatKeys.isMain(key)) throw new IllegalArgumentException("Unbekannter Wert: " + key);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO visibility(key,visible) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET visible=excluded.visible")) {
            p.setString(1, key);
            p.setInt(2, on ? 1 : 0);
            p.executeUpdate();
        }
    }

    // ---------- Bearbeiten ----------

    public synchronized void set(UUID id, String key, long value) throws SQLException {
        if (!StatKeys.valid(key)) throw new IllegalArgumentException("Unbekannter Wert: " + key);
        if (value < 0) throw new IllegalArgumentException("Wert darf nicht negativ sein.");
        try (PreparedStatement p = c.prepareStatement("INSERT INTO totals(uuid,key,value) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET value=excluded.value")) {
            p.setString(1, id.toString());
            p.setString(2, key);
            p.setLong(3, value);
            p.executeUpdate();
        }
    }

    /** Setzt alle Werte eines Spielers zurueck (Summen, Tageswerte, Ereignisse); der Spieler bleibt bekannt. */
    public synchronized void resetPlayer(UUID id) throws SQLException {
        for (String t : new String[] {"totals", "daily", "events"}) {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM " + t + " WHERE uuid=?")) {
                p.setString(1, id.toString());
                p.executeUpdate();
            }
        }
    }

    public synchronized void resetAll() throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("DELETE FROM totals");
            s.execute("DELETE FROM daily");
            s.execute("DELETE FROM events");
        }
    }

    @Override
    public synchronized void close() {
        try {
            c.close();
        } catch (SQLException ignored) {
            // beim Beenden nichts mehr zu retten
        }
    }

    /** Fuer Tests: Anzahl Zeilen einer Tabelle. */
    synchronized int count(String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { return r.next() ? r.getInt(1) : 0; }
    }

    static Map<String, Long> single(String key, long v) {
        Map<String, Long> m = new HashMap<>();
        m.put(key, v);
        return m;
    }
}
