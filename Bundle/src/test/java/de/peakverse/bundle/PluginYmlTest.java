package de.peakverse.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Schuetzt die zusammengefuehrte plugin.yml des All-in-One-Jars. Befehle und Rechte der Alpha-Version (PeakSound) duerfen nicht verschwinden,
 * sonst gehen bestehende Rechte-Einstellungen der Server verloren. Die Datei muss gueltiges YAML sein (ein falsch gesetzter Doppelpunkt
 * hat Paper schon einmal das Plugin ablehnen lassen), und die Hauptklasse und die eingebauten Module muessen im Jar liegen.
 */
class PluginYmlTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() throws Exception {
        try (InputStream in = PluginYmlTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml fehlt");
            return new Yaml().load(in);
        }
    }

    @Test
    void theOnePluginIsCalledPeakCore() throws Exception {
        assertEquals("PeakCore", load().get("name"));
        assertEquals("de.peakverse.core.plugin.PeakCorePlugin", load().get("main"));
        assertNotNull(PluginYmlTest.class.getResource("/de/peakverse/core/plugin/PeakCorePlugin.class"), "Hauptklasse nicht im Jar");
    }

    @Test
    void voiceChatStaysOptional() throws Exception {
        assertTrue(load().get("softdepend") instanceof List<?> list && list.contains("voicechat"));
        assertEquals(null, load().get("depend"), "das Jar hat keine harten Abhaengigkeiten");
    }

    @Test
    void commandsOfTheAlphaVersionAndTheCoreAreThere() throws Exception {
        Map<?, ?> commands = (Map<?, ?>) load().get("commands");
        for (String c : List.of("peakcore", "peaksound", "peaksoundvote")) assertTrue(commands.containsKey(c), "Befehl fehlt: " + c);
    }

    @Test
    void permissionNodesOfTheAlphaVersionAreStillThere() throws Exception {
        Map<?, ?> permissions = (Map<?, ?>) load().get("permissions");
        for (String node : List.of("peakcore.admin", "peaksound.upload", "peaksound.library", "peaksound.library.manage.own",
                "peaksound.library.manage.any", "peaksound.queue", "peaksound.vote", "peaksound.place", "peaksound.radio",
                "peaksound.limit.unlimited", "peaksound.admin")) {
            assertTrue(permissions.containsKey(node), "Recht fehlt: " + node);
        }
    }

    @Test
    void audioLibrariesAreLoadedByPaperNotBundled() throws Exception {
        List<?> libs = (List<?>) load().get("libraries");
        assertTrue(libs.contains("net.jthink:jaudiotagger:3.0.1"));
        assertEquals(5, libs.size());
    }

    @Test
    void builtInModulesAreRegisteredForTheServiceLoader() throws Exception {
        try (InputStream in = PluginYmlTest.class.getResourceAsStream("/META-INF/services/de.peakverse.core.api.module.PeakModule")) {
            assertNotNull(in, "Dienst-Datei der Module fehlt");
            String text = new String(in.readAllBytes());
            assertTrue(text.contains("de.peaksound.plugin.PeakSoundModule"));
            assertNotNull(PluginYmlTest.class.getResource("/de/peaksound/plugin/PeakSoundModule.class"));
        }
    }

    @Test
    void peakSoundDefaultConfigIsBundledAtTheModulePath() {
        assertNotNull(PluginYmlTest.class.getResource("/modules/peaksound/config.yml"));
        assertNotNull(PluginYmlTest.class.getResource("/config.yml"), "Core-config.yml fehlt");
    }
}
