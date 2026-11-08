package de.peakverse.peakachievements;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;

/**
 * Web-Seite von PeakAchievements ({@code /api/peakachievements/...}): Baum ansehen, Minecraft-Fortschritte und eigene Achievements bearbeiten,
 * per Ziehen umhaengen. Aenderungen landen zuerst im Entwurf ({@code draft/}) und gelten erst nach "Veroeffentlichen" (mit Sicherung in {@code backups/}).
 */
public final class AchievementsWeb {
    private static final Gson GSON = new Gson();

    /** Zugriff auf das Modul. */
    public interface Host {
        AchievementStore live();

        Path folder();

        boolean peakMode();

        /** Laedt die Dateien neu und veroeffentlicht den Baum (Haupt-Thread). */
        void applyLive() throws IOException;
    }

    private final WebRegistry web;
    private final Host host;
    private AchievementStore draft;

    public AchievementsWeb(WebRegistry web, Host host) {
        this.web = web;
        this.host = host;
    }

    public void register(String script) {
        web.panel("Achievements", "Achievements", script);
        web.route("state", r -> main(() -> WebResponse.json(state())));
        web.route("meta", r -> WebResponse.json(meta()));
        web.route("vanilla", r -> change(r, () -> editVanilla(r)));
        web.route("custom", r -> change(r, () -> editCustom(r)));
        web.route("delete", r -> change(r, () -> deleteCustom(r)));
        web.route("parent", r -> change(r, () -> reparent(r)));
        web.route("publish", r -> change(r, () -> publish(r)));
        web.route("discard", r -> change(r, () -> discard(r)));
    }

    private interface Action {
        void run() throws Exception;
    }

    private WebResponse main(java.util.concurrent.Callable<WebResponse> call) throws Exception {
        return web.onMain(call);
    }

