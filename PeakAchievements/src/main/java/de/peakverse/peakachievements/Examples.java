package de.peakverse.peakachievements;

import java.util.ArrayList;
import java.util.List;

/** Beispiele fuer einen frischen Server (abschaltbar mit {@code examples: false}); der Owner kann sie aendern oder loeschen. */
public final class Examples {
    private Examples() {}

    private static Achievement make(String id, String title, String desc, String icon, Achievement.Type type, String target, int count, Achievement.Frame frame, String parent, int xp) {
        Achievement a = new Achievement();
        a.id = id;
        a.title = title;
        a.description = desc;
        a.icon = icon;
        a.type = type;
        a.target = target;
        a.count = count;
        a.frame = frame;
        a.parent = parent;
        a.rewards.xp = xp;
        return a;
    }

    public static List<Achievement> all() {
        List<Achievement> l = new ArrayList<>();
        l.add(make("zombie_jaeger", "Zombie-Jaeger", "Besiege 10 Zombies.", "ROTTEN_FLESH", Achievement.Type.KILL, "ZOMBIE", 10, Achievement.Frame.TASK, "", 20));
        l.add(make("zombie_schreck", "Schreck der Untoten", "Besiege 100 Zombies.", "ZOMBIE_HEAD", Achievement.Type.KILL, "ZOMBIE", 100, Achievement.Frame.GOAL, "zombie_jaeger", 100));
        l.add(make("steinmetz", "Steinmetz", "Baue 200 Stein ab.", "STONE_PICKAXE", Achievement.Type.BREAK, "STONE", 200, Achievement.Frame.TASK, "", 20));
        l.add(make("bergarbeiter", "Bergarbeiter", "Baue 50 Diamanterz ab.", "DIAMOND_PICKAXE", Achievement.Type.BREAK, "DIAMOND_ORE", 50, Achievement.Frame.CHALLENGE, "steinmetz", 200));
        l.add(make("angler", "Petri Heil", "Fange 10 Fische.", "FISHING_ROD", Achievement.Type.FISH, "", 10, Achievement.Frame.TASK, "", 20));
        l.add(make("springer", "Hoch hinaus", "Springe 100 Mal.", "RABBIT_FOOT", Achievement.Type.JUMP, "", 100, Achievement.Frame.TASK, "", 10));
        l.add(make("wanderer", "Weitgereist", "Laufe 1000 Bloecke weit.", "LEATHER_BOOTS", Achievement.Type.WALK, "", 1000, Achievement.Frame.TASK, "", 30));
        l.add(make("stammgast", "Stammgast", "Spiele eine Stunde auf diesem Server.", "CLOCK", Achievement.Type.PLAYTIME, "", 60, Achievement.Frame.GOAL, "", 50));
        return l;
    }
}
