package de.peakverse.peakadmin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Die letzten Chat-Nachrichten (Ringspeicher) mit laufender Nummer, damit die Web-Seite nur Neues holt. */
public final class ChatLog {
    /** Eine Chat-Zeile; {@code player} ist leer bei Server- oder Web-Nachrichten. */
    public record Entry(long seq, long time, String player, String text) {}

    private final int max;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long next = 1;

    public ChatLog(int max) {
        this.max = Math.max(1, max);
    }

    public synchronized Entry add(String player, String text) {
        Entry e = new Entry(next++, System.currentTimeMillis(), player == null ? "" : player, text == null ? "" : text);
        if (entries.size() >= max) entries.removeFirst();
        entries.addLast(e);
        return e;
    }

    /** Alle Eintraege mit einer Nummer groesser als {@code afterSeq}. */
    public synchronized List<Entry> since(long afterSeq) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.seq() > afterSeq) out.add(e);
        return out;
    }

    public synchronized long lastSeq() {
        return next - 1;
    }
}