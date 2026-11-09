package de.peakverse.peakregions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Schematic-Dateien von WorldEdit verwalten (auflisten, hochladen, loeschen). Kein Bukkit, damit es testbar ist. */
public final class SchemStore {
    /** Eine Schematic-Datei. */
    public record Schem(String name, long size, long time) {}

    public static final long MAX_BYTES = 25L << 20;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}\\.(schem|schematic)");
    private final Path folder;

    public SchemStore(Path folder) {
        this.folder = folder.toAbsolutePath().normalize();
    }

    public Path folder() {
        return folder;
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches() && !name.contains("..");
    }

    public List<Schem> list() throws IOException {
        List<Schem> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (validName(n) && Files.isRegularFile(p)) out.add(new Schem(n, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
            }
        }
        out.sort(Comparator.comparing(Schem::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public Path file(String name) {
        if (!validName(name)) throw new IllegalArgumentException("Ungueltiger Dateiname (erlaubt: a-z, 0-9, _ . - und .schem oder .schematic)");
        Path p = folder.resolve(name).normalize();
        if (!p.startsWith(folder)) throw new IllegalArgumentException("Unsicherer Pfad");
        return p;
    }

    public void save(String name, byte[] data) throws IOException {
        Path target = file(name);
        if (data == null || data.length == 0) throw new IllegalArgumentException("Die Datei ist leer.");
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("Die Datei ist zu gross (max. " + (MAX_BYTES >> 20) + " MB).");
        Files.createDirectories(folder);
        Path tmp = folder.resolve(name + ".part");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(file(name));
    }

    public byte[] read(String name) throws IOException {
        Path p = file(name);
        if (!Files.isRegularFile(p)) throw new IllegalArgumentException("Datei nicht gefunden");
        return Files.readAllBytes(p);
    }
}