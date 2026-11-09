package de.peakverse.peakwarps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarpStoreTest {
    @TempDir Path tmp;

    private static WarpStore.Warp warp(String name) {
        WarpStore.Warp w = new WarpStore.Warp();
        w.name = name;
        w.world = "world";
        w.y = 64;
        return w;
    }

    @Test
    void saveReloadAndDelete() throws IOException {
        Path f = tmp.resolve("warps.json");
        WarpStore s = new WarpStore(f);
        WarpStore.Warp w = warp("spawn");
        w.restricted = true;
        w.description = "a\nb";
        s.save(w, "Admin");
        WarpStore again = new WarpStore(f);
        assertEquals(1, again.all().size());
        assertTrue(again.get("SPAWN").restricted);
        assertEquals("Admin", again.get("spawn").creator);
        assertEquals("a b", again.get("spawn").description);
        assertTrue(again.delete("spawn"));
        assertFalse(again.delete("spawn"));
        assertEquals(0, new WarpStore(f).all().size());
    }

    @Test
    void creatorStaysOnUpdate() throws IOException {
        WarpStore s = new WarpStore(tmp.resolve("w.json"));
        s.save(warp("a"), "Erster");
        WarpStore.Warp w2 = warp("a");
        w2.x = 5;
        s.save(w2, "Zweiter");
        assertEquals("Erster", s.get("a").creator);
        assertEquals(5, s.get("a").x);
    }

    @Test
    void validation() {
        WarpStore s = new WarpStore(tmp.resolve("v.json"));
        assertThrows(IllegalArgumentException.class, () -> s.save(warp("Bad Name"), "x"));
        assertThrows(IllegalArgumentException.class, () -> s.save(warp("../x"), "x"));
        WarpStore.Warp far = warp("far");
        far.x = 99_999_999;
        assertThrows(IllegalArgumentException.class, () -> s.save(far, "x"));
        WarpStore.Warp nan = warp("nan");
        nan.z = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> s.save(nan, "x"));
        WarpStore.Warp icon = warp("ic");
        icon.icon = "../evil";
        try {
            assertEquals("ender_pearl", s.save(icon, "x").icon);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertNotNull(s.get("ic"));
    }

    @Test
    void brokenFileStartsEmpty() throws IOException {
        Path f = tmp.resolve("broken.json");
        java.nio.file.Files.writeString(f, "{kaputt");
        assertEquals(0, new WarpStore(f).all().size());
    }

    @Test
    void mapTooltipIsEscaped() {
        assertEquals("&lt;script&gt;&amp;&quot;", WarpStore.html("<script>&\""));
    }
}