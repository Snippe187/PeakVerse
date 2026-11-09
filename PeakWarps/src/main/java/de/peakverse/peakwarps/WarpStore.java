package de.peakverse.peakwarps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Die Warp-Punkte (Name, Ort, Symbol, Beschreibung, Zugriff); gespeichert in {@code warps.json}. Kein Bukkit, damit es testbar ist. */
public final class WarpStore {
    /** Ein Warp-Punkt. {@code restricted}: nur mit dem Recht {@code peakwarps.warp.<name>}. */
    public static final class Warp {
        public String name;
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String icon = "ender_pearl";
        public String description = "";
        public boolean restricted;
        public boolean onMap = true;
        public String creator = "";
        public long created;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,24}");
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9_./-]{1,64}");
    private static final Pattern ICON = Pattern.compile("[a-z0-9_]{1,48}");
    private final Path file;
    private final Map<String, Warp> warps = new LinkedHashMap<>();

    public WarpStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
        try {
            if (Files.isRegularFile(this.file)) {
                List<Warp> list = GSON.fromJson(Files.readString(this.file, StandardCharsets.UTF_8), new TypeToken<List<Warp>>() {}.getType());
                if (list != null) for (Warp w : list) if (w != null && w.name != null && NAME.matcher(w.name).matches()) warps.put(w.name, w);
            }
        } catch (IOException | RuntimeException e) {
            // beschaedigte Datei: leer starten (die Datei bleibt bis zum naechsten Speichern erhalten)
        }
    }

    /** Text fuer HTML-Tooltips der Karte unschaedlich machen. */
    public static String html(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public synchronized List<Warp> all() {
        return new ArrayList<>(warps.values());
    }

    public synchronized Warp get(String name) {
        return name == null ? null : warps.get(name.toLowerCase());
    }

    /** Legt einen Warp an oder ersetzt ihn (Name unveraendert); prueft alle Felder. */
    public synchronized Warp save(Warp in, String admin) throws IOException {
        if (in == null || !validName(in.name)) throw new IllegalArgumentException("Name: 1-24 Zeichen, a-z, 0-9, _ und -");
        if (in.world == null || !WORLD.matcher(in.world).matches()) throw new IllegalArgumentException("Ungueltige Welt");
        if (!Double.isFinite(in.x) || !Double.isFinite(in.y) || !Double.isFinite(in.z) || Math.abs(in.x) > 30_000_000 || Math.abs(in.z) > 30_000_000 || in.y < -2048 || in.y > 4096) {
            throw new IllegalArgumentException("Ungueltige Koordinaten");
        }
        if (in.icon == null || !ICON.matcher(in.icon).matches()) in.icon = "ender_pearl";
        if (in.description == null) in.description = "";
        if (in.description.length() > 200) in.description = in.description.substring(0, 200);
        in.description = in.description.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        Warp old = warps.get(in.name);
        in.creator = old != null ? old.creator : (admin == null ? "" : admin);
        in.created = old != null ? old.created : System.currentTimeMillis();
        warps.put(in.name, in);
        write();
        return in;
    }

    public synchronized boolean delete(String name) throws IOException {
        boolean had = warps.remove(name) != null;
        if (had) write();
        return had;
    }

    private void write() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(new ArrayList<>(warps.values())), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}