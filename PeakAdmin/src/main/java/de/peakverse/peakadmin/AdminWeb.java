package de.peakverse.peakadmin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Web-Seite von PeakAdmin ({@code /api/peakadmin/...}): Spieler, Konsole, Sicherungen, Neustart, Status und Chat. */
public final class AdminWeb {
    private static final Gson GSON = new Gson();

    /** Einstellungen, die die Web-Seite lesen und aendern darf. */
    public interface Settings {
        JsonObject get();

        void set(JsonObject o) throws IOException;
    }

    private final WebRegistry web;
    private final StatsHistory stats;
    private final ChatLog chat;
    private final MuteStore mutes;
    private final LogTail logTail;
    private final ServerControl control;
    private final Settings settings;

    public AdminWeb(WebRegistry web, StatsHistory stats, ChatLog chat, MuteStore mutes, LogTail logTail, ServerControl control, Settings settings) {
        this.web = web;
        this.stats = stats;
        this.chat = chat;
        this.mutes = mutes;
        this.logTail = logTail;
        this.control = control;
        this.settings = settings;
    }

    private interface Action {
        Object run() throws Exception;
    }

    public void register(String script) {
        web.panel("Verwaltung", "Admin", script);
        web.route("players", r -> main(() -> WebResponse.json(PlayerTools.list().toString())));
        web.route("player/*", this::player);
        web.route("console", this::console);
        web.route("console/run", this::consoleRun);
        web.route("status", r -> main(() -> WebResponse.json(status().toString())));
        web.route("chat", r -> main(() -> WebResponse.json(chatJson(r).toString())));
        web.route("chat/say", this::say);
        web.route("backups", r -> main(() -> WebResponse.json(backups().toString())));
        web.route("backups/create", this::backupCreate);
        web.route("backups/delete", this::backupDelete);
        web.route("restart", this::restart);
        web.route("settings", this::settings);
        web.route("lp", r -> main(() -> {
            JsonObject o = lpOn() ? LpBridge.groups() : new JsonObject();
            o.addProperty("available", lpOn());
            return WebResponse.json(o.toString());
        }));
        web.route("lp/group", this::lpGroup);
        web.route("lp/user/*", this::lpUser);
    }

    // ---- LuckPerms ----

    private WebResponse lpGroup(WebRequest r) throws Exception {
        JsonObject o = body(r);
        String act = str(o, "action");
        return change(r, "lp", "LuckPerms Gruppe: " + act + " " + (o.has("group") ? o.get("group").getAsString() : ""), () -> {
            requireOp(r);
            if (!lpOn()) throw new IllegalStateException("LuckPerms ist nicht installiert.");
            String g = str(o, "group");
            switch (act) {
                case "create" -> LpBridge.createGroup(g);
                case "delete" -> LpBridge.deleteGroup(g);
                case "perm" -> LpBridge.groupPermission(g, str(o, "key"), o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsBoolean() : null);
                case "parent" -> LpBridge.groupParent(g, str(o, "parent"), o.get("on").getAsBoolean());
                default -> throw new IllegalArgumentException("Unbekannte Aktion: " + act);
            }
            return LpBridge.groups();
        });
    }

    private WebResponse lpUser(WebRequest r) throws Exception {
        UUID id;
        try {
            id = UUID.fromString(r.path().split("/")[2]);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return WebResponse.error(400, "Ungueltige Spieler-Kennung");
        }
        if (r.method().equals("GET")) return main(() -> {
            if (!lpOn()) throw new IllegalStateException("LuckPerms ist nicht installiert.");
            return WebResponse.json(LpBridge.userInfo(id).toString());
        });
        JsonObject o = body(r);
        String act = str(o, "action");
        return change(r, "lp", "LuckPerms Spieler " + id + ": " + act, () -> {
            requireOp(r);
            if (!lpOn()) throw new IllegalStateException("LuckPerms ist nicht installiert.");
            switch (act) {
                case "group" -> LpBridge.userGroup(id, str(o, "group"), o.get("on").getAsBoolean());
                case "perm" -> LpBridge.userPermission(id, str(o, "key"), o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsBoolean() : null);
                default -> throw new IllegalArgumentException("Unbekannte Aktion: " + act);
            }
            return LpBridge.userInfo(id);
        });
    }

    // ---- Hilfen ----

    private WebResponse main(Callable<WebResponse> task) throws Exception {
        try {
            return web.onMain(task);
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            return WebResponse.error(400, e.getMessage() == null ? "Ungueltige Eingabe." : e.getMessage());
        } catch (NullPointerException | UnsupportedOperationException | ClassCastException e) {
            return WebResponse.error(400, "Ungueltige Eingabe (Feld fehlt oder hat den falschen Typ).");
        } catch (IOException e) {
            return WebResponse.error(500, "Dateifehler: " + e.getMessage());
        }
    }

