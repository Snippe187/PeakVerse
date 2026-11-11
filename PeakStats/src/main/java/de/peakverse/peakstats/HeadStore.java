package de.peakverse.peakstats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Speichert den Kopf jedes Spielers (aus dem Skin im Spielerprofil) als kleines PNG im Plugin-Ordner und liefert ihn fuer die Web-Seite.
 * Der Skin wird nur von {@code textures.minecraft.net} geladen (die Adresse steht im signierten Profil); ohne Skin (Offline-Server) gibt es kein Bild,
 * die Web-Seite zeigt dann einen selbst gezeichneten Kopf.
 */
final class HeadStore {
    private static final long MAX_SKIN_BYTES = 256 * 1024;
    private static final Duration REFRESH = Duration.ofDays(7);
    private static final int SCALE = 8;   // 8x8 Pixel -> 64x64

    private final Path folder;
    private final Logger log;

    HeadStore(Path folder, Logger log) {
        this.folder = folder;
        this.log = log;
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            log.warning("PeakStats: Kopf-Ordner nicht anlegbar: " + e.getMessage());
        }
    }

    Path file(UUID id) {
        return folder.resolve(id + ".png");
    }

    /** Die Skin-Adresse aus dem Wert der Profil-Eigenschaft "textures" (Base64-JSON); {@code null}, wenn keine gueltige da ist. */
    static String skinUrl(String texturesValue) {
        if (texturesValue == null || texturesValue.isBlank()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(texturesValue), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String url = o.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            URI u = URI.create(url);
            if (!"textures.minecraft.net".equals(u.getHost()) || u.getUserInfo() != null) return null;
            if (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme())) return null;
            return "https://textures.minecraft.net" + u.getRawPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    boolean fresh(UUID id) {
        try {
            Path f = file(id);
            return Files.exists(f) && Duration.between(Files.getLastModifiedTime(f).toInstant(), java.time.Instant.now()).compareTo(REFRESH) < 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Laedt den Skin und speichert den Kopf (blockierend; nicht auf dem Haupt-Thread aufrufen). */
    void store(UUID id, String url) {
        if (url == null || fresh(id)) return;
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            if (c.getResponseCode() != 200) return;
            byte[] data;
            try (InputStream in = c.getInputStream()) {
                data = in.readNBytes((int) MAX_SKIN_BYTES + 1);
            }
            if (data.length > MAX_SKIN_BYTES) return;
            byte[] png = headFromSkin(data);
            if (png != null) Files.write(file(id), png);
        } catch (IOException | RuntimeException e) {
            log.fine("PeakStats: Skin von " + id + " nicht geladen: " + e.getMessage());
        }
    }

    /** Schneidet Gesicht und Hut-Ebene aus einem Skin (64x64 oder 64x32) und vergroessert sie auf 64x64 Pixel. */
    static byte[] headFromSkin(byte[] skinPng) throws IOException {
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinPng));
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) return null;
        BufferedImage head = new BufferedImage(8 * SCALE, 8 * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                int hat = skin.getRGB(40 + x, 8 + y);
                int px = (hat >>> 24) > 127 ? hat | 0xFF000000 : base;
                for (int dy = 0; dy < SCALE; dy++) for (int dx = 0; dx < SCALE; dx++) head.setRGB(x * SCALE + dx, y * SCALE + dy, px);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(head, "png", out);
        return out.toByteArray();
    }

    byte[] read(UUID id) {
        try {
            Path f = file(id);
            return Files.exists(f) ? Files.readAllBytes(f) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
