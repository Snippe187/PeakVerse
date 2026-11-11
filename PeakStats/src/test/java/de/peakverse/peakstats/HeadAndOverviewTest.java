package de.peakverse.peakstats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadAndOverviewTest {
    @TempDir Path dir;

    private static String textures(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void onlyMojangTextureHostsAreAccepted() {
        assertEquals("https://textures.minecraft.net/texture/abc", HeadStore.skinUrl(textures("http://textures.minecraft.net/texture/abc")));
        assertNull(HeadStore.skinUrl(textures("http://evil.example.com/texture/abc")));
        assertNull(HeadStore.skinUrl(textures("http://textures.minecraft.net.evil.com/x")));
        assertNull(HeadStore.skinUrl(textures("http://user@textures.minecraft.net/x")));
        assertNull(HeadStore.skinUrl(textures("file:///etc/passwd")));
        assertNull(HeadStore.skinUrl("kein base64!"));
        assertNull(HeadStore.skinUrl(null));
    }

    @Test
    void cutsTheFaceAndHatOutOfASkin() throws Exception {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) skin.setRGB(8 + x, 8 + y, 0xFFFF0000);   // Gesicht rot
        skin.setRGB(40, 8, 0xFF00FF00);                                                                  // ein Hut-Pixel gruen
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(skin, "png", out);
        byte[] png = HeadStore.headFromSkin(out.toByteArray());
        assertNotNull(png);
        BufferedImage head = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(64, head.getWidth());
        assertEquals(0xFF00FF00, head.getRGB(0, 0), "Hut liegt ueber dem Gesicht");
        assertEquals(0xFFFF0000, head.getRGB(63, 63));
        assertNull(HeadStore.headFromSkin("kein png".getBytes(StandardCharsets.UTF_8)));
        BufferedImage wrong = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream o2 = new ByteArrayOutputStream();
        ImageIO.write(wrong, "png", o2);
        assertNull(HeadStore.headFromSkin(o2.toByteArray()), "falsche Groesse wird abgelehnt");
    }

    @Test
    void overviewQueries() throws Exception {
        try (StatsDb db = new StatsDb(dir.resolve("o.db"))) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            db.touch(a, "A", 1);
            db.touch(b, "B", 1);
            LocalDate d = LocalDate.of(2026, 11, 9);
            db.addAll(Map.of(a, Map.of(StatKeys.PLAY, 60L, "mine:stone", 5L), b, Map.of(StatKeys.PLAY, 40L)), d);
            db.addAll(Map.of(a, Map.of(StatKeys.PLAY, 10L)), d.plusDays(1));
            assertEquals(2, db.playerCount());
            assertEquals(110L, db.sumTotals().get(StatKeys.PLAY));
            assertTrue(!db.sumTotals().containsKey("mine:stone"), "Einzelwerte zaehlen nicht als Hauptwerte");
            assertEquals(Map.of("2026-11-09", 2L, "2026-11-10", 1L), db.activePerDay(d));
            long noon = java.time.LocalDateTime.of(2026, 11, 9, 14, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();   // Montag
            db.addEvents(List.of(new StatsDb.Event(noon, a.toString(), "login", "", "w", 0, 0, 0), new StatsDb.Event(noon, b.toString(), "login", "", "w", 0, 0, 0),
                    new StatsDb.Event(noon, b.toString(), "death", "x", "w", 0, 0, 0)));
            int[][] heat = db.heatmap();
            assertEquals(2, heat[0][14], "Montag 14 Uhr: zwei Anmeldungen, der Tod zaehlt nicht");
        }
    }
}
