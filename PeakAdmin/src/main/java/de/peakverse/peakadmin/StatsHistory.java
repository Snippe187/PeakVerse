package de.peakverse.peakadmin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Verlauf der Server-Werte (TPS, RAM, Spieler ...) als Ringspeicher. */
public final class StatsHistory {
    /** Ein Messpunkt. */
    public record Sample(long time, double tps, double mspt, long ramUsed, long ramMax, int players, int entities, int chunks) {}

    private final int max;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public StatsHistory(int max) {
        this.max = Math.max(1, max);
    }

    public synchronized void add(Sample s) {
        if (samples.size() >= max) samples.removeFirst();
        samples.addLast(s);
    }

    public synchronized List<Sample> snapshot() {
        return new ArrayList<>(samples);
    }
}