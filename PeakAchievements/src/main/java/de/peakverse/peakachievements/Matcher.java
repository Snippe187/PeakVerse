package de.peakverse.peakachievements;

import java.util.List;
import java.util.Locale;

/** Reine Logik (ohne Bukkit): welche Achievements ein Ereignis zaehlt und wann eines erreicht ist. */
public final class Matcher {
    private Matcher() {}

    /** Ereignis-Ausloeser, die ueber {@link #matches} gezaehlt werden; Statistik-Ausloeser lesen den Wert direkt. */
    public static boolean isEvent(Achievement.Type t) {
        return t == Achievement.Type.KILL || t == Achievement.Type.BREAK || t == Achievement.Type.PLACE || t == Achievement.Type.CRAFT || t == Achievement.Type.FISH;
    }

    public static boolean isStat(Achievement.Type t) {
        return t == Achievement.Type.JUMP || t == Achievement.Type.WALK || t == Achievement.Type.PLAYTIME || t == Achievement.Type.DEATH;
    }

    /** Leeres Ziel zaehlt alles, sonst muss der Name (ohne Gross/Klein, mit oder ohne {@code minecraft:}) passen. */
    public static boolean matches(Achievement a, Achievement.Type type, String name) {
        if (!a.enabled || a.type != type) return false;
        if (a.target == null || a.target.isBlank() || a.target.equals("*")) return true;
        return norm(a.target).equals(norm(name));
    }

    static String norm(String s) {
        String n = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return n.startsWith("minecraft:") ? n.substring(10) : n;
    }

    public static List<Achievement> forEvent(List<Achievement> all, Achievement.Type type, String name) {
        return all.stream().filter(a -> matches(a, type, name)).toList();
    }

    /**
     * Wandelt einen Rohwert der Minecraft-Statistik in die Einheit des Achievements um: Laufen in Bloecken (Statistik in cm),
     * Spielzeit in Minuten (Statistik in Ticks), Spruenge und Tode unveraendert.
     */
    public static long statValue(Achievement.Type type, long raw) {
        return switch (type) {
            case WALK -> raw / 100;
            case PLAYTIME -> raw / 20 / 60;
            default -> raw;
        };
    }

    public static boolean reached(Achievement a, long value) {
        return value >= a.count;
    }
}
