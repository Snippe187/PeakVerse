package de.peakverse.peakstats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Katalog der gezaehlten Werte. Schluessel mit Praefix ({@code mob:zombie}, {@code mine:stone}) gehoeren zu einem Hauptwert. */
public final class StatKeys {
    public record Def(String key, String group, String labelDe, String labelEn, String unit) {}

    public static final String PLAY = "play_ms", AFK = "afk_ms", LOGINS = "logins", KILLS_PLAYER = "kills_player", KILLS_MOB = "kills_mob",
            DEATHS = "deaths", DMG_DEALT = "damage_dealt", DMG_TAKEN = "damage_taken", MINED = "blocks_mined", ORES = "ores_mined",
            PLACED = "blocks_placed", DISTANCE = "distance_cm", CRAFTED = "items_crafted";

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        add(PLAY, "time", "Spielzeit (aktiv)", "Play time (active)", "ms");
        add(AFK, "time", "AFK-Zeit", "AFK time", "ms");
        add(LOGINS, "time", "Anmeldungen", "Logins", "n");
        add(KILLS_PLAYER, "combat", "Spieler-Kills", "Player kills", "n");
        add(KILLS_MOB, "combat", "Mob-Kills", "Mob kills", "n");
        add(DEATHS, "combat", "Tode", "Deaths", "n");
        add(DMG_DEALT, "combat", "Schaden ausgeteilt", "Damage dealt", "hp10");
        add(DMG_TAKEN, "combat", "Schaden erhalten", "Damage taken", "hp10");
        add(MINED, "build", "Bloecke abgebaut", "Blocks mined", "n");
        add(ORES, "build", "Erze abgebaut", "Ores mined", "n");
        add(PLACED, "build", "Bloecke gesetzt", "Blocks placed", "n");
        add(DISTANCE, "build", "Strecke", "Distance", "cm");
        add(CRAFTED, "build", "Items hergestellt", "Items crafted", "n");
    }

    private StatKeys() {}

    private static void add(String key, String group, String de, String en, String unit) {
        DEFS.put(key, new Def(key, group, de, en, unit));
    }

    public static List<Def> all() {
        return List.copyOf(DEFS.values());
    }

    public static boolean isMain(String key) {
        return DEFS.containsKey(key);
    }

    /** Hauptwert, dessen Sichtbarkeit fuer den Schluessel gilt ({@code mob:x} gehoert zu {@code kills_mob}); {@code null}, wenn unbekannt. */
    public static String parentOf(String key) {
        if (key == null) return null;
        if (DEFS.containsKey(key)) return key;
        if (key.startsWith("mob:")) return KILLS_MOB;
        if (key.startsWith("mine:")) return MINED;
        return null;
    }

    /** Erlaubte Schluessel fuer Eingaben: Hauptwerte oder {@code mob:}/{@code mine:} mit einfachem Namen. */
    public static boolean valid(String key) {
        if (key == null) return false;
        if (DEFS.containsKey(key)) return true;
        return key.matches("(mob|mine):[a-z0-9_]{1,64}");
    }

    public static boolean isOre(String materialName) {
        String n = materialName.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith("_ore") || n.equals("ancient_debris");
    }
}
