package de.peakverse.peakadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminToolsTest {
    @TempDir Path tmp;

    @Test
    void backupZipsWorldWithoutLockAndPrunes() throws IOException {
        Path world = tmp.resolve("world");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "x");
        Files.writeString(world.resolve("session.lock"), "lock");
        Files.writeString(world.resolve("region/r.0.0.mca"), "data");
        BackupStore s = new BackupStore(tmp.resolve("bk"));
        BackupStore.Result r = s.create(world, "Auto!");
        assertEquals(0, r.skipped());
        try (ZipFile z = new ZipFile(s.file(r.backup().name()).toFile())) {
            assertNotNull(z.getEntry("level.dat"));
            assertNotNull(z.getEntry("region/r.0.0.mca"));
            assertNull(z.getEntry("session.lock"));
        }
        assertTrue(r.backup().name().endsWith("-auto.zip"));
        for (int i = 0; i < 3; i++) Files.copy(s.file(r.backup().name()), s.folder().resolve("backup-2020010" + i + "-000000.zip"));
        assertEquals(4, s.list().size());
        assertEquals(2, s.prune(2));
        assertEquals(2, s.list().size());
    }

    @Test
    void backupRejectsBadNamesAndSelfBackup() throws IOException {
        BackupStore s = new BackupStore(tmp.resolve("bk"));
        assertThrows(IllegalArgumentException.class, () -> s.file("../evil.zip"));
        assertThrows(IllegalArgumentException.class, () -> s.file("level.dat"));
        Path world = tmp.resolve("w2");
        Files.createDirectories(world);
        assertThrows(IOException.class, () -> new BackupStore(world.resolve("bk")).create(world, null));
    }

    @Test
    void muteStorePersistsAndExpires() throws IOException {
        Path f = tmp.resolve("mutes.json");
        MuteStore m = new MuteStore(f);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        m.mute(a, "A", "admin", "spam", 0);
        m.mute(b, "B", "admin", "x", 1);
        assertNotNull(new MuteStore(f).active(a));
        assertEquals(2, m.activeAll().size());
        assertTrue(m.unmute(a));
        assertFalse(m.unmute(a));
        assertNull(new MuteStore(f).active(a));
    }

    @Test
    void chatLogSinceAndLimit() {
        ChatLog c = new ChatLog(3);
        for (int i = 1; i <= 5; i++) c.add("p", "m" + i);
        assertEquals(3, c.since(0).size());
        assertEquals("m5", c.since(4).get(0).text());
        assertEquals(5, c.lastSeq());
    }

    @Test
    void statsHistoryKeepsLast() {
        StatsHistory h = new StatsHistory(2);
        for (int i = 0; i < 4; i++) h.add(new StatsHistory.Sample(i, 20, 1, 1, 2, 0, 0, 0));
        assertEquals(2, h.snapshot().size());
        assertEquals(2, h.snapshot().get(0).time());
    }

    @Test
    void logTailReadsNewLinesAndResets() throws IOException {
        Path log = tmp.resolve("latest.log");
        Files.writeString(log, "a\nb\n", StandardCharsets.UTF_8);
        LogTail t = new LogTail(log);
        LogTail.Chunk c = t.read(-1);
        assertEquals(2, c.lines().size());
        Files.writeString(log, "a\nb\nc\n", StandardCharsets.UTF_8);
        LogTail.Chunk n = t.read(c.position());
        assertEquals(1, n.lines().size());
        assertEquals("c", n.lines().get(0));
        Files.writeString(log, "neu\n", StandardCharsets.UTF_8);
        LogTail.Chunk r = t.read(100);
        assertTrue(r.reset());
        assertEquals("neu", r.lines().get(0));
        assertEquals(0, new LogTail(tmp.resolve("fehlt.log")).read(-1).lines().size());
    }

    @Test
    void playerNameAndTextAreSanitized() {
        assertEquals("Steve_1", PlayerTools.safeName("Steve_1"));
        assertThrows(IllegalArgumentException.class, () -> PlayerTools.safeName("a b; op x"));
        assertThrows(IllegalArgumentException.class, () -> PlayerTools.safeName(null));
        assertEquals("a  b", PlayerTools.safeText("a\n\rb"));
    }
}