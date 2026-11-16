package de.peakverse.peakstats;

import java.util.Locale;

/** Werte lesbar machen (Chat): Zeit, Strecke, Schaden, Zahlen. Ohne Bukkit, damit es testbar ist. */
public final class StatFormat {
    private StatFormat() {}

    public static String value(String unit, long v) {
        return switch (unit) {
            case "ms" -> duration(v);
            case "cm" -> v >= 100_000 ? String.format(Locale.GERMANY, "%.1f km", v / 100_000.0) : String.format(Locale.GERMANY, "%.0f m", v / 100.0);
            case "hp10" -> String.format(Locale.GERMANY, "%.1f HP", v / 10.0);
            default -> String.format(Locale.GERMANY, "%,d", v);
        };
    }

    /** Millisekunden als "3 Std 5 Min" (unter einer Minute: Sekunden). */
    public static String duration(long ms) {
        long s = Math.max(0, ms) / 1000;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (d > 0) return d + " T " + h + " Std";
        if (h > 0) return h + " Std " + m + " Min";
        if (m > 0) return m + " Min";
        return s + " Sek";
    }

    /** Kurzname fuer {@code /top}: "spielzeit" -> play_ms usw.; unbekannt = {@code null}. */
    public static String keyFor(String word) {
        if (word == null) return null;
        String w = word.toLowerCase(Locale.ROOT);
        for (StatKeys.Def d : StatKeys.all()) if (d.key().equals(w)) return d.key();
        return switch (w) {
            case "spielzeit", "playtime", "zeit" -> StatKeys.PLAY;
            case "afk" -> StatKeys.AFK;
            case "kills", "mobs" -> StatKeys.KILLS_MOB;
            case "pvp", "spielerkills" -> StatKeys.KILLS_PLAYER;
            case "tode", "deaths" -> StatKeys.DEATHS;
            case "bloecke", "abgebaut", "mined" -> StatKeys.MINED;
            case "erze", "ores" -> StatKeys.ORES;
            case "gesetzt", "placed" -> StatKeys.PLACED;
            case "strecke", "distanz", "distance" -> StatKeys.DISTANCE;
            case "hergestellt", "crafted" -> StatKeys.CRAFTED;
            default -> null;
        };
    }

    /** Zeitraum-Wort -> Schluessel fuer {@link StatsWeb#since}; unbekannt = {@code null}. */
    public static String periodFor(String word) {
        if (word == null) return "total";
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "heute", "today" -> "today";
            case "woche", "week" -> "week";
            case "monat", "month" -> "month";
            case "gesamt", "total", "alle" -> "total";
            default -> null;
        };
    }
}