    /** Aendernde Aktion: nur POST, auf dem Haupt-Thread, mit Protokoll und Live-Meldung. */
    private WebResponse change(WebRequest r, String event, String what, Action a) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        WebResponse res = main(() -> {
            Object o = a.run();
            return o == null ? WebResponse.ok() : WebResponse.json(o.toString());
        });
        if (res.status() < 300) {
            web.log(r.admin(), what);
            Bukkit.getLogger().info("[PeakAdmin/Web] " + r.admin() + ": " + what);
            web.changed(event, r.admin());
        }
        return res;
    }

    /** Nur den Plugin-Namen pruefen: die LuckPerms-Klassen (LpBridge) werden erst geladen, wenn das Plugin da ist. */
    private static boolean lpOn() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    private static boolean isOp(String name) {
        for (OfflinePlayer op : Bukkit.getOperators()) if (name.equalsIgnoreCase(op.getName())) return true;
        return false;
    }

    private static void requireOp(WebRequest r) {
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

    private static int num(JsonObject o, String k, int d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : d;
    }

    // ---- Spieler ----

    private WebResponse player(WebRequest r) throws Exception {
        String[] p = r.path().split("/"); // player/<uuid>[/<aktion>]
        UUID id;
        try {
            id = UUID.fromString(p[1]);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return WebResponse.error(400, "Ungueltige Spieler-Kennung");
        }
        if (p.length == 2) return main(() -> WebResponse.json(PlayerTools.detail(id, mutes).toString()));
        String act = p[2];
        if (act.equals("advancements") && r.method().equals("GET")) return main(() -> WebResponse.json(PlayerTools.advancements(id).toString()));
        String who = Bukkit.getOfflinePlayer(id).getName();
        String name = who == null ? id.toString() : who;
        return change(r, "player", "Spieler " + name + ": " + act, () -> {
            JsonObject o = r.body().length == 0 ? new JsonObject() : body(r);
            switch (act) {
                case "kick" -> PlayerTools.kick(id, o.has("reason") ? str(o, "reason") : "");
                case "ban" -> PlayerTools.ban(name, o.has("reason") ? str(o, "reason") : "Vom Server gesperrt");
                case "pardon" -> PlayerTools.pardon(name);
                case "whitelist" -> PlayerTools.whitelist(name, o.get("on").getAsBoolean());
                case "op" -> {
                    requireOp(r);
                    PlayerTools.op(name, o.get("on").getAsBoolean());
                }
                case "mute" -> mutes.mute(id, name, r.admin(), o.has("reason") ? PlayerTools.safeText(str(o, "reason")) : "", num(o, "hours", 0));
                case "unmute" -> mutes.unmute(id);
                case "heal" -> PlayerTools.heal(id);
                case "gamemode" -> PlayerTools.gamemode(id, str(o, "mode"));
                case "level" -> PlayerTools.setLevel(id, num(o, "level", 0));
                case "tp-player" -> PlayerTools.teleportToPlayer(id, UUID.fromString(str(o, "target")));
                case "tp-pos" -> PlayerTools.teleportTo(id, str(o, "world"), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                case "remove-slot" -> PlayerTools.removeSlot(id, str(o, "inv"), num(o, "slot", -1));
                case "clear" -> {
                    requireOp(r);
                    PlayerTools.clear(id, str(o, "inv"));
                }
                case "give" -> PlayerTools.give(id, str(o, "inv"), str(o, "item"), num(o, "amount", 1));
                case "advancement" -> {
                    int n = PlayerTools.advancement(id, str(o, "key"), o.get("grant").getAsBoolean());
                    JsonObject res = new JsonObject();
                    res.addProperty("changed", n);
                    return res;
                }
                default -> throw new IllegalArgumentException("Unbekannte Aktion: " + act);
            }
            return PlayerTools.detail(id, mutes);
        });
    }

    // ---- Konsole ----

    private WebResponse console(WebRequest r) throws Exception {
        long since = r.query().containsKey("since") ? Long.parseLong(r.query().get("since")) : -1;
        LogTail.Chunk c = logTail.read(since);
        JsonObject o = new JsonObject();
        o.addProperty("position", c.position());
        o.addProperty("reset", c.reset());
        JsonArray lines = new JsonArray();
        c.lines().forEach(lines::add);
        o.add("lines", lines);
        return WebResponse.json(o.toString());
    }

    private WebResponse consoleRun(WebRequest r) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        JsonObject o = body(r);
        String cmd = PlayerTools.safeText(str(o, "cmd"));
        if (cmd.isEmpty()) return WebResponse.error(400, "Leerer Befehl.");
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String run = cmd;
        return change(r, "console", "Konsole: " + run, () -> {
            requireOp(r);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
            return null;
        });
    }

    // ---- Status, Chat ----

    private JsonObject status() {
        JsonObject o = new JsonObject();
        Runtime rt = Runtime.getRuntime();
        double[] tps = Bukkit.getTPS();
        o.addProperty("tps", Math.min(20.0, tps[0]));
        o.addProperty("mspt", Bukkit.getAverageTickTime());
        o.addProperty("ramUsed", rt.totalMemory() - rt.freeMemory());
        o.addProperty("ramMax", rt.maxMemory());
        o.addProperty("players", Bukkit.getOnlinePlayers().size());
        o.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        o.addProperty("version", Bukkit.getMinecraftVersion());
        o.addProperty("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        JsonArray worlds = new JsonArray();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", w.getName());
            j.addProperty("entities", w.getEntityCount());
            j.addProperty("chunks", w.getChunkCount());
            j.addProperty("players", w.getPlayerCount());
            worlds.add(j);
        }
        o.add("worlds", worlds);
        JsonArray h = new JsonArray();
        for (StatsHistory.Sample s : stats.snapshot()) {
            JsonObject j = new JsonObject();
            j.addProperty("t", s.time());
            j.addProperty("tps", s.tps());
            j.addProperty("mspt", s.mspt());
            j.addProperty("ram", s.ramUsed());
            j.addProperty("players", s.players());
            j.addProperty("entities", s.entities());
            j.addProperty("chunks", s.chunks());
            h.add(j);
        }
        o.add("history", h);
        return o;
    }

    private JsonObject chatJson(WebRequest r) {
        long since = r.query().containsKey("since") ? Long.parseLong(r.query().get("since")) : 0;
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (ChatLog.Entry e : chat.since(since)) {
            JsonObject j = new JsonObject();
            j.addProperty("seq", e.seq());
            j.addProperty("t", e.time());
            j.addProperty("player", e.player());
            j.addProperty("text", e.text());
            a.add(j);
        }
        o.add("messages", a);
        o.addProperty("last", chat.lastSeq());
        JsonArray m = new JsonArray();
        mutes.activeAll().forEach((id, mu) -> {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", id);
            j.addProperty("name", mu.name);
            j.addProperty("reason", mu.reason);
            j.addProperty("until", mu.until);
            m.add(j);
        });
        o.add("mutes", m);
        return o;
    }

    private WebResponse say(WebRequest r) throws Exception {
        String text = PlayerTools.safeText(str(body(r), "text"));
        if (text.isEmpty()) return WebResponse.error(400, "Leere Nachricht.");
        return change(r, "chat", "Chat-Nachricht gesendet", () -> {
            Bukkit.broadcast(net.kyori.adventure.text.Component.text("[" + r.admin() + "] ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(net.kyori.adventure.text.Component.text(text, net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            chat.add("[Web] " + r.admin(), text);
            return null;
        });
    }

    // ---- Sicherungen, Neustart, Einstellungen ----

    private JsonObject backups() throws IOException {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (BackupStore.Backup b : control.backups().list()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", b.name());
            j.addProperty("size", b.size());
            j.addProperty("time", b.time());
            a.add(j);
        }
        o.add("backups", a);
        o.addProperty("running", control.backupRunning());
        o.addProperty("last", control.lastBackupInfo());
        o.addProperty("folder", control.backups().folder().toString());
        o.addProperty("restartAt", control.restartAt());
        return o;
    }

    private WebResponse backupCreate(WebRequest r) throws Exception {
        return change(r, "backups", "Sicherung gestartet", () -> {
            requireOp(r);
            int keep = settings.get().get("keep").getAsInt();
            if (!control.backupNow("web", keep, err -> web.changed("backups", r.admin()))) throw new IllegalStateException("Es laeuft schon eine Sicherung.");
            return backups();
        });
    }

    private WebResponse backupDelete(WebRequest r) throws Exception {
        String name = str(body(r), "name");
        return change(r, "backups", "Sicherung geloescht: " + name, () -> {
            requireOp(r);
            control.backups().delete(name);
            return backups();
        });
    }

    private WebResponse restart(WebRequest r) throws Exception {
        JsonObject o = body(r);
        boolean cancel = o.has("cancel") && o.get("cancel").getAsBoolean();
        int minutes = num(o, "minutes", 5);
        return change(r, "backups", cancel ? "Neustart abgesagt" : "Neustart geplant in " + minutes + " Minute(n)", () -> {
            requireOp(r);
            if (cancel) control.cancelRestart();
            else control.scheduleRestart(minutes, o.has("reason") ? str(o, "reason") : "");
            return backups();
        });
    }

    private WebResponse settings(WebRequest r) throws Exception {
        if (r.method().equals("GET")) return main(() -> WebResponse.json(settings.get().toString()));
        JsonObject o = body(r);
        return change(r, "backups", "Einstellungen geaendert", () -> {
            requireOp(r);
            settings.set(o);
            return settings.get();
        });
    }
}