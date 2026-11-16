package de.peakverse.peakstats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsLookupTest {
    @TempDir Path dir;

    @Test
    void onlyReleasedValuesAreShown() throws Exception {
        try (StatsDb db = new StatsDb(dir.resolve("l.db"))) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            db.touch(a, "Anna", 1);
            db.touch(b, "Ben", 1);
            db.addAll(Map.of(a, Map.of(StatKeys.PLAY, 3_600_000L, StatKeys.DEATHS, 2L), b, Map.of(StatKeys.PLAY, 7_200_000L)), LocalDate.now());
            StatsLookupImpl l = new StatsLookupImpl(db);
            assertEquals("-", l.value(a, "spielzeit"), "nichts freigegeben");
            assertEquals("-", l.topName("spielzeit", 1));
            db.setVisible(StatKeys.PLAY, true);
            StatsLookupImpl l2 = new StatsLookupImpl(db);
            assertEquals("1 Std 0 Min", l2.value(a, "spielzeit"));
            assertEquals("-", l2.value(a, "tode"), "Tode bleiben verborgen");
            assertEquals("Ben", l2.topName("play_ms", 1));
            assertEquals("2 Std 0 Min", l2.topValue("spielzeit", 1));
            assertEquals("Anna", l2.topName("spielzeit", 2));
            assertEquals("-", l2.topName("spielzeit", 3));
            assertEquals("-", l2.value(a, "quatsch"));
        }
    }
}
