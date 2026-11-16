package de.peakverse.peakstats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StatFormatTest {
    @Test
    void formatsDurations() {
        assertEquals("5 Sek", StatFormat.duration(5_000));
        assertEquals("3 Min", StatFormat.duration(185_000));
        assertEquals("2 Std 5 Min", StatFormat.duration(2 * 3_600_000L + 5 * 60_000L));
        assertEquals("1 T 3 Std", StatFormat.duration(86_400_000L + 3 * 3_600_000L));
        assertEquals("0 Sek", StatFormat.duration(-5));
    }

    @Test
    void formatsValuesByUnit() {
        assertEquals("1,5 km", StatFormat.value("cm", 150_000));
        assertEquals("250 m", StatFormat.value("cm", 25_000));
        assertEquals("12,3 HP", StatFormat.value("hp10", 123));
        assertEquals("1.234", StatFormat.value("n", 1234));
    }

    @Test
    void mapsWordsToKeysAndPeriods() {
        assertEquals(StatKeys.PLAY, StatFormat.keyFor("Spielzeit"));
        assertEquals(StatKeys.DEATHS, StatFormat.keyFor("tode"));
        assertEquals(StatKeys.MINED, StatFormat.keyFor("blocks_mined"));
        assertNull(StatFormat.keyFor("quatsch"));
        assertNull(StatFormat.keyFor(null));
        assertEquals("week", StatFormat.periodFor("woche"));
        assertEquals("total", StatFormat.periodFor(null));
        assertNull(StatFormat.periodFor("jahr"));
    }
}
