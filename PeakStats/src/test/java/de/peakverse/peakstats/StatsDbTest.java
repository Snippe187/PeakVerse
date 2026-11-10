package de.peakverse.peakstats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsDbTest {
    @TempDir Path dir;
    StatsDb db;
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @BeforeEach
    void open() throws Exception {
        db = new StatsDb(dir.resolve("stats.db"));
        assertTrue(db.touch(a, "Alice", 1000));
        assertTrue(db.touch(b, "Bob", 1000));
    }

    @AfterEach
    void close() {
        db.close();
    }

    private static Map<UUID, Map<String, Long>> delta(UUID id, String key, long v) {
        Map<UUID, Map<String, Long>> m = new HashMap<>();
        m.put(id, new HashMap<>(Map.of(key, v)));
        return m;
    }

    @Test
    void touchKnowsReturningPlayersAndUpdatesTheName() throws Exception {
        assertFalse(db.touch(a, "AliceNeu", 2000));
        assertEquals("AliceNeu", db.nameOf(a));
        assertEquals(a, db.idOf("alicenEU"));
        assertNull(db.idOf("Niemand"));
    }

    @Test
    void addsTotalsAndDailyValues() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 9);
        db.addAll(delta(a, StatKeys.MINED, 5), today);
        db.addAll(delta(a, StatKeys.MINED, 7), today);
        db.addAll(delta(a, StatKeys.MINED, 3), today.minusDays(1));
        assertEquals(15L, db.totals(a).get(StatKeys.MINED));
        assertEquals(Map.of(today.minusDays(1).toString(), 3L, today.toString(), 12L), db.daily(a, StatKeys.MINED, today.minusDays(5)));
    }

    @Test
    void leaderboardsByPeriod() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 9);
        db.addAll(delta(a, StatKeys.PLAY, 100), today.minusDays(20));
        db.addAll(delta(b, StatKeys.PLAY, 60), today);
        List<StatsDb.Row> total = db.top(StatKeys.PLAY, null, 10, Set.of());
        assertEquals("Alice", total.get(0).name());
        List<StatsDb.Row> week = db.top(StatKeys.PLAY, today.minusDays(6), 10, Set.of());
        assertEquals(1, week.size());
        assertEquals("Bob", week.get(0).name());
        assertEquals("Bob", db.top(StatKeys.PLAY, null, 1, Set.of(a)).get(0).name(), "ausgeblendete Spieler fehlen");
    }

    @Test
    void baselineImportCountsOnceAndHasNoDailyValues() throws Exception {
        assertFalse(db.imported(a));
        db.importBaseline(a, Map.of(StatKeys.DEATHS, 4L, "mob:zombie", 9L));
        assertTrue(db.imported(a));
        assertEquals(4L, db.totals(a).get(StatKeys.DEATHS));
        assertEquals(0, db.count("daily"));
    }

    @Test
    void visibilityDefaultsToAdminsOnly() throws Exception {
        assertTrue(db.visible().isEmpty());
        db.setVisible(StatKeys.PLAY, true);
        assertEquals(Set.of(StatKeys.PLAY), db.visible());
        db.setVisible(StatKeys.PLAY, false);
        assertTrue(db.visible().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> db.setVisible("mob:zombie", true));
    }

    @Test
    void setValidatesKeysAndValues() throws Exception {
        db.set(a, StatKeys.DEATHS, 3);
        assertEquals(3L, db.totals(a).get(StatKeys.DEATHS));
        assertThrows(IllegalArgumentException.class, () -> db.set(a, "drop table", 1));
        assertThrows(IllegalArgumentException.class, () -> db.set(a, StatKeys.DEATHS, -1));
        db.set(a, "mine:stone", 5);
    }

    @Test
    void eventsArePrunedButDailyValuesStay() throws Exception {
        db.addEvents(List.of(new StatsDb.Event(1000, a.toString(), "death", "fall", "world", 1, 2, 3), new StatsDb.Event(9000, a.toString(), "login", "", "world", 0, 0, 0)));
        db.addAll(delta(a, StatKeys.DEATHS, 1), LocalDate.of(2026, 1, 1));
        assertEquals(1, db.pruneEvents(5000));
        assertEquals(1, db.events(a, 10).size());
        assertEquals("login", db.events(a, 10).get(0).type());
        assertEquals(1, db.count("daily"));
    }

    @Test
    void resetPlayerKeepsTheOtherOnes() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 9);
        db.addAll(delta(a, StatKeys.PLAY, 5), d);
        db.addAll(delta(b, StatKeys.PLAY, 7), d);
        db.resetPlayer(a);
        assertTrue(db.totals(a).isEmpty());
        assertEquals(7L, db.totals(b).get(StatKeys.PLAY));
        db.resetAll();
        assertTrue(db.totals(b).isEmpty());
        assertEquals("Bob", db.nameOf(b), "Spieler bleiben bekannt");
    }

    @Test
    void sqlInKeysIsNotExecuted() throws Exception {
        db.addAll(delta(a, "x'); DROP TABLE totals;--", 1), LocalDate.of(2026, 11, 9));
        assertEquals(1L, db.totals(a).get("x'); DROP TABLE totals;--"), "als Text gespeichert, nicht ausgefuehrt");
    }

    @Test
    void keyRulesAndPeriodsAndCsv() {
        assertTrue(StatKeys.valid("mob:zombie"));
        assertTrue(StatKeys.valid("mine:deepslate_diamond_ore"));
        assertFalse(StatKeys.valid("mob:Zombie; x"));
        assertEquals(StatKeys.KILLS_MOB, StatKeys.parentOf("mob:creeper"));
        assertEquals(StatKeys.MINED, StatKeys.parentOf("mine:stone"));
        assertNull(StatKeys.parentOf("irgendwas"));
        assertTrue(StatKeys.isOre("minecraft_diamond_ore".replace("minecraft_", "")));
        LocalDate d = LocalDate.of(2026, 11, 9);
        assertNull(StatsWeb.since("total", d));
        assertEquals(d, StatsWeb.since("today", d));
        assertEquals(d.minusDays(6), StatsWeb.since("week", d));
        assertThrows(IllegalArgumentException.class, () -> StatsWeb.since("jahr", d));
        assertEquals("\"'=cmd\"", StatsWeb.cell("=cmd"), "CSV-Formeln werden entschaerft");
        assertEquals("\"a\"\"b\"", StatsWeb.cell("a\"b"));
    }
}
