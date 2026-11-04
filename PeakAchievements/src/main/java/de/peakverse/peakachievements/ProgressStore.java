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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fortschritt je Spieler: erreichte eigene Achievements (mit Zeit) und Zaehler. Liegt in {@code progress.json}, nicht im Scoreboard. */
public final class ProgressStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP = new TypeToken<LinkedHashMap<String, PlayerProgress>>() {}.getType();

    public static final class PlayerProgress {
        public Map<String, Long> done = new LinkedHashMap<>();
        public Map<String, Long> counters = new HashMap<>();
    }

    private final Path file;
    private final Map<String, PlayerProgress> players = new LinkedHashMap<>();
    private boolean dirty;

    public ProgressStore(Path folder) {
        this.file = folder.resolve("progress.json");
    }

    public void load() throws IOException {
        players.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String, PlayerProgress> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
            if (m != null) for (var e : m.entrySet()) {
                PlayerProgress p = e.getValue();
                if (p == null) continue;
                if (p.done == null) p.done = new LinkedHashMap<>();
                if (p.counters == null) p.counters = new HashMap<>();
                players.put(e.getKey(), p);
            }
        } catch (RuntimeException e) {
            Files.move(file, file.resolveSibling("progress.json.kaputt"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Schreibt nur, wenn sich etwas geaendert hat. */
    public void saveIfDirty() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("progress.json.tmp");
        Files.writeString(tmp, GSON.toJson(players, MAP), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        dirty = false;
    }

    private PlayerProgress of(UUID id) {
        return players.computeIfAbsent(id.toString(), k -> new PlayerProgress());
    }

    public boolean isDone(UUID player, String achievement) {
        PlayerProgress p = players.get(player.toString());
        return p != null && p.done.containsKey(achievement);
    }

    public Set<String> done(UUID player) {
        PlayerProgress p = players.get(player.toString());
        return p == null ? Set.of() : new HashSet<>(p.done.keySet());
    }

    /** @return {@code true}, wenn es neu erreicht wurde */
    public boolean award(UUID player, String achievement, long now) {
        PlayerProgress p = of(player);
        if (p.done.containsKey(achievement)) return false;
        p.done.put(achievement, now);
        dirty = true;
        return true;
    }

    public boolean revoke(UUID player, String achievement) {
        PlayerProgress p = players.get(player.toString());
        if (p == null || p.done.remove(achievement) == null) return false;
        dirty = true;
        return true;
    }

    public long counter(UUID player, String key) {
        PlayerProgress p = players.get(player.toString());
        return p == null ? 0 : p.counters.getOrDefault(key, 0L);
    }

    /** Erhoeht einen Zaehler und gibt den neuen Wert zurueck. */
    public long add(UUID player, String key, long amount) {
        PlayerProgress p = of(player);
        long v = p.counters.merge(key, amount, Long::sum);
        dirty = true;
        return v;
    }

    /** Wie viele Spieler ein Achievement haben (fuer die Seltenheit in Prozent) und wie viele Spieler es gibt. */
    public int holders(String achievement) {
        int n = 0;
        for (PlayerProgress p : players.values()) if (p.done.containsKey(achievement)) n++;
        return n;
    }

    public int playerCount() {
        return players.size();
    }
}
