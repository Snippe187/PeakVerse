package de.peakverse.example;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;

/** Ein minimales Modul: meldet sich im Log und liest eine Einstellung aus {@code plugins/Example/config.yml}. */
public final class ExampleModule implements PeakModule {
    private ModuleContext context;

    @Override
    public String id() {
        return "example";
    }

    @Override
    public String displayName() {
        return "Example";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig(); // kopiert modules/example/config.yml aus dem Jar, falls es noch keine gibt
        context.logger().info(context.config().getString("greeting", "Hallo") + " (Version " + context.version() + ")");
        // Hier Listener (context.host().getServer().getPluginManager()...), Befehle und Scheduler anmelden.
    }

    @Override
    public void contributePack(PackSink sink) {
        // Dateien fuers Server-Resource-Pack, z. B.: sink.text("assets/example/lang/de_de.json", "{}");
    }

    @Override
    public void disable() {
        // Daten speichern, Aufgaben beenden. Wird auch nach einem fehlgeschlagenen enable() aufgerufen: context kann null sein.
        if (context != null) context.logger().info("beendet");
        context = null;
    }
}
