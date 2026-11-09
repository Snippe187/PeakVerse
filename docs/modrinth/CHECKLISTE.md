# Modrinth: Checkliste (nur vorbereiten, Upload macht der Nutzer)

Ein Projekt fuer beides (Entscheidung): eine Modrinth-Version enthaelt PeakCore-<V>.jar (Plugin) und PeakVerse-Mod-<V>.jar (Mod).

| Feld | Wert |
|---|---|
| Name | PeakVerse |
| Slug | peakverse (pruefen, ob frei) |
| Projekt-Typ | Mod (Plugin-Datei wird als zusaetzliche Datei angehaengt) oder zwei Projekte, falls Modrinth das so verlangt |
| Kurzbeschreibung (max. 256 Zeichen) | All-in-one server suite for Paper 26.2 with an optional Fabric client mod: web admin, players, backups, warps, music, textures, scoreboards, achievements, enchantments. |
| Kategorien | management, utility (Server); Fabric: library nicht noetig |
| Loader | fabric (Mod), paper (+ spigot/bukkit) (Plugin) |
| Spielversion | 26.2 |
| Umgebung | Client: optional, Server: required |
| Abhaengigkeit | Fabric API (Pflicht fuer die Mod) |
| Lizenz | GPL-3.0-only |
| Beschreibung | docs/modrinth/DESCRIPTION.md |
| Changelog je Version | dist/release/<V>/RELEASE-NOTES.md |
| Dateien | dist/release/<V>/*.jar (+ .sha256 nur fuer GitHub) |
| Icon | fehlt noch (512x512 PNG), Galerie-Bilder fehlen noch (Web-Oberflaeche, Spiel) |
| Links | Quellcode/Issues: entscheiden (Repos sind noch privat; Releases-Repo ist oeffentlich: https://github.com/Snippe187/PeakVerse-Releases) |

Vor dem Upload pruefen:
- [ ] Mod in echtem Fabric-Client getestet (Jar-in-Jar mit PeakCore-Mod + PeakSound-Mod)
- [ ] Plugin auf frischem Paper 26.2 gestartet, `/peakcore modules` zeigt alle Module
- [ ] Alte Jars (PeakCore-Mod, PeakSound-Mod) werden nicht doppelt geladen
- [ ] Icon, Galerie, Slug, Lizenz-Text entschieden
- [ ] Modrinth-Regeln: keine Auto-Update-/Download-Funktionen, die Modrinth untersagt, pruefen (der Mod-Updater laedt von GitHub)