package de.peakverse.peakenchants;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Verwaltet Datapack-Addons mit Verzauberungen im Ordner {@code <Welt>/datapacks}: installieren (ZIP), an/aus, entfernen und die Verzauberungs-Dateien
 * bearbeiten. Aenderungen an Verzauberungen landen zuerst im Entwurf (im Speicher) und werden mit {@link #publish()} geschrieben (mit Sicherung).
 * Minecraft liest Verzauberungen nur beim Serverstart; jede Aenderung setzt deshalb {@link #restartNeeded()}.
 */
public final class EnchantStore {
    private static final Gson GSON = new Gson();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final long MAX_TOTAL = 100L << 20;
    private static final int MAX_ENTRIES = 20_000;
    /** Tags, in die man eine Verzauberung per Schalter aufnehmen kann. */
    public static final List<String> TAGS = List.of("in_enchanting_table", "on_random_loot", "treasure", "non_treasure", "curse", "tradeable", "on_traded_equipment", "tooltip_order");
    public static final String OWN_ADDON = "peakenchants";

    public record Addon(String id, String name, String description, boolean enabled, boolean zip, int count) { }

    private final Path datapacks;
    private final Path backups;
    /** Entwurf: Pfad relativ zum Datapacks-Ordner ("addon/data/...") -> neuer Inhalt, {@code null} = loeschen. */
    private final Map<String, byte[]> staged = new LinkedHashMap<>();
    private boolean restartNeeded;

    public EnchantStore(Path datapacks, Path backups) {
        this.datapacks = datapacks.toAbsolutePath().normalize();
        this.backups = backups.toAbsolutePath().normalize();
    }

    public boolean restartNeeded() { return restartNeeded; }

    public boolean hasDraft() { return !staged.isEmpty(); }

    public int draftSize() { return staged.size(); }

    // ---- Addons ----

    public List<Addon> addons() throws IOException {
        List<Addon> out = new ArrayList<>();
        if (!Files.isDirectory(datapacks)) return out;
        try (Stream<Path> s = Files.list(datapacks)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                String n = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    boolean on = Files.isRegularFile(p.resolve("pack.mcmeta"));
                    if (!on && !Files.isRegularFile(p.resolve("pack.mcmeta.off"))) continue;
                    if (!Files.isDirectory(p.resolve("data"))) continue;
                    int count = enchantKeys(n).size();
                    if (count == 0 && !n.equals(OWN_ADDON)) continue; // z. B. der Ordner "bukkit", den Paper anlegt
                    out.add(new Addon(n, n, description(p.resolve(on ? "pack.mcmeta" : "pack.mcmeta.off")), on, false, count));
                } else if (n.endsWith(".zip") && Files.isRegularFile(p)) {
                    int count = 0;
                    try (ZipFile z = new ZipFile(p.toFile())) {
                        for (var e = z.entries(); e.hasMoreElements(); ) if (isEnchantFile(e.nextElement().getName())) count++;
                    } catch (IOException ignored) {
                        continue;
                    }
                    if (count > 0) out.add(new Addon(n, n, "", true, true, count));
                }
            }
        }
        return out;
    }

    private static String description(Path mcmeta) {
        try {
            JsonObject o = GSON.fromJson(Files.readString(mcmeta, StandardCharsets.UTF_8), JsonObject.class);
            return text(o.getAsJsonObject("pack").get("description"));
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isEnchantFile(String name) {
        String[] p = name.split("/");
        return p.length == 4 && p[0].equals("data") && p[2].equals("enchantment") && p[3].endsWith(".json");
    }

    /** Installiert oder ersetzt ein Addon aus einer ZIP-Datei (altes wird gesichert). */
    public void install(String id, byte[] zip) throws IOException {
        id = checkId(id);
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (files.size() > MAX_ENTRIES) throw new IllegalArgumentException("Die ZIP hat zu viele Dateien.");
                String n = e.getName().replace('\\', '/');
                if (n.startsWith("/") || n.contains("..") || n.contains(":")) throw new IllegalArgumentException("Unsicherer Pfad in der ZIP: " + n);
                if (e.isDirectory()) continue;
                byte[] data = in.readNBytes((int) (MAX_TOTAL - total + 1));
                total += data.length;
                if (total > MAX_TOTAL) throw new IllegalArgumentException("Die ZIP ist entpackt groesser als 100 MB.");
                files.put(n, data);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Das ist keine gueltige ZIP-Datei.");
        }
        String prefix = "";
        if (!files.containsKey("pack.mcmeta")) {
            Set<String> tops = new LinkedHashSet<>();
            for (String n : files.keySet()) if (n.contains("/")) tops.add(n.substring(0, n.indexOf('/') + 1));
            if (tops.size() == 1 && files.containsKey(tops.iterator().next() + "pack.mcmeta")) prefix = tops.iterator().next();
            else throw new IllegalArgumentException("In der ZIP fehlt die Datei pack.mcmeta (kein Datapack).");
        }
        boolean any = false;
        for (String n : files.keySet()) if (n.startsWith(prefix) && isEnchantFile(n.substring(prefix.length()))) any = true;
        if (!any) throw new IllegalArgumentException("Das Datapack enthaelt keine Verzauberungen (data/<name>/enchantment/*.json).");
        Files.createDirectories(datapacks);
        Path tmp = Files.createTempDirectory(datapacks, ".install-");
        try {
            for (var e : files.entrySet()) {
                if (!e.getKey().startsWith(prefix)) continue;
                Path t = tmp.resolve(e.getKey().substring(prefix.length())).normalize();
                if (!t.startsWith(tmp)) throw new IllegalArgumentException("Unsicherer Pfad: " + e.getKey());
                Files.createDirectories(t.getParent());
                Files.write(t, e.getValue());
            }
            Path target = datapacks.resolve(id);
            if (Files.exists(target)) archive(target, id);
            Files.move(tmp, target);
            dropStaged(id);
            restartNeeded = true;
        } finally {
            deleteTree(tmp);
        }
    }

    /** Entpackt ein ZIP-Addon, das schon im Datapacks-Ordner liegt, in einen Ordner (die ZIP wird gesichert). */
    public void unpack(String zipName) throws IOException {
        if (!zipName.endsWith(".zip") || zipName.contains("/") || zipName.contains("\\")) throw new IllegalArgumentException("Keine ZIP-Datei.");
        Path z = datapacks.resolve(zipName);
        if (!Files.isRegularFile(z)) throw new IllegalArgumentException("Die Datei gibt es nicht.");
        String id = checkId(zipName.substring(0, zipName.length() - 4).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
        byte[] data = Files.readAllBytes(z);
        // Erst die ZIP wegraeumen: laeuft eine Datei gerade im Server (Windows), schlaegt das fehl, bevor etwas geaendert wurde
        Files.createDirectories(backups);
        Path saved = backups.resolve(stamp() + "-" + zipName);
        try {
            Files.move(z, saved);
        } catch (IOException e) {
            throw new IllegalArgumentException("Die ZIP ist gerade vom Server in Benutzung und laesst sich nicht verschieben (Windows). Server stoppen und die ZIP von Hand entpacken, oder das Addon hier als ZIP neu hochladen.");
        }
        try {
            install(id, data);
        } catch (IOException | RuntimeException e) {
            Files.move(saved, z);
            throw e;
        }
    }

    public void setEnabled(String id, boolean enabled) throws IOException {
        Path dir = folder(id);
        Path on = dir.resolve("pack.mcmeta"), off = dir.resolve("pack.mcmeta.off");
        if (enabled && Files.isRegularFile(off)) Files.move(off, on);
        else if (!enabled && Files.isRegularFile(on)) Files.move(on, off);
        restartNeeded = true;
    }

    public void remove(String id) throws IOException {
        archive(folder(id), id);
        dropStaged(id);
        restartNeeded = true;
    }

    private Path folder(String id) {
        Path p = datapacks.resolve(checkId(id));
        if (!Files.isDirectory(p)) throw new IllegalArgumentException("Das Addon gibt es nicht: " + id);
        return p;
    }

    private static String checkId(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Ungueltiger Name (erlaubt: a-z 0-9 _ . -).");
        return id;
    }

    private void archive(Path p, String name) throws IOException {
        Files.createDirectories(backups);
        Path dest = backups.resolve(stamp() + "-" + name);
        Files.move(p, dest);
    }

    private static String stamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")); }

    private void dropStaged(String id) { staged.keySet().removeIf(k -> k.startsWith(id + "/")); }

    private static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        } catch (IOException ignored) {
            // bleibt liegen
        }
    }

    // ---- Verzauberungen lesen (Entwurf eingerechnet) ----

    private byte[] read(String addon, String rel) throws IOException {
        String key = addon + "/" + rel;
        if (staged.containsKey(key)) return staged.get(key);
        Path f = datapacks.resolve(addon).resolve(rel);
        return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
    }

    /** Schluessel ("namespace:id") aller Verzauberungen eines Addons. */
    public Set<String> enchantKeys(String addon) throws IOException {
        Set<String> keys = new java.util.TreeSet<>();
        Path data = datapacks.resolve(addon).resolve("data");
        if (Files.isDirectory(data)) {
            try (Stream<Path> s = Files.list(data)) {
                for (Path ns : (Iterable<Path>) s::iterator) {
                    Path dir = ns.resolve("enchantment");
                    if (!Files.isDirectory(dir)) continue;
                    try (Stream<Path> f = Files.list(dir)) {
                        f.map(x -> x.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> keys.add(ns.getFileName() + ":" + n.substring(0, n.length() - 5)));
                    }
                }
            }
        }
        for (var e : staged.entrySet()) {
            if (!e.getKey().startsWith(addon + "/")) continue;
            String rel = e.getKey().substring(addon.length() + 1);
            if (!isEnchantFile(rel)) continue;
            String[] p = rel.split("/");
            String k = p[1] + ":" + p[3].substring(0, p[3].length() - 5);
            if (e.getValue() == null) keys.remove(k); else keys.add(k);
        }
        return keys;
    }

    private static String enchantRel(String key) {
        int c = key.indexOf(':');
        if (c < 1) throw new IllegalArgumentException("Schluessel muss wie namespace:name aussehen.");
        String ns = key.substring(0, c), id = key.substring(c + 1);
        if (!ID.matcher(ns).matches() || !ID.matcher(id).matches()) throw new IllegalArgumentException("Ungueltiger Schluessel: " + key + " (erlaubt: a-z 0-9 _ . -)");
        return "data/" + ns + "/enchantment/" + id + ".json";
    }

    public JsonObject enchant(String addon, String key) throws IOException {
        byte[] b = read(checkId(addon), enchantRel(key));
        if (b == null) return null;
        try {
            return GSON.fromJson(new String(b, StandardCharsets.UTF_8), JsonObject.class);
        } catch (RuntimeException e) {
            return null; // kaputte Datei: in der Oberflaeche als "unlesbar" zeigen
        }
    }

    public boolean isStaged(String addon, String key) {
        return staged.containsKey(addon + "/" + enchantRel(key)) || TAGS.stream().anyMatch(t -> staged.containsKey(addon + "/" + tagRel(t)));
    }

    /** Anzeigename aus dem Feld description (Text oder Text-Komponente). */
    public static String text(JsonElement d) {
        if (d == null || d.isJsonNull()) return "";
        if (d.isJsonPrimitive()) return d.getAsString();
        if (d.isJsonObject()) {
            JsonObject o = d.getAsJsonObject();
            if (o.has("text")) return o.get("text").getAsString();
            if (o.has("translate")) return o.get("translate").getAsString();
        }
        return "";
    }

    // ---- Verzauberungen aendern (Entwurf) ----

    /** Prueft die Pflichtfelder, damit Minecraft die Datei beim Start nicht ablehnt (ein Fehler bricht dort das Laden des ganzen Datapacks ab). */
    public static void validate(JsonObject o) {
        if (o == null) throw new IllegalArgumentException("Leere Verzauberung.");
        if (!o.has("description") || text(o.get("description")).isBlank() && !o.get("description").isJsonObject()) throw new IllegalArgumentException("Name (description) fehlt.");
        intIn(o, "weight", 1, 1024);
        intIn(o, "max_level", 1, 255);
        intIn(o, "anvil_cost", 0, 1000);
        for (String c : List.of("min_cost", "max_cost")) {
            if (!o.has(c) || !o.get(c).isJsonObject()) throw new IllegalArgumentException(c + " fehlt.");
            JsonObject cost = o.getAsJsonObject(c);
            intIn(cost, "base", -10_000, 10_000);
            intIn(cost, "per_level_above_first", -10_000, 10_000);
        }
        if (!o.has("supported_items") || !o.get("supported_items").isJsonPrimitive() && !o.get("supported_items").isJsonArray()) throw new IllegalArgumentException("supported_items fehlt (z. B. #minecraft:enchantable/weapon).");
        if (o.get("supported_items").isJsonPrimitive() && !o.get("supported_items").getAsString().matches("#?([a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw new IllegalArgumentException("supported_items ist ungueltig.");
        if (!o.has("slots") || !o.get("slots").isJsonArray() || o.getAsJsonArray("slots").isEmpty()) throw new IllegalArgumentException("slots fehlt (z. B. mainhand).");
        if (o.has("effects") && !o.get("effects").isJsonObject()) throw new IllegalArgumentException("effects muss ein Objekt sein.");
    }

    private static void intIn(JsonObject o, String f, int min, int max) {
        try {
            if (!o.has(f) || !o.get(f).isJsonPrimitive()) throw new IllegalArgumentException(f + " fehlt.");
            double d = o.get(f).getAsDouble();
            if (d != Math.rint(d)) throw new IllegalArgumentException(f + " muss eine ganze Zahl sein.");
            if (d < min || d > max) throw new IllegalArgumentException(f + " muss zwischen " + min + " und " + max + " liegen.");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(f + " ist keine Zahl.");
        }
    }

    public void stage(String addon, String key, JsonObject json) throws IOException {
        addon = checkId(addon);
        validate(json);
        if (!Files.isDirectory(datapacks.resolve(addon))) {
            if (!addon.equals(OWN_ADDON)) throw new IllegalArgumentException("Das Addon gibt es nicht: " + addon);
            createOwnAddon();
        }
        staged.put(addon + "/" + enchantRel(key), GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    public void stageDelete(String addon, String key) throws IOException {
        addon = checkId(addon);
        String rel = enchantRel(key);
        if (read(addon, rel) == null) throw new IllegalArgumentException("Die Verzauberung gibt es nicht.");
        staged.put(addon + "/" + rel, null);
    }

    private void createOwnAddon() throws IOException {
        // pack.mcmeta von einem vorhandenen Addon uebernehmen, damit die Pack-Version zum Server passt
        String mcmeta = null;
        for (Addon a : addons()) {
            Path m = datapacks.resolve(a.id()).resolve(a.enabled() ? "pack.mcmeta" : "pack.mcmeta.off");
            if (!a.zip() && Files.isRegularFile(m)) {
                JsonObject o = GSON.fromJson(Files.readString(m, StandardCharsets.UTF_8), JsonObject.class);
                o.getAsJsonObject("pack").addProperty("description", "Eigene Verzauberungen (PeakEnchants)");
                mcmeta = GSON.toJson(o);
                break;
            }
        }
        if (mcmeta == null) throw new IllegalArgumentException("Zuerst ein Datapack mit Verzauberungen hochladen (daraus wird die Pack-Version uebernommen).");
        Path dir = datapacks.resolve(OWN_ADDON);
        Files.createDirectories(dir.resolve("data").resolve(OWN_ADDON).resolve("enchantment"));
        Files.writeString(dir.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
    }

    // ---- Tags ----

    private static String tagRel(String tag) { return "data/minecraft/tags/enchantment/" + tag + ".json"; }

    /** In welchen der bekannten Tags steht die Verzauberung (in diesem Addon)? */
    public Set<String> tagsOf(String addon, String key) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String t : TAGS) if (tagValues(addon, t).contains(key)) out.add(t);
        return out;
    }

    private List<String> tagValues(String addon, String tag) throws IOException {
        byte[] b = read(addon, tagRel(tag));
        List<String> v = new ArrayList<>();
        if (b == null) return v;
        try {
            JsonObject o = GSON.fromJson(new String(b, StandardCharsets.UTF_8), JsonObject.class);
            if (o != null && o.has("values")) for (JsonElement e : o.getAsJsonArray("values")) v.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("id").getAsString());
        } catch (RuntimeException ignored) {
            // kaputte Tag-Datei wird beim Speichern ersetzt
        }
        return v;
    }

    public void stageTags(String addon, String key, Set<String> wanted) throws IOException {
        addon = checkId(addon);
        enchantRel(key);
        for (String t : wanted) if (!TAGS.contains(t)) throw new IllegalArgumentException("Unbekannter Tag: " + t);
        for (String t : TAGS) {
            List<String> v = tagValues(addon, t);
            boolean has = v.contains(key), want = wanted.contains(t);
            if (has == want) continue;
            if (want) v.add(key); else v.remove(key);
            JsonObject o = new JsonObject();
            o.addProperty("replace", false);
            JsonArray a = new JsonArray();
            v.forEach(a::add);
            o.add("values", a);
            staged.put(addon + "/" + tagRel(t), GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---- Entwurf ----

    /** Schreibt den Entwurf (alte Dateien kommen vorher in backups/). Gibt die Zahl der Dateien zurueck. */
    public int publish() throws IOException {
        if (staged.isEmpty()) throw new IllegalArgumentException("Es gibt keinen Entwurf.");
        Path stampDir = backups.resolve(stamp() + "-edit");
        int n = 0;
        for (var e : staged.entrySet()) {
            Path target = datapacks.resolve(e.getKey()).normalize();
            if (!target.startsWith(datapacks)) continue;
            if (Files.isRegularFile(target)) {
                Path b = stampDir.resolve(e.getKey());
                Files.createDirectories(b.getParent());
                Files.copy(target, b, StandardCopyOption.REPLACE_EXISTING);
            }
            if (e.getValue() == null) Files.deleteIfExists(target);
            else {
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.write(tmp, e.getValue());
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            n++;
        }
        staged.clear();
        restartNeeded = true;
        return n;
    }

    public void discard() { staged.clear(); }

    /** Fuer die Oberflaeche: alle Verzauberungen aller Addons als geordnete Liste. */
    public Map<String, Map<String, JsonObject>> all() throws IOException {
        Map<String, Map<String, JsonObject>> out = new TreeMap<>();
        for (Addon a : addons()) {
            if (a.zip()) continue;
            Map<String, JsonObject> m = new HashMap<>();
            for (String k : enchantKeys(a.id())) m.put(k, enchant(a.id(), k));
            out.put(a.id(), m);
        }
        return out;
    }
}
