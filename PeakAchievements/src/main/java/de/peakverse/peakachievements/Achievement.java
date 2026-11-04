package de.peakverse.peakachievements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Ein eigenes Achievement. Alle Felder sind oeffentlich, damit Gson die Datei {@code achievements.json} direkt lesen und schreiben kann. */
public final class Achievement {
    public enum Frame { TASK, GOAL, CHALLENGE }

    /** Was gezaehlt wird. {@code target} nennt den Mob-, Block- oder Item-Typ (leer = jeder). */
    public enum Type { KILL, BREAK, PLACE, CRAFT, FISH, JUMP, WALK, PLAYTIME, DEATH, MANUAL }

    /** ALWAYS = immer sichtbar, AFTER_PARENT = sichtbar, wenn der Elternteil erreicht ist, UNTIL_DONE = verborgen bis erreicht. */
    public enum Visibility { ALWAYS, AFTER_PARENT, UNTIL_DONE }

    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");

    public String id;
    public String title = "";
    public String description = "";
    /** Minecraft-Gegenstand als Symbol, z. B. DIAMOND_SWORD. */
    public String icon = "STONE";
    public Frame frame = Frame.TASK;
    public boolean enabled = true;
    public boolean hidden = false;
    /** Gruppe/Reiter-Ast, frei waehlbar. */
    public String category = "allgemein";
    /** Kennung eines anderen Achievements (eigenes oder {@code minecraft:...}); leer = haengt an der gemeinsamen Wurzel. */
    public String parent = "";
    public int points = 10;
    /** Wann Spieler den Knoten im Baum sehen. */
    public Visibility visibility = Visibility.AFTER_PARENT;
    /** Lage im Skill-Tree-Bildschirm; {@code null} = automatisch unter dem Elternteil. */
    public Integer x;
    public Integer y;
    public Type type = Type.MANUAL;
    public String target = "";
    public int count = 1;
    public Rewards rewards = new Rewards();

    public static final class Rewards {
        public int xp = 0;
        /** Items als {@code MATERIAL:Anzahl}, z. B. {@code DIAMOND:3}. */
        public List<String> items = new ArrayList<>();
        /** Konsolenbefehle mit {@code %player%}; nur ein Voll-Admin darf sie setzen. */
        public List<String> commands = new ArrayList<>();
        /** Bildschirm-Titel; leer = keiner. */
        public String title = "";
        public boolean firework = false;
        public String sound = "";
    }

    /** Fehler in Worten; leer, wenn alles in Ordnung ist. */
    public List<String> check() {
        List<String> e = new ArrayList<>();
        if (id == null || !ID.matcher(id).matches()) e.add("Kennung: nur a-z, 0-9 und _, hoechstens 40 Zeichen.");
        if (title == null || title.isBlank()) e.add("Der Titel fehlt.");
        if (title != null && title.length() > 60) e.add("Titel ist laenger als 60 Zeichen.");
        if (description != null && description.length() > 200) e.add("Beschreibung ist laenger als 200 Zeichen.");
        if (type == null) e.add("Die Art des Ausloesers fehlt.");
        if (count < 1 || count > 1_000_000) e.add("Anzahl muss zwischen 1 und 1000000 liegen.");
        if (points < 0 || points > 10_000) e.add("Punkte muessen zwischen 0 und 10000 liegen.");
        if (frame == null) e.add("Die Rahmenform fehlt.");
        if (parent != null && id != null && parent.equals(id)) e.add("Ein Achievement kann nicht an sich selbst haengen.");
        if (rewards == null) e.add("Belohnungen fehlen.");
        else {
            if (rewards.xp < 0 || rewards.xp > 100_000) e.add("XP muessen zwischen 0 und 100000 liegen.");
            for (String s : rewards.items) {
                String[] p = s.split(":");
                boolean ok = p.length >= 1 && p.length <= 2 && !p[0].isBlank();
                if (ok && p.length == 2) {
                    try {
                        int n = Integer.parseInt(p[1]);
                        ok = n >= 1 && n <= 64 * 36;
                    } catch (NumberFormatException ex) {
                        ok = false;
                    }
                }
                if (!ok) e.add("Item '" + s + "' ist ungueltig (Form MATERIAL oder MATERIAL:Anzahl).");
            }
        }
        return e;
    }

    /** Ein Ausloeser ohne Zaehlen (nur manuell vergeben). */
    public boolean manual() {
        return type == Type.MANUAL;
    }
}