    private WebResponse change(WebRequest r, Action action) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        if (!host.peakMode()) return WebResponse.error(409, "PeakAchievements steht auf vanilla: erst /peakachievements mode peak.");
        return web.onMain(() -> {
            try {
                action.run();
            } catch (IllegalArgumentException e) {
                return WebResponse.error(400, e.getMessage());
            }
            web.changed("achievements", r.admin());
            return WebResponse.json(state());
        });
    }

    // ---- Entwurf ----

    private AchievementStore view() {
        return draft != null ? draft : host.live();
    }

    /** Legt den Entwurf an (Kopie des Live-Stands), falls es noch keinen gibt. */
    private AchievementStore edit() throws IOException {
        if (draft != null) return draft;
        Path dir = host.folder().resolve("draft");
        Files.createDirectories(dir);
        for (String f : List.of("achievements.json", "vanilla.json")) {
            Path src = host.folder().resolve(f);
            if (Files.isRegularFile(src)) Files.copy(src, dir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            else Files.deleteIfExists(dir.resolve(f));
        }
        AchievementStore s = new AchievementStore(dir);
        s.load();
        draft = s;
        return s;
    }

    private void publish(WebRequest r) throws IOException {
        if (draft == null) throw new IllegalArgumentException("Es gibt keinen Entwurf.");
        Path stamp = host.folder().resolve("backups").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(stamp);
        Path dir = host.folder().resolve("draft");
        for (String f : List.of("achievements.json", "vanilla.json")) {
            Path live = host.folder().resolve(f);
            if (Files.isRegularFile(live)) Files.copy(live, stamp.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            Path d = dir.resolve(f);
            if (Files.isRegularFile(d)) Files.copy(d, live, StandardCopyOption.REPLACE_EXISTING);
        }
        discardFiles();
        web.log(r.admin(), "Achievements veroeffentlicht (Sicherung " + stamp.getFileName() + ")");
        host.applyLive();
    }

    private void discard(WebRequest r) {
        if (draft == null) return;
        discardFiles();
        web.log(r.admin(), "Achievements-Entwurf verworfen");
    }

    private void discardFiles() {
        draft = null;
        Path dir = host.folder().resolve("draft");
        try {
            for (String f : List.of("achievements.json", "vanilla.json", "achievements.json.tmp", "vanilla.json.tmp")) Files.deleteIfExists(dir.resolve(f));
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // bleibt liegen, beim naechsten Entwurf wird ueberschrieben
        }
    }

    // ---- Bearbeiten (immer im Entwurf) ----

    private void editVanilla(WebRequest r) throws IOException {
        JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
        String key = str(o, "key");
        if (key == null || !key.startsWith("minecraft:") || Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(key)) == null) throw new IllegalArgumentException("Unbekannter Minecraft-Fortschritt.");
        AchievementStore s = edit();
        if (o.has("reset") && o.get("reset").getAsBoolean()) {
            s.resetVanilla(key);
            web.log(r.admin(), "Minecraft-Fortschritt zurueckgesetzt (Entwurf): " + key);
            return;
        }
        VanillaOverride v = s.vanilla(key);
        if (o.has("enabled")) v.enabled = o.get("enabled").getAsBoolean();
        if (o.has("title")) v.title = blankToNull(str(o, "title"), 60);
        if (o.has("description")) v.description = blankToNull(str(o, "description"), 200);
        if (o.has("icon")) v.icon = icon(str(o, "icon"));
        if (o.has("points")) v.points = o.get("points").isJsonNull() ? null : bounded(o.get("points").getAsInt(), 0, 10_000);
        if (o.has("visibility")) v.visibility = o.get("visibility").isJsonNull() ? null : visibility(str(o, "visibility"));
        s.putVanilla(key, v);
        web.log(r.admin(), "Minecraft-Fortschritt bearbeitet (Entwurf): " + key);
    }

    private void editCustom(WebRequest r) throws IOException {
        Achievement a = GSON.fromJson(r.text(), Achievement.class);
        if (a == null || a.id == null) throw new IllegalArgumentException("Kennung fehlt.");
        if (a.rewards == null) a.rewards = new Achievement.Rewards();
        if (a.icon == null || a.icon.isBlank()) a.icon = "STONE";
        a.icon = a.icon.toUpperCase(Locale.ROOT);
        if (Material.matchMaterial(a.icon) == null) throw new IllegalArgumentException("Unbekanntes Symbol: " + a.icon);
        AchievementStore s = edit();
        Achievement old = s.get(a.id);
        boolean op = isOp(r.admin());
        List<String> oldCommands = old == null ? List.of() : old.rewards.commands;
        if (!a.rewards.commands.equals(oldCommands) && !op) throw new IllegalArgumentException("Konsolen-Befehle als Belohnung darf nur ein Operator setzen.");
        s.put(a);
        web.log(r.admin(), (old == null ? "Achievement angelegt (Entwurf): " : "Achievement bearbeitet (Entwurf): ") + a.id);
    }

    private void deleteCustom(WebRequest r) throws IOException {
        String id = str(GSON.fromJson(r.text(), JsonObject.class), "id");
        AchievementStore s = edit();
        s.remove(id);
        web.log(r.admin(), "Achievement geloescht (Entwurf): " + id);
    }

    /** Haengt einen Knoten um ({@code key}: Minecraft-Schluessel oder eigene Kennung; {@code parent}: dasselbe oder leer = Wurzel). */
    private void reparent(WebRequest r) throws IOException {
        JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
        String key = str(o, "key");
        String parent = str(o, "parent");
        if (parent == null) parent = "";
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Knoten fehlt.");
        if (key.equals(parent)) throw new IllegalArgumentException("Ein Knoten kann nicht an sich selbst haengen.");
        AchievementStore s = edit();
        if (!parent.isEmpty() && !exists(s, parent)) throw new IllegalArgumentException("Den Zielknoten gibt es nicht.");
        if (wouldCycle(s, key, parent)) throw new IllegalArgumentException("Das wuerde einen Kreis bilden: der Zielknoten haengt selbst unter diesem Knoten.");
        if (key.startsWith("minecraft:")) {
            VanillaOverride v = s.vanilla(key);
            v.parent = parent.isEmpty() ? "" : parent;
            s.putVanilla(key, v);
        } else {
            Achievement a = s.get(key);
            if (a == null) throw new IllegalArgumentException("Unbekanntes Achievement.");
            a.parent = parent;
            s.put(a);
        }
        web.log(r.admin(), "Umgehaengt (Entwurf): " + key + " -> " + (parent.isEmpty() ? "Wurzel" : parent));
    }

    private boolean exists(AchievementStore s, String key) {
        return key.startsWith("minecraft:") ? Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(key)) != null : s.get(key) != null;
    }

    /** Der aktuelle Elternteil eines Knotens (leer = Wurzel). */
    private String parentOf(AchievementStore s, String key) {
        if (key.startsWith("minecraft:")) {
            VanillaOverride v = s.vanilla(key);
            if (v.parent != null) return v.parent;
            Advancement adv = Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(key));
            Advancement p = adv == null ? null : adv.getParent();
            while (p != null && p.getDisplay() == null) p = p.getParent();
            return p == null ? "" : p.getKey().toString();
        }
        Achievement a = s.get(key);
        return a == null || a.parent == null ? "" : a.parent;
    }

    private boolean wouldCycle(AchievementStore s, String key, String newParent) {
        String cur = newParent;
        int guard = 0;
        while (cur != null && !cur.isEmpty()) {
            if (cur.equals(key) || ++guard > 2000) return true;
            cur = parentOf(s, cur);
        }
        return false;
    }

    // ---- Lesen ----

    private String state() {
        AchievementStore s = view();
        JsonObject o = new JsonObject();
        o.addProperty("mode", host.peakMode() ? "peak" : "vanilla");
        o.addProperty("draft", draft != null);
        JsonArray nodes = new JsonArray();
        for (Advancement adv : VanillaMirror.collect()) {
            String key = adv.getKey().toString();
            VanillaOverride v = s.vanilla(key);
            var d = adv.getDisplay();
            JsonObject n = new JsonObject();
            n.addProperty("key", key);
            n.addProperty("kind", "vanilla");
            n.addProperty("title", v.title != null ? v.title : plain(d.title()));
            n.addProperty("baseTitle", plain(d.title()));
            n.addProperty("description", v.description != null ? v.description : plain(d.description()));
            n.addProperty("baseDescription", plain(d.description()));
            n.addProperty("icon", (v.icon != null ? v.icon : d.icon().getType().name()).toLowerCase(Locale.ROOT));
            n.addProperty("baseIcon", d.icon().getType().name().toLowerCase(Locale.ROOT));
            n.addProperty("frame", d.frame().name().toLowerCase(Locale.ROOT));
            n.addProperty("enabled", v.enabled);
            n.addProperty("parent", parentOf(s, key));
            n.addProperty("baseParent", parentOf(new AchievementStore(host.folder().resolve("none")), key));
            n.addProperty("points", v.points == null ? 0 : v.points);
            n.addProperty("visibility", (v.visibility != null ? v.visibility : d.isHidden() ? Achievement.Visibility.UNTIL_DONE : Achievement.Visibility.AFTER_PARENT).name());
            n.addProperty("changed", s.vanillaAll().containsKey(key));
            nodes.add(n);
        }
        for (Achievement a : s.all()) {
            JsonObject n = JsonParserHolder.tree(a);
            n.addProperty("key", a.id);
            n.addProperty("kind", "custom");
            n.addProperty("icon", a.icon.toLowerCase(Locale.ROOT));
            n.addProperty("frame", a.frame.name().toLowerCase(Locale.ROOT));
            nodes.add(n);
        }
        o.add("nodes", nodes);
        return o.toString();
    }

    private String meta() {
        JsonObject o = new JsonObject();
        JsonArray mats = new JsonArray();
        for (Material m : Material.values()) if (m.isItem() && !m.isLegacy() && !m.isAir()) mats.add(m.name().toLowerCase(Locale.ROOT));
        o.add("materials", mats);
        JsonArray ents = new JsonArray();
        for (org.bukkit.entity.EntityType t : org.bukkit.entity.EntityType.values()) if (t != org.bukkit.entity.EntityType.UNKNOWN) ents.add(t.name());
        o.add("entities", ents);
        JsonArray types = new JsonArray();
        for (Achievement.Type t : Achievement.Type.values()) types.add(t.name());
        o.add("types", types);
        return o.toString();
    }

    // ---- Hilfen ----

    private static String plain(net.kyori.adventure.text.Component c) {
        try {
            return io.papermc.paper.text.PaperComponents.plainTextSerializer().serialize(c);
        } catch (RuntimeException | LinkageError e) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
        }
    }

    private static boolean isOp(String name) {
        return name != null && Bukkit.getOfflinePlayer(name).isOp();
    }

    private static String str(JsonObject o, String field) {
        return o == null || !o.has(field) || o.get(field).isJsonNull() ? null : o.get(field).getAsString();
    }

    private static String blankToNull(String s, int max) {
        if (s == null || s.isBlank()) return null;
        if (s.length() > max) throw new IllegalArgumentException("Text ist laenger als " + max + " Zeichen.");
        return s;
    }

    private static String icon(String s) {
        if (s == null || s.isBlank()) return null;
        if (Material.matchMaterial(s) == null) throw new IllegalArgumentException("Unbekanntes Symbol: " + s);
        return s.toUpperCase(Locale.ROOT);
    }

    private static int bounded(int v, int min, int max) {
        if (v < min || v > max) throw new IllegalArgumentException("Wert muss zwischen " + min + " und " + max + " liegen.");
        return v;
    }

    private static Achievement.Visibility visibility(String s) {
        try {
            return Achievement.Visibility.valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unbekannte Sichtbarkeit.");
        }
    }

    /** Wandelt ein Achievement in ein JSON-Objekt (alle Felder). */
    private static final class JsonParserHolder {
        static JsonObject tree(Achievement a) {
            return GSON.toJsonTree(a).getAsJsonObject();
        }
    }
}
