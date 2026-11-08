package de.peakverse.peakachievements;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import io.papermc.paper.advancement.AdvancementDisplay;

/**
 * Spiegelt die Minecraft-Fortschritte in den Reiter "PeakVerse": jede Kopie ist ein stiller Fortschritt ({@code minecraft:impossible}, kein Toast, keine Chat-Meldung),
 * der erreicht ist, sobald der Spieler das Original erreicht hat. Texte bleiben uebersetzbar (Minecraft uebersetzt sie auf dem Client).
 */
public final class VanillaMirror {
    private static final String PREFIX = "mc/";

    private VanillaMirror() {}

    /** Schluessel der Kopie zu einem Minecraft-Fortschritt, z. B. {@code peakachievements:mc/story/mine_stone}. */
    public static NamespacedKey mirrorKey(NamespacedKey vanilla) {
        return new NamespacedKey(AdvancementJson.NAMESPACE, PREFIX + vanilla.getKey());
    }

    /** Alle Minecraft-Fortschritte mit Anzeige (ohne Rezepte), Eltern vor Kindern. */
    public static List<Advancement> collect() {
        List<Advancement> out = new ArrayList<>();
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement a = it.next();
            NamespacedKey k = a.getKey();
            if (!k.getNamespace().equals(NamespacedKey.MINECRAFT) || k.getKey().startsWith("recipes/")) continue;
            if (a.getDisplay() == null) continue;
            out.add(a);
        }
        out.sort(Comparator.comparingInt(VanillaMirror::depth).thenComparing(a -> a.getKey().toString()));
        return out;
    }

    static int depth(Advancement a) {
        int d = 0;
        for (Advancement p = a.getParent(); p != null; p = p.getParent()) d++;
        return d;
    }

    /** JSON der Kopie. Eintraege aus {@code vanilla.json} (Text, Symbol) haben Vorrang vor Minecraft. */
    public static String json(Advancement adv, String parentKey, VanillaOverride o) {
        AdvancementDisplay d = adv.getDisplay();
        JsonObject display = new JsonObject();
        String icon = o != null && o.icon != null && !o.icon.isBlank() ? "minecraft:" + o.icon.toLowerCase(Locale.ROOT) : d.icon().getType().getKey().toString();
        JsonObject ic = new JsonObject();
        ic.addProperty("id", icon);
        display.add("icon", ic);
        display.add("title", o != null && o.title != null ? plain(o.title) : component(d.title()));
        display.add("description", o != null && o.description != null ? plain(o.description) : component(d.description()));
        display.addProperty("frame", d.frame().name().toLowerCase(Locale.ROOT));
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        boolean hidden = o != null && o.visibility == Achievement.Visibility.UNTIL_DONE || d.isHidden();
        display.addProperty("hidden", hidden);
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add(AdvancementJson.CRITERION, trigger);
        JsonObject root = new JsonObject();
        root.add("display", display);
        root.add("criteria", criteria);
        root.addProperty("parent", parentKey);
        return root.toString();
    }

    private static JsonObject plain(String s) {
        JsonObject t = new JsonObject();
        t.addProperty("text", s);
        return t;
    }

    private static com.google.gson.JsonElement component(Component c) {
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(c));
    }
}
