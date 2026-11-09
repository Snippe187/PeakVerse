package de.peakverse.peakenchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.peakverse.core.api.ModuleInfo;
import de.peakverse.core.api.module.PeakModule;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/** Schuetzt die Grundregeln eines Moduls: gueltige ID, ServiceLoader-Eintrag, Standard-Config im Jar. Weitere Tests des Moduls hier ergaenzen. */
class PeakEnchantsModuleTest {
    @Test
    void idIsValidForPeakCore() {
        PeakEnchantsModule module = new PeakEnchantsModule();
        new ModuleInfo(module.id(), module.displayName(), "0.1.0", 1); // wirft bei ungueltiger ID
    }

    @Test
    void isFoundByTheServiceLoader() {
        boolean found = ServiceLoader.load(PeakModule.class).stream().anyMatch(p -> p.type() == PeakEnchantsModule.class);
        assertTrue(found, "META-INF/services/de.peakverse.core.api.module.PeakModule fehlt oder nennt die Klasse nicht");
    }

    @Test
    void defaultConfigIsAtTheModulePath() {
        assertNotNull(getClass().getResource("/modules/" + new PeakEnchantsModule().id() + "/config.yml"));
    }

    @Test
    void disableWithoutEnableIsSafe() {
        new PeakEnchantsModule().disable();
        assertEquals("peakenchants", new PeakEnchantsModule().id());
    }
}
