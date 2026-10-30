# PeakVerse

Gesamtprojekt: Server-Plugins und Fabric-Mods fuer Minecraft 26.2, aufgebaut als Basis (PeakCore) mit eigenstaendigen Modulen.

| Ordner | Zweck | Stand |
|---|---|---|
| `PeakCore/` | Basis-Plugin und Core-Mod: Kanal zur Mod, Modul-Lader, Resource-Pack-Dienst (Webserver, Hash, Versand), oeffentliche API | im Bau |
| `PeakSound/` | Musik-Modul (MP3-Upload, Jukebox, Radio, Display, ...), eingebautes Modul von PeakCore plus eigene Client-Mod | im Bau |
| `Bundle/` | Baut das All-in-One-Server-Plugin `PeakCore-<Version>.jar` aus Core und Modulen | im Bau |
| `TexturePeak/` | Texturen-Modul (Items nach Namen, Faehigkeiten, Web-Editor fuer das Server-Pack) | geplant |
| `PeakStats/` | Statistik-Modul mit Web-Oberflaeche | spaeter |
| `ServerPackKeeper/` | Kleines Zusatzprojekt, spaeter (derzeit nur die Vorlage) | spaeter |
| `_archiv/` | Unveraenderte Originalstaende (nicht bearbeiten) | |
| `tools/` | Skripte: alles bauen, Jars in den Test-Server kopieren, neues Modul anlegen | |
| `ModuleTemplate/` | Vorlage für neue Module, siehe `docs/NEUES_MODUL.md` | |
| `run/` | Gemeinsamer Test-Server fuer alle Plugins | |
| `docs/` | Gesamtplan und Architektur | |

Auf dem Server liegt **ein einziges Plugin-Jar** (`PeakCore`); jedes Modul darin ist in der `config.yml` ein- und ausschaltbar. Auf dem Client gibt es getrennte Mods (Core-Mod plus eine je Modul).

Jedes Modul ist ein eigenes Gradle-Projekt mit eigenem Git-Repository, eigener Version und eigenem Changelog.
Module kennen nur die oeffentliche API von PeakCore, nie einander.