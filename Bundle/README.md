# Bundle - das All-in-One-Plugin

Setzt das Server-Plugin `PeakCore-<Version>.jar` aus dem Core (`../PeakCore`) und den eingebauten Modulen (`../PeakSound`, spaeter TexturePeak) zusammen. Auf dem Server liegt nur dieses eine Jar.

- `src/main/resources/plugin.yml`: die zusammengefuehrte plugin.yml (Befehle und Rechte von Core und Modulen, `libraries` fuer die Audio-Bibliotheken). Neues Modul: seine Befehle und Rechte hier eintragen.
- `build.gradle.kts`: Abhaengigkeiten auf die Module; neues Modul = eine Zeile `implementation(...)` plus Schalter in der `config.yml` des Cores.
- Fremde Bibliotheken (LGPL) werden nie eingeschmolzen, Paper laedt sie ueber `libraries`.

Bauen: `./gradlew build` (Java 25) oder alle Module gemeinsam mit `../tools/build-all.ps1 -Install`. Ergebnis: `build/libs/PeakCore-<Version>.jar`.
