package de.peakverse.peakadmin;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Liest neue Zeilen am Ende einer Log-Datei (logs/latest.log) fuer die Live-Konsole. */
public final class LogTail {
    /** Neue Zeilen und die Dateiposition, ab der beim naechsten Mal weitergelesen wird. */
    public record Chunk(long position, List<String> lines, boolean reset) {}

    private static final long MAX_READ = 256 * 1024;
    private final Path file;

    public LogTail(Path file) {
        this.file = file;
    }

    /** {@code from < 0}: die letzten Zeilen (ca. 64 KB); sonst alles ab dieser Position. Ist die Datei kleiner geworden (neues Log), beginnt es von vorn. */
    public Chunk read(long from) throws IOException {
        if (!Files.isRegularFile(file)) return new Chunk(0, List.of(), false);
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long len = f.length();
            boolean reset = false;
            long start = from;
            if (from < 0) start = Math.max(0, len - 64 * 1024);
            else if (from > len) {
                start = 0;
                reset = true;
            }
            if (len - start > MAX_READ) start = len - MAX_READ;
            f.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            f.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(text.split("\r?\n")));
            if (start > 0 && !lines.isEmpty() && (from < 0 || len - from > MAX_READ)) lines.remove(0); // angeschnittene erste Zeile
            if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
            return new Chunk(len, lines, reset);
        }
    }
}