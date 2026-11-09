package de.peakverse.peakadmin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stummschaltungen (Chat) mit Grund und Ablauf; gespeichert in {@code mutes.json}. */
public final class MuteStore {
    /** Eine Stummschaltung; {@code until == 0} bedeutet dauerhaft. */
    public static final class Mute {
        public String name;
        public String reason;
        public String admin;
        public long time;
        public long until;

        boolean active(long now) {
            return until == 0 || until > now;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, Mute> mutes = new LinkedHashMap<>();

    public MuteStore(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file)) {
                Map<String, Mute> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<Map<String, Mute>>() {}.getType());
                if (m != null) mutes.putAll(m);
            }
        } catch (IOException | RuntimeException e) {
            // beschaedigte Datei: leer starten
        }
    }

    public synchronized void mute(UUID id, String name, String admin, String reason, long hours) throws IOException {
        Mute m = new Mute();
        m.name = name;
        m.admin = admin;
        m.reason = reason == null ? "" : reason;
        m.time = System.currentTimeMillis();
        m.until = hours <= 0 ? 0 : m.time + hours * 3_600_000L;
        mutes.put(id.toString(), m);
        save();
    }

    public synchronized boolean unmute(UUID id) throws IOException {
        boolean had = mutes.remove(id.toString()) != null;
        if (had) save();
        return had;
    }

    public synchronized Mute active(UUID id) {
        Mute m = mutes.get(id.toString());
        return m != null && m.active(System.currentTimeMillis()) ? m : null;
    }

    public synchronized Map<String, Mute> activeAll() {
        Map<String, Mute> out = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (var e : mutes.entrySet()) if (e.getValue().active(now)) out.put(e.getKey(), e.getValue());
        return out;
    }

    private void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(mutes), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}