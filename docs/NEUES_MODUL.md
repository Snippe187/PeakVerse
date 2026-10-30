# Neues Modul anlegen

In fünf Schritten, ohne Netzwerkcode, wenn das Modul nur serverseitig arbeitet.

## 1. Aus der Vorlage erzeugen

```powershell
.\tools\new-module.ps1 -Id texturepeak -Name TexturePeak
```

Das kopiert `ModuleTemplate/` nach `TexturePeak/` und benennt Paket, Klasse, ID, Config-Pfad und Anzeigenamen um.
Die ID hat 2–24 Zeichen (`a-z`, `0-9`, `_`) und beginnt mit einem Buchstaben; sie ist später Netzwerk-Kennung, Config-Schlüssel und Pack-Namensraum und lässt sich nicht mehr ändern, ohne Daten zu entwerten.

## 2. Ins Gesamt-Jar einhängen

Das Skript nennt die drei Stellen am Ende:

1. `Bundle/settings.gradle.kts`: `includeBuild("../TexturePeak")`
2. `Bundle/build.gradle.kts`: `implementation("de.peakverse:texturepeak")`
3. `PeakCore/core-plugin/src/main/resources/config.yml`: unter `modules:` einen Eintrag `texturepeak: enabled: true`

Brauchen Befehle oder Rechte des Moduls einen Eintrag, kommen sie in `Bundle/src/main/resources/plugin.yml`. Externe Bibliotheken, die nicht eingeschmolzen werden sollen, kommen dort unter `libraries:` (Paper lädt sie beim Start).

## 3. Das Modul schreiben

Die Klasse `TexturePeakModule` hat vier Stellen:
- `enable(ModuleContext)`: Config laden, Listener, Befehle und Scheduler über `context.host()` anmelden.
- `contributePack(PackSink)`: Dateien fürs Resource-Pack ablegen (wirkt ab Phase 9).
- `disable()`: Daten speichern, Aufgaben beenden. Läuft auch nach einem fehlgeschlagenen `enable`, `context` kann `null` sein.
- Standardwerte der Config stehen in `src/main/resources/modules/<id>/config.yml`.

Daten gehören nach `context.dataFolder()`, Meldungen an `context.logger()`.

## 4. Bauen und testen

```powershell
cd TexturePeak; .\gradlew.bat build        # Modul allein, mit dem Vorlagentest
cd ..; .\tools\build-all.ps1 -Install      # Gesamt-Jar bauen und in den Test-Server kopieren
```

Dann auf dem Testserver `/peakcore modules` ausführen: Das Modul muss als „läuft“ erscheinen. Mit `modules.<id>.enabled: false` und Neustart muss es als „aus“ erscheinen.

## 5. Mod-Seite (nur wenn nötig)

Braucht das Modul Menüs oder Rendering im Spiel, bekommt es eine eigene Fabric-Mod mit `depends: peakcore` und meldet sich in `PeakCoreSession.whenReady(session -> session.registerModule(...))` an. Als Muster dient `PeakSound/fabric-mod`.

## Prüfliste

- [ ] ID gültig, `/peakcore modules` zeigt das Modul
- [ ] Ausschalten per Config (`enabled: false`) lässt Core und andere Module unberührt
- [ ] Ein Fehler in `enable` schaltet nur dieses Modul ab
- [ ] Daten liegen im eigenen Ordner, Rechte prüft der Server
