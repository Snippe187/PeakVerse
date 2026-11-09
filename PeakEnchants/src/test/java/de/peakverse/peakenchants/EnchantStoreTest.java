package de.peakverse.peakenchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnchantStoreTest {
    static final String ENCH = "{\"description\":\"Curse of Agony\",\"supported_items\":\"#minecraft:enchantable/armor\",\"weight\":1,\"max_level\":1,"
            + "\"min_cost\":{\"base\":25,\"per_level_above_first\":0},\"max_cost\":{\"base\":50,\"per_level_above_first\":0},\"anvil_cost\":8,\"slots\":[\"armor\"],\"effects\":{}}";
    @TempDir Path dir;
    EnchantStore store;

    @BeforeEach
    void setUp() {
        store = new EnchantStore(dir.resolve("datapacks"), dir.resolve("backups"));
    }

    static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    byte[] pack() throws Exception {
        return zip(Map.of("pack.mcmeta", "{\"pack\":{\"description\":\"Test\",\"pack_format\":107}}", "data/ench/enchantment/agony.json", ENCH));
    }

    @Test
    void installsListsAndTogglesAnAddon() throws Exception {
        store.install("ench", pack());
        assertEquals(1, store.addons().size());
        assertEquals(1, store.addons().get(0).count());
        assertTrue(store.addons().get(0).enabled());
        assertTrue(store.restartNeeded());
        store.setEnabled("ench", false);
        assertFalse(store.addons().get(0).enabled());
        assertTrue(Files.exists(dir.resolve("datapacks/ench/pack.mcmeta.off")));
        store.setEnabled("ench", true);
        assertTrue(store.addons().get(0).enabled());
    }

    @Test
    void stripsASingleTopFolderAndReplacesWithBackup() throws Exception {
        store.install("a", zip(Map.of("top/pack.mcmeta", "{\"pack\":{\"description\":\"x\"}}", "top/data/n/enchantment/e.json", ENCH)));
        assertEquals(Set.of("n:e"), store.enchantKeys("a"));
        store.install("a", pack());
        assertEquals(Set.of("ench:agony"), store.enchantKeys("a"));
        assertTrue(Files.list(dir.resolve("backups")).findAny().isPresent());
    }

    @Test
    void refusesUnsafeOrWrongZips() {
        assertThrows(IllegalArgumentException.class, () -> store.install("x", zip(Map.of("../evil.txt", "x"))));
        assertThrows(IllegalArgumentException.class, () -> store.install("x", zip(Map.of("a.txt", "x"))));
        assertThrows(IllegalArgumentException.class, () -> store.install("x", zip(Map.of("pack.mcmeta", "{}"))));
        assertThrows(IllegalArgumentException.class, () -> store.install("x", "no zip".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> store.install("../x", pack().clone()));
    }

    @Test
    void draftEditsOnlyApplyOnPublishAndAreBackedUp() throws Exception {
        store.install("ench", pack());
        JsonObject j = store.enchant("ench", "ench:agony");
        j.addProperty("weight", 7);
        store.stage("ench", "ench:agony", j);
        store.stageTags("ench", "ench:agony", Set.of("treasure"));
        assertEquals(7, store.enchant("ench", "ench:agony").get("weight").getAsInt());
        assertTrue(Files.readString(dir.resolve("datapacks/ench/data/ench/enchantment/agony.json")).contains("\"weight\":1"));
        assertEquals(Set.of("treasure"), store.tagsOf("ench", "ench:agony"));
        int n = store.publish();
        assertEquals(2, n);
        assertTrue(Files.readString(dir.resolve("datapacks/ench/data/ench/enchantment/agony.json")).contains("\"weight\":7"));
        assertTrue(Files.exists(dir.resolve("datapacks/ench/data/minecraft/tags/enchantment/treasure.json")));
        assertFalse(store.hasDraft());
    }

    @Test
    void discardAndDeleteWork() throws Exception {
        store.install("ench", pack());
        store.stageDelete("ench", "ench:agony");
        assertEquals(Set.of(), store.enchantKeys("ench"));
        store.discard();
        assertEquals(Set.of("ench:agony"), store.enchantKeys("ench"));
        assertThrows(IllegalArgumentException.class, () -> store.stageDelete("ench", "ench:nope"));
    }

    @Test
    void validationRejectsBrokenValues() throws Exception {
        JsonObject ok = com.google.gson.JsonParser.parseString(ENCH).getAsJsonObject();
        EnchantStore.validate(ok);
        for (String f : new String[] {"weight", "max_level", "min_cost", "slots", "supported_items", "description"}) {
            JsonObject bad = ok.deepCopy();
            bad.remove(f);
            assertThrows(IllegalArgumentException.class, () -> EnchantStore.validate(bad), f);
        }
        JsonObject bad = ok.deepCopy();
        bad.addProperty("weight", 5000);
        assertThrows(IllegalArgumentException.class, () -> EnchantStore.validate(bad));
        assertThrows(IllegalArgumentException.class, () -> store.stage("ench", "Bad Key", ok));
    }

    @Test
    void ownAddonNeedsAnExistingPackForItsVersion() throws Exception {
        JsonObject ok = com.google.gson.JsonParser.parseString(ENCH).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> store.stage("peakenchants", "peakenchants:new", ok));
        store.install("ench", pack());
        store.stage("peakenchants", "peakenchants:new", ok);
        store.publish();
        assertTrue(store.addons().stream().anyMatch(a -> a.id().equals("peakenchants") && a.count() == 1));
    }
}
