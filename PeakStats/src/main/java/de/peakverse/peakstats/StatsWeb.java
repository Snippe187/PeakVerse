package de.peakverse.peakstats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Web-Seite von PeakStats fuer Administratoren ({@code /api/peakstats/...}): Spieler, Werte, Verlauf, Bestenlisten, Freigabe, Bearbeiten, Export. */
final class StatsWeb {
    private static final Gson GSON = new Gson();

    private final WebRegistry web;
    private final StatsDb db;
    private final HeadStore heads;

    StatsWeb(WebRegistry web, StatsDb db, HeadStore heads) {
        this.web = web;
        this.db = db;
        this.heads = heads;
    }

    void register(String script) {
        web.panel("Statistik", "Statistics", script);
        web.route("catalog", r -> guard(() -> WebResponse.json(catalog().toString())));
        web.route("players", r -> guard(() -> WebResponse.json(onlineFlag(db.players()).toString())));
        web.route("player/*", r -> guard(() -> player(r)));
        web.route("top", r -> guard(() -> top(r)));
        web.route("overview", r -> guard(this::overview));
        web.route("head/*", r -> guard(() -> head(r)));
        web.route("visibility", r -> guard(() -> visibility(r)));
        web.route("set", r -> guard(() -> set(r)));
        web.route("reset", r -> guard(() -> reset(r)));
        web.route("export.csv", r -> guard(this::csv));
    }

    private interface Task {
        WebResponse run() throws Exception;
    }

    private WebResponse guard(Task t) throws Exception {
        try {
            return t.run();
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            return WebResponse.error(400, e.getMessage() == null ? "Ungueltige Eingabe." : e.getMessage());
        } catch (NullPointerException | UnsupportedOperationException | ClassCastException e) {
            return WebResponse.error(400, "Ungueltige Eingabe (Feld fehlt oder hat den falschen Typ).");
        } catch (SQLException e) {
            return WebResponse.error(500, "Datenbankfehler: " + e.getMessage());
        }
    }

    private static boolean isOp(String name) {
        for (OfflinePlayer op : Bukkit.getOperators()) if (name.equalsIgnoreCase(op.getName())) return true;
        return false;
    }

    private static void requireOp(WebRequest r) {
        if (!r.method().equals("POST")) throw new IllegalStateException("POST erforderlich.");
        if (!isOp(r.admin())) throw new IllegalStateException("Nur fuer Operatoren.");
    }

