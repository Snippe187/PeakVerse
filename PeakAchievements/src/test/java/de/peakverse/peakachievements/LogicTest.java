package de.peakverse.peakachievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicTest {
    private static Achievement kill(String target) {
        Achievement a = new Achievement();
        a.id = "k";
        a.title = "K";
        a.type = Achievement.Type.KILL;
        a.target = target;
        a.count = 3;
        return a;
    }

    @Test
    void targetMatchingIgnoresCaseAndNamespace() {
        assertTrue(Matcher.matches(kill("zombie"), Achievement.Type.KILL, "ZOMBIE"));
        assertTrue(Matcher.matches(kill("minecraft:zombie"), Achievement.Type.KILL, "zombie"));
        assertTrue(Matcher.matches(kill(""), Achievement.Type.KILL, "CREEPER"));
        assertTrue(Matcher.matches(kill("*"), Achievement.Type.KILL, "CREEPER"));
        assertFalse(Matcher.matches(kill("ZOMBIE"), Achievement.Type.KILL, "CREEPER"));
        assertFalse(Matcher.matches(kill("ZOMBIE"), Achievement.Type.BREAK, "ZOMBIE"));
    }

    @Test
    void disabledAchievementsCountNothing() {
        Achievement a = kill("");
        a.enabled = false;
        assertTrue(Matcher.forEvent(List.of(a), Achievement.Type.KILL, "ZOMBIE").isEmpty());
    }

    @Test
    void statisticsAreConvertedToTheUnitOfTheAchievement() {
        assertEquals(10, Matcher.statValue(Achievement.Type.WALK, 1000));
        assertEquals(60, Matcher.statValue(Achievement.Type.PLAYTIME, 60L * 60 * 20));
        assertEquals(7, Matcher.statValue(Achievement.Type.JUMP, 7));
        assertTrue(Matcher.reached(kill(""), 3));
        assertFalse(Matcher.reached(kill(""), 2));
    }

    @Test
    void advancementJsonIsImpossibleTriggerWithEscapedText() {
        Achievement a = kill("ZOMBIE");
        a.title = "Er sagte \"Hallo\"";
        a.icon = "ROTTEN_FLESH";
        a.visibility = Achievement.Visibility.UNTIL_DONE;
        var o = JsonParser.parseString(AdvancementJson.of(a, "peakachievements:root", true)).getAsJsonObject();
        assertEquals("minecraft:impossible", o.getAsJsonObject("criteria").getAsJsonObject("done").get("trigger").getAsString());
        assertEquals("peakachievements:root", o.get("parent").getAsString());
        var d = o.getAsJsonObject("display");
        assertEquals("Er sagte \"Hallo\"", d.getAsJsonObject("title").get("text").getAsString());
        assertEquals("minecraft:rotten_flesh", d.getAsJsonObject("icon").get("id").getAsString());
        assertTrue(d.get("hidden").getAsBoolean());
        assertTrue(JsonParser.parseString(AdvancementJson.root("T", "D")).getAsJsonObject().getAsJsonObject("display").has("background"));
    }

    @Test
    void examplesAreValidAndHaveUniqueIds() {
        var ids = new HashSet<String>();
        for (Achievement a : Examples.all()) {
            assertTrue(a.check().isEmpty(), a.id + ": " + a.check());
            assertTrue(ids.add(a.id));
        }
        for (Achievement a : Examples.all()) assertTrue(a.parent.isEmpty() || ids.contains(a.parent));
    }
}
