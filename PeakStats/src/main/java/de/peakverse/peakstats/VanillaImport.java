package de.peakverse.peakstats;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Liest die eingebauten Minecraft-Statistiken eines Spielers einmalig als Startwerte (nur auf dem Haupt-Thread aufrufen). */
final class VanillaImport {
    private VanillaImport() {}

    static Map<String, Long> read(Player p) {
        Map<String, Long> m = new HashMap<>();
        put(m, StatKeys.PLAY, safe(p, Statistic.PLAY_ONE_MINUTE) * 50L);   // Ticks -> ms
        put(m, StatKeys.DEATHS, safe(p, Statistic.DEATHS));
        put(m, StatKeys.KILLS_MOB, safe(p, Statistic.MOB_KILLS));
        put(m, StatKeys.KILLS_PLAYER, safe(p, Statistic.PLAYER_KILLS));
        put(m, StatKeys.DMG_DEALT, safe(p, Statistic.DAMAGE_DEALT));   // Vanilla zaehlt Schaden in Zehntel-Lebenspunkten, wie hp10
        put(m, StatKeys.DMG_TAKEN, safe(p, Statistic.DAMAGE_TAKEN));
        long dist = 0;
        for (Statistic s : new Statistic[] {Statistic.WALK_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.CROUCH_ONE_CM, Statistic.SWIM_ONE_CM,
                Statistic.FLY_ONE_CM, Statistic.AVIATE_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.HORSE_ONE_CM,
                Statistic.PIG_ONE_CM, Statistic.STRIDER_ONE_CM, Statistic.CLIMB_ONE_CM}) dist += safe(p, s);
        put(m, StatKeys.DISTANCE, dist);
        long mined = 0, ores = 0, crafted = 0, placed = 0;
        for (Material mat : Material.values()) {
            if (mat.isLegacy()) continue;
            if (mat.isBlock()) {
                long n = safe(p, Statistic.MINE_BLOCK, mat);
                if (n > 0) {
                    mined += n;
                    String name = mat.getKey().getKey();
                    m.put("mine:" + name, n);
                    if (StatKeys.isOre(name)) ores += n;
                }
            }
            if (mat.isItem()) {
                crafted += safe(p, Statistic.CRAFT_ITEM, mat);
                if (mat.isBlock()) placed += safe(p, Statistic.USE_ITEM, mat);
            }
        }
        put(m, StatKeys.MINED, mined);
        put(m, StatKeys.ORES, ores);
        put(m, StatKeys.CRAFTED, crafted);
        put(m, StatKeys.PLACED, placed);
        for (EntityType t : EntityType.values()) {
            if (!t.isAlive() || t == EntityType.PLAYER) continue;
            long n = safe(p, Statistic.KILL_ENTITY, t);
            if (n > 0) m.put("mob:" + t.getKey().getKey(), n);
        }
        return m;
    }

    private static void put(Map<String, Long> m, String key, long v) {
        if (v > 0) m.put(key, v);
    }

    private static long safe(Player p, Statistic s) {
        try {
            return p.getStatistic(s);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static long safe(Player p, Statistic s, Material m) {
        try {
            return p.getStatistic(s, m);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static long safe(Player p, Statistic s, EntityType t) {
        try {
            return p.getStatistic(s, t);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
