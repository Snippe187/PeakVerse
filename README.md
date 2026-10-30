# PeakVerse

Gesamtprojekt: Server-Plugins und Fabric-Mods fuer Minecraft 26.2, aufgebaut als Basis (PeakCore) mit eigenstaendigen Modulen.

| Ordner | Zweck | Stand |
|---|---|---|
| `PeakCore/` | Basis-Plugin und Core-Mod: Kanal zur Mod, Resource-Pack-Dienst (Webserver, Hash, Versand), oeffentliche API | geplant |
| `PeakSound/` | Musik-Modul (MP3-Upload, Jukebox, Radio, Display, ...), haengt von PeakCore ab | geplant (Umbau aus dem Original) |
| `TexturePeak/` | Texturen-Modul (Items nach Namen, Faehigkeiten, Web-Editor fuer das Server-Pack) | geplant |
| `PeakStats/` | Statistik-Modul mit Web-Oberflaeche | spaeter |
| `ServerPackKeeper/` | Kleines Zusatzprojekt, spaeter (derzeit nur die Vorlage) | spaeter |
| `_archiv/` | Unveraenderte Originalstaende (nicht bearbeiten) | |
| `tools/` | Skripte: alles bauen, Jars in den Test-Server kopieren | |
| `run/` | Gemeinsamer Test-Server fuer alle Plugins | |
| `docs/` | Gesamtplan und Architektur | |

Jedes Modul ist ein eigenes Gradle-Projekt mit eigenem Git-Repository, eigener Version und eigenem Changelog.
Module kennen nur die oeffentliche API von PeakCore, nie einander.