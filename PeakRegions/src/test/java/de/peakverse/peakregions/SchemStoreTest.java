package de.peakverse.peakregions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemStoreTest {
    @TempDir Path tmp;

    @Test
    void saveListReadDelete() throws IOException {
        SchemStore s = new SchemStore(tmp.resolve("schematics"));
        s.save("burg.schem", new byte[] {1, 2, 3});
        s.save("alt.schematic", new byte[] {9});
        assertEquals(2, s.list().size());
        assertEquals("alt.schematic", s.list().get(0).name());
        assertEquals(3, s.read("burg.schem").length);
        assertTrue(s.delete("burg.schem"));
        assertFalse(s.delete("burg.schem"));
        assertEquals(1, s.list().size());
    }

    @Test
    void rejectsBadNamesAndSizes() {
        SchemStore s = new SchemStore(tmp.resolve("s"));
        assertThrows(IllegalArgumentException.class, () -> s.save("../x.schem", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> s.save("a.txt", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> s.save("a..b.schem", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> s.save("ok.schem", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> s.save("big.schem", new byte[(int) SchemStore.MAX_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> s.read("nope.schem"));
        assertFalse(SchemStore.validName("a/b.schem"));
        assertFalse(SchemStore.validName(null));
    }

    @Test
    void listWithoutFolderIsEmpty() throws IOException {
        assertEquals(0, new SchemStore(tmp.resolve("fehlt")).list().size());
        assertFalse(Files.exists(tmp.resolve("fehlt")));
    }
}