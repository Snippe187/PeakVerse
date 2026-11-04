package de.peakverse.peakachievements;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Baut die JSON-Dateien der echten Minecraft-Fortschritte. Ausloeser ist immer {@code minecraft:impossible}: vergeben wird nur durch PeakAchievements. */
public final class AdvancementJson {
    public static final String NAMESPACE = "peakachievements";
    public static final String ROOT = "root";
    public static final String CRITERION = "done";

    private AdvancementJson() {}

    /** Schluessel des Fortschritts zu einem eigenen Achievement. */
    public static String key(String id) {
        return NAMESPACE + ":" + id;
    }

    /** Die Wurzel: sie bildet den Reiter "PeakVerse" mit Hintergrund. */
    public static String root(String title, String description) {
        JsonObject o = base("NETHER_STAR", title, description, "task", false, false);
        o.getAsJsonObject("display").addProperty("background", "minecraft:gui/advancements/backgrounds/stone");
        return o.toString();
    }

    /** @param parentKey Schluessel des Elternteils, z. B. {@code peakachievements:root} */
    public static String of(Achievement a, String parentKey, boolean announce) {
        boolean hidden = a.visibility == Achievement.Visibility.UNTIL_DONE || a.hidden;
        JsonObject o = base(a.icon, a.title, a.description == null ? "" : a.description, a.frame.name().toLowerCase(Locale.ROOT), true, announce);
        o.getAsJsonObject("display").addProperty("hidden", hidden);
        o.addProperty("parent", parentKey);
        return o.toString();
    }

    private static JsonObject base(String icon, String title, String description, String frame, boolean toast, boolean announce) {
        JsonObject display = new JsonObject();
        JsonObject ic = new JsonObject();
        ic.addProperty("id", "minecraft:" + (icon == null || icon.isBlank() ? "stone" : icon.toLowerCase(Locale.ROOT)));
        display.add("icon", ic);
        display.add("title", text(title));
        display.add("description", text(description));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", announce);
        display.addProperty("hidden", false);
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add(CRITERION, trigger);
        JsonObject o = new JsonObject();
        o.add("display", display);
        o.add("criteria", criteria);
        return o;
    }

    private static JsonObject text(String s) {
        JsonObject t = new JsonObject();
        t.addProperty("text", s == null ? "" : s);
        return t;
    }
}
