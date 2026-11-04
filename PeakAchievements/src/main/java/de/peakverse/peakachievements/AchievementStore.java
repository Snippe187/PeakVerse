package de.peakverse.peakachievements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Datenbank des Owners: {@code achievements.json} (eigene Achievements) und {@code vanilla.json} (Einstellungen zu Minecraft-Fortschritten).
 * Geschrieben wird atomar. Eine kaputte Datei wird als {@code .kaputt} aufgehoben, damit der Server trotzdem startet. Nicht thread-sicher.
 */
public final class AchievementStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<List<Achievement>>() {}.getType();
    private static final Type MAP = new TypeToken<LinkedHashMap<String, VanillaOverride>>() {}.getType();

    private final Path folder;
    private final Map<String, Achievement> custom = new LinkedHashMap<>();
    private final Map<String, VanillaOverride> vanilla = new LinkedHashMap<>();

    public AchievementStore(Path folder) {
        this.folder = folder;
    }

    private Path customFile() { return folder.resolve("achievements.json"); }
    private Path vanillaFile() { return folder.resolve("vanilla.json"); }

    public void load() throws IOException {
        custom.clear();
        vanilla.clear();
        List<Achievement> list = read(customFile(), LIST);
        if (list != null) {
            for (Achievement a : list) {
                if (a == null || a.id == null || !a.check().isEmpty()) continue;
                if (a.rewards == null) a.rewards = new Achievement.Rewards();
                if (a.visibility == null) a.visibility = Achievement.Visibility.AFTER_PARENT;
                custom.put(a.id, a);
            }
        }
        Map<String, VanillaOverride> map = read(vanillaFile(), MAP);
        if (map != null) for (var e : map.entrySet()) if (e.getKey() != null && e.getValue() != null) vanilla.put(e.getKey(), e.getValue());
    }

    private <T> T read(Path file, Type type) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (RuntimeException e) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".kaputt"), StandardCopyOption.REPLACE_EXISTING);
            return null;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(folder);
        write(customFile(), GSON.toJson(new ArrayList<>(custom.values()), LIST));
        write(vanillaFile(), GSON.toJson(vanilla, MAP));
    }

    private static void write(Path file, String json) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- eigene Achievements ----

    public List<Achievement> all() {
        return custom.values().stream().sorted(Comparator.comparing(a -> a.id)).toList();
    }

    public Achievement get(String id) {
        return custom.get(id);
    }

    /** Legt an oder ersetzt (gleiche Kennung). @throws IllegalArgumentException bei Fehlern oder wenn die Kette einen Kreis bildet */
    public void put(Achievement a) throws IOException {
        List<String> errors = a.check();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        if (a.parent == null) a.parent = "";
        if (a.visibility == null) a.visibility = Achievement.Visibility.AFTER_PARENT;
        Achievement old = custom.put(a.id, a);
        if (hasCycle(a.id)) {
            if (old == null) custom.remove(a.id);
            else custom.put(a.id, old);
            throw new IllegalArgumentException("Die Eltern-Kette von '" + a.id + "' bildet einen Kreis.");
        }
        save();
    }

    public Achievement remove(String id) throws IOException {
        Achievement a = custom.remove(id);
        if (a == null) throw new IllegalArgumentException("Kein Achievement '" + id + "'.");
        // Kinder haengen danach wieder an der Wurzel
        for (Achievement c : custom.values()) if (id.equals(c.parent)) c.parent = "";
        save();
        return a;
    }

    public void setEnabled(String id, boolean enabled) throws IOException {
        Achievement a = custom.get(id);
        if (a == null) throw new IllegalArgumentException("Kein Achievement '" + id + "'.");
        a.enabled = enabled;
        save();
    }

    private boolean hasCycle(String start) {
        String cur = custom.get(start).parent;
        int guard = 0;
        while (cur != null && !cur.isEmpty() && custom.containsKey(cur)) {
            if (cur.equals(start) || ++guard > custom.size()) return true;
            cur = custom.get(cur).parent;
        }
        return false;
    }

    // ---- Minecraft-Fortschritte ----

    /** Einstellungen zu einem Minecraft-Fortschritt; ohne Eintrag ein unveraenderter Standard. */
    public VanillaOverride vanilla(String key) {
        VanillaOverride o = vanilla.get(key);
        return o == null ? new VanillaOverride() : o;
    }

    public Map<String, VanillaOverride> vanillaAll() {
        return Map.copyOf(vanilla);
    }

    public void putVanilla(String key, VanillaOverride o) throws IOException {
        if (key == null || !key.startsWith("minecraft:")) throw new IllegalArgumentException("Schluessel muss mit minecraft: beginnen.");
        vanilla.put(key, o);
        save();
    }

    public void resetVanilla(String key) throws IOException {
        vanilla.remove(key);
        save();
    }

    public boolean vanillaEnabled(String key) {
        return vanilla(key).enabled;
    }
}
