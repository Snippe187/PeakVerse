package de.peakverse.peakachievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreTest {
    @TempDir Path dir;

    private static Achievement ach(String id, String parent) {
        Achievement a = new Achievement();
        a.id = id;
        a.title = "Titel " + id;
        a.parent = parent;
        a.type = Achievement.Type.KILL;
        a.target = "ZOMBIE";
        a.count = 100;
        return a;
    }

    @Test
    void savesAndReloadsEverything() throws IOException {
        AchievementStore s = new AchievementStore(dir);
        Achievement a = ach("zombies", "");
        a.rewards.xp = 50;
        a.rewards.items.add("DIAMOND:3");
        s.put(a);
        VanillaOverride o = new VanillaOverride();
        o.enabled = false;
        o.points = 5;
        s.putVanilla("minecraft:story/mine_stone", o);

        AchievementStore t = new AchievementStore(dir);
        t.load();
        assertEquals(100, t.get("zombies").count);
        assertEquals(50, t.get("zombies").rewards.xp);
        assertFalse(t.vanillaEnabled("minecraft:story/mine_stone"));
        assertTrue(t.vanillaEnabled("minecraft:story/other"));
        assertEquals(5, t.vanilla("minecraft:story/mine_stone").points);
    }

    @Test
    void rejectsBadInput() {
        AchievementStore s = new AchievementStore(dir);
        Achievement bad = ach("Gross Schreibung", "");
        assertThrows(IllegalArgumentException.class, () -> s.put(bad));
        Achievement self = ach("x", "x");
        assertThrows(IllegalArgumentException.class, () -> s.put(self));
        Achievement cmd = ach("y", "");
        cmd.rewards.items.add("DIAMOND:abc");
        assertThrows(IllegalArgumentException.class, () -> s.put(cmd));
        assertThrows(IllegalArgumentException.class, () -> s.putVanilla("peak:x", new VanillaOverride()));
    }

    @Test
    void refusesParentCyclesAndKeepsTheOldState() throws IOException {
        AchievementStore s = new AchievementStore(dir);
        s.put(ach("a", ""));
        s.put(ach("b", "a"));
        s.put(ach("c", "b"));
        assertThrows(IllegalArgumentException.class, () -> s.put(ach("a", "c")));
        assertEquals("", s.get("a").parent);
    }

    @Test
    void removingAParentReattachesChildrenToTheRoot() throws IOException {
        AchievementStore s = new AchievementStore(dir);
        s.put(ach("a", ""));
        s.put(ach("b", "a"));
        s.remove("a");
        assertEquals("", s.get("b").parent);
        assertNull(s.get("a"));
        assertThrows(IllegalArgumentException.class, () -> s.remove("a"));
    }

    @Test
    void brokenFileIsKeptAndTheServerStillStarts() throws IOException {
        Files.writeString(dir.resolve("achievements.json"), "{kaputt");
        AchievementStore s = new AchievementStore(dir);
        s.load();
        assertTrue(s.all().isEmpty());
        assertTrue(Files.exists(dir.resolve("achievements.json.kaputt")));
    }

    @Test
    void progressCountsAwardsAndSurvivesReload() throws IOException {
        UUID p = UUID.randomUUID();
        ProgressStore s = new ProgressStore(dir);
        assertTrue(s.award(p, "a", 1000));
        assertFalse(s.award(p, "a", 2000));
        assertEquals(3, s.add(p, "kill:ZOMBIE", 3));
        assertEquals(5, s.add(p, "kill:ZOMBIE", 2));
        s.saveIfDirty();

        ProgressStore t = new ProgressStore(dir);
        t.load();
        assertTrue(t.isDone(p, "a"));
        assertEquals(5, t.counter(p, "kill:ZOMBIE"));
        assertEquals(1, t.holders("a"));
        assertTrue(t.revoke(p, "a"));
        assertFalse(t.isDone(p, "a"));
        assertFalse(t.revoke(p, "a"));
    }
}
