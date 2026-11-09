package de.peakverse.peakadmin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Welt-Sicherungen als ZIP: anlegen, auflisten, loeschen, alte aufraeumen. Kein Bukkit, damit es testbar ist. */
public final class BackupStore {
    /** Eine vorhandene Sicherung. */
    public record Backup(String name, long size, long time) {}

    /** Ergebnis einer Sicherung: Datei und Zahl der uebersprungenen (gesperrten) Dateien. */
    public record Result(Backup backup, int skipped) {}

    private static final Pattern NAME = Pattern.compile("backup-\\d{8}-\\d{6}(-[a-z0-9_]{1,20})?\\.zip");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Path folder;

    public BackupStore(Path folder) {
        this.folder = folder.toAbsolutePath().normalize();
    }

    public Path folder() {
        return folder;
    }

    /** Packt den Ordner {@code source} in eine neue ZIP-Datei; {@code session.lock} und nicht lesbare Dateien werden uebersprungen. */
    public Result create(Path source, String label) throws IOException {
        Files.createDirectories(folder);
        Path src = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(src)) throw new IOException("Welt-Ordner nicht gefunden: " + src);
        if (folder.startsWith(src)) throw new IOException("Der Sicherungs-Ordner liegt in der Welt (wuerde sich selbst sichern).");
        String suffix = label == null || label.isBlank() ? "" : "-" + label.toLowerCase().replaceAll("[^a-z0-9_]", "").replaceAll("^(.{20}).*$", "$1");
        if (suffix.equals("-")) suffix = "";
        String name = "backup-" + LocalDateTime.now().format(STAMP) + suffix + ".zip";
        Path tmp = folder.resolve(name + ".part");
        int[] skipped = {0};
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out); Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p) || p.getFileName().toString().equals("session.lock")) continue;
                String entry = src.relativize(p).toString().replace('\\', '/');
                try {
                    byte[] data = Files.readAllBytes(p);
                    zip.putNextEntry(new ZipEntry(entry));
                    zip.write(data);
                    zip.closeEntry();
                } catch (IOException e) {
                    skipped[0]++;
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Path done = folder.resolve(name);
        Files.move(tmp, done);
        return new Result(new Backup(name, Files.size(done), Files.getLastModifiedTime(done).toMillis()), skipped[0]);
    }

    public List<Backup> list() throws IOException {
        List<Backup> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (NAME.matcher(n).matches() && Files.isRegularFile(p)) out.add(new Backup(n, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
            }
        }
        out.sort(Comparator.comparingLong(Backup::time).reversed());
        return out;
    }

    public Path file(String name) {
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Ungueltiger Name");
        Path p = folder.resolve(name).normalize();
        if (!p.startsWith(folder)) throw new IllegalArgumentException("Unsicherer Pfad");
        return p;
    }

    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(file(name));
    }

    /** Behaelt die neuesten {@code keep} Sicherungen, loescht den Rest; liefert die Zahl der geloeschten. */
    public int prune(int keep) throws IOException {
        if (keep <= 0) return 0;
        List<Backup> all = list();
        int removed = 0;
        for (int i = keep; i < all.size(); i++) if (delete(all.get(i).name())) removed++;
        return removed;
    }
}