    private static JsonObject body(WebRequest r) {
        JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
        if (o == null) throw new IllegalArgumentException("Leere Anfrage.");
        return o;
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) throw new IllegalArgumentException("Feld fehlt: " + k);
        return o.get(k).getAsString();
    }

    private JsonArray catalog() throws SQLException {
        Set<String> vis = db.visible();
        JsonArray a = new JsonArray();
        for (StatKeys.Def d : StatKeys.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("key", d.key());
            o.addProperty("group", d.group());
            o.addProperty("labelDe", d.labelDe());
            o.addProperty("labelEn", d.labelEn());
            o.addProperty("unit", d.unit());
            o.addProperty("visible", vis.contains(d.key()));
            a.add(o);
        }
        return a;
    }

    private JsonArray onlineFlag(JsonArray players) {
        Set<UUID> on = new java.util.HashSet<>();
        for (var p : Bukkit.getOnlinePlayers()) on.add(p.getUniqueId());
        for (var e : players) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("online", on.contains(UUID.fromString(o.get("uuid").getAsString())));
        }
        return players;
    }

    private UUID uuidOf(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige Spieler-ID.");
        }
    }

    private WebResponse player(WebRequest r) throws Exception {
        String[] p = r.path().split("/");
        if (p.length < 2) return WebResponse.error(404, "Spieler fehlt.");
        UUID id = uuidOf(p[1]);
        String name = db.nameOf(id);
        if (name == null) return WebResponse.error(404, "Unbekannter Spieler.");
        JsonObject o = new JsonObject();
        o.addProperty("uuid", id.toString());
        o.addProperty("name", name);
        for (var pe : db.players()) {
            JsonObject po = pe.getAsJsonObject();
            if (po.get("uuid").getAsString().equals(id.toString())) {
                o.addProperty("firstSeen", po.get("firstSeen").getAsLong());
                o.addProperty("lastSeen", po.get("lastSeen").getAsLong());
            }
        }
        var online = Bukkit.getPlayer(id);
        o.addProperty("online", online != null);
        JsonObject totals = new JsonObject();
        db.totals(id).forEach(totals::addProperty);
        o.add("totals", totals);
        LocalDate since = LocalDate.now().minusDays(29);
        JsonObject daily = new JsonObject();
        for (String k : new String[] {StatKeys.PLAY, StatKeys.AFK, StatKeys.MINED, StatKeys.KILLS_MOB, StatKeys.DEATHS, StatKeys.DISTANCE}) {
            JsonObject d = new JsonObject();
            db.daily(id, k, since).forEach(d::addProperty);
            daily.add(k, d);
        }
        o.add("daily", daily);
        JsonArray ev = new JsonArray();
        for (StatsDb.Event e : db.events(id, 100)) {
            JsonObject x = new JsonObject();
            x.addProperty("time", e.time());
            x.addProperty("type", e.type());
            x.addProperty("detail", e.detail());
            x.addProperty("world", e.world());
            x.addProperty("x", e.x());
            x.addProperty("y", e.y());
            x.addProperty("z", e.z());
            ev.add(x);
        }
        o.add("events", ev);
        return WebResponse.json(o.toString());
    }

    private WebResponse head(WebRequest r) {
        String[] p = r.path().split("/");
        UUID id = p.length < 2 ? null : uuidOf(p[1]);
        byte[] png = id == null ? null : heads.read(id);
        return png == null ? WebResponse.error(404, "Kein Kopf gespeichert.") : WebResponse.bytes("image/png", png);
    }
    private WebResponse overview() throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("players", db.playerCount());
        o.addProperty("online", Bukkit.getOnlinePlayers().size());
        JsonObject sums = new JsonObject();
        db.sumTotals().forEach(sums::addProperty);
        o.add("totals", sums);
        LocalDate since = LocalDate.now().minusDays(29);
        JsonObject play = new JsonObject(), active = new JsonObject();
        db.dailyAll(StatKeys.PLAY, since).forEach(play::addProperty);
        db.activePerDay(since).forEach(active::addProperty);
        o.add("playPerDay", play);
        o.add("activePerDay", active);
        JsonArray heat = new JsonArray();
        for (int[] row : db.heatmap()) {
            JsonArray r = new JsonArray();
            for (int v : row) r.add(v);
            heat.add(r);
        }
        o.add("heat", heat);
        JsonObject podium = new JsonObject();
        for (String k : new String[] {StatKeys.PLAY, StatKeys.KILLS_PLAYER, StatKeys.KILLS_MOB, StatKeys.DEATHS, StatKeys.MINED, StatKeys.PLACED, StatKeys.DISTANCE}) {
            JsonArray a = new JsonArray();
            for (StatsDb.Row row : db.top(k, null, 3, Set.of())) {
                JsonObject x = new JsonObject();
                x.addProperty("uuid", row.uuid());
                x.addProperty("name", row.name());
                x.addProperty("value", row.value());
                a.add(x);
            }
            podium.add(k, a);
        }
        o.add("podium", podium);
        return WebResponse.json(o.toString());
    }
    /** Zeitraum -> Anfangstag; {@code null} = gesamt. */
    static LocalDate since(String period, LocalDate today) {
        return switch (period == null ? "total" : period) {
            case "today" -> today;
            case "week" -> today.minusDays(6);
            case "month" -> today.minusDays(29);
            case "total" -> null;
            default -> throw new IllegalArgumentException("Unbekannter Zeitraum: " + period);
        };
    }

    private WebResponse top(WebRequest r) throws Exception {
        String key = r.query().getOrDefault("key", StatKeys.PLAY);
        if (!StatKeys.valid(key)) throw new IllegalArgumentException("Unbekannter Wert: " + key);
        int limit = 10;
        try {
            limit = Math.max(1, Math.min(100, Integer.parseInt(r.query().getOrDefault("limit", "10"))));
        } catch (NumberFormatException ignored) {
            // Standard
        }
        JsonArray a = new JsonArray();
        int rank = 1;
        for (StatsDb.Row row : db.top(key, since(r.query().get("period"), LocalDate.now()), limit, Set.of())) {
            JsonObject o = new JsonObject();
            o.addProperty("rank", rank++);
            o.addProperty("uuid", row.uuid());
            o.addProperty("name", row.name());
            o.addProperty("value", row.value());
            a.add(o);
        }
        return WebResponse.json(a.toString());
    }

    private WebResponse visibility(WebRequest r) throws Exception {
        requireOp(r);
        JsonObject o = body(r);
        String key = str(o, "key");
        boolean on = o.get("visible").getAsBoolean();
        db.setVisible(key, on);
        web.log(r.admin(), "PeakStats: " + key + (on ? " fuer Spieler sichtbar" : " nur fuer Admins"));
        web.changed("peakstats", r.admin());
        return WebResponse.json(catalog().toString());
    }

    private WebResponse set(WebRequest r) throws Exception {
        requireOp(r);
        JsonObject o = body(r);
        UUID id = uuidOf(str(o, "uuid"));
        String key = str(o, "key");
        long value = o.get("value").getAsLong();
        if (db.nameOf(id) == null) throw new IllegalArgumentException("Unbekannter Spieler.");
        db.set(id, key, value);
        web.log(r.admin(), "PeakStats: " + db.nameOf(id) + " " + key + " = " + value);
        web.changed("peakstats", r.admin());
        return WebResponse.ok();
    }

    private WebResponse reset(WebRequest r) throws Exception {
        requireOp(r);
        JsonObject o = body(r);
        if (o.has("all") && o.get("all").getAsBoolean()) {
            if (!o.has("confirm") || !"ALLE".equals(o.get("confirm").getAsString())) throw new IllegalArgumentException("Zum Loeschen aller Werte confirm=ALLE senden.");
            db.resetAll();
            web.log(r.admin(), "PeakStats: ALLE Werte zurueckgesetzt");
        } else {
            UUID id = uuidOf(str(o, "uuid"));
            String name = db.nameOf(id);
            if (name == null) throw new IllegalArgumentException("Unbekannter Spieler.");
            db.resetPlayer(id);
            web.log(r.admin(), "PeakStats: Werte von " + name + " zurueckgesetzt");
        }
        web.changed("peakstats", r.admin());
        return WebResponse.ok();
    }

    private WebResponse csv() throws SQLException {
        StringBuilder sb = new StringBuilder("name,uuid,last_seen");
        for (StatKeys.Def d : StatKeys.all()) sb.append(',').append(d.key());
        sb.append('\n');
        for (var e : db.players()) {
            JsonObject o = e.getAsJsonObject();
            JsonObject st = o.getAsJsonObject("stats");
            sb.append(cell(o.get("name").getAsString())).append(',').append(o.get("uuid").getAsString()).append(',').append(o.get("lastSeen").getAsLong());
            for (StatKeys.Def d : StatKeys.all()) sb.append(',').append(st.has(d.key()) ? st.get(d.key()).getAsLong() : 0);
            sb.append('\n');
        }
        return WebResponse.bytes("text/csv; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Schuetzt vor CSV-Injektion (Tabellenprogramme fuehren Zellen mit = + - @ am Anfang als Formel aus). */
    static String cell(String s) {
        String t = s.replace("\"", "\"\"");
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        return "\"" + t + "\"";
    }
}
