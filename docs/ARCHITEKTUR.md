# PeakVerse – Architektur

## Überblick

```
Server:  PeakCore-<Version>.jar   (ein Bukkit-Plugin, gebaut von Bundle/)
           ├─ Core (core-plugin, core-api, core-protocol)
           └─ eingebaute Module (PeakSound, später TexturePeak, ...), je per Schalter an/aus

Client:  eine Fabric-Mod je Baustein, damit man sie einzeln installieren kann
           ├─ PeakCore-Mod   (Handshake, Admin-Status, Modul-Routing)
           └─ PeakSound-Mod  (hängt an der Core-Mod; fällt auf den alten Kanal zurück)
```

| Projekt | Aufgabe |
|---|---|
| `PeakCore/` | Core: Handshake, Modul-Laden, Kanal `peakcore:main`, API für Module, Core-Mod |
| `PeakSound/` | Modul PeakSound (Server-Teil als Modul, Mod als eigene Fabric-Mod) |
| `Bundle/` | setzt Core und Module zum Jar `PeakCore-<Version>.jar` zusammen, enthält die zusammengeführte `plugin.yml` |
| `ModuleTemplate/` | Vorlage für ein neues Modul (baubar, mit Test) |
| `tools/` | `build-all.ps1` (alles bauen), `new-module.ps1` (neues Modul anlegen) |

## Wie ein Modul geladen wird

1. Bundle legt alle Teil-Jars in ein Jar. Jedes Modul bringt `META-INF/services/de.peakverse.core.api.module.PeakModule` mit.
2. Beim Start findet der Core die Module per `ServiceLoader`. In `plugins/PeakCore/config.yml` steht unter `modules.<id>.enabled`, ob ein Modul läuft (Standard: an). Ein altes, einzelnes PeakSound-Jar auf dem Server schaltet das eingebaute Modul ab, damit nichts doppelt läuft.
3. Der Core ruft `enable(ModuleContext)` auf. Jedes Modul bekommt einen eigenen Datenordner (`plugins/<Anzeigename>`), eine eigene Config (`modules/<id>/config.yml` im Jar liefert die Standardwerte) und einen eigenen Logger.
4. Fehler in `enable` schalten nur dieses Modul ab. `disable` läuft beim Herunterfahren in umgekehrter Reihenfolge.

Module an/aus gilt erst nach einem Serverneustart.

## Was ein Modul darf (öffentliche API, `core-api`)

| Schnittstelle | Zweck |
|---|---|
| `PeakModule` | `id`, `displayName`, `enable`, `disable`, optional `contributePack` |
| `ModuleContext` | Host-Plugin (für Events, Scheduler, Befehle), Datenordner, Config, Logger, Version |
| `PeakCoreApi` | `isModded`, `client`, `isAdmin`, Modul-Registrierung für den Netzwerkkanal |
| `ModuleRegistration` / `ModuleMessageHandler` | Nutzdaten an die Mod eines Spielers senden und empfangen (Kanal `peakcore:main`, Modul-ID im Umschlag) |
| `PlayerHandshakeEvent` | Ein Spieler hat den Handshake abgeschlossen |
| `PackSink` / `PackPaths` | Dateien fürs Server-Resource-Pack beitragen (Pack-Bau: siehe unten) |

Regeln:
- **Rechte prüft immer der Server** (`PeakCoreApi.isAdmin`), nie die Angabe der Mod.
- **Schlüssel und Namensräume nutzen die Modul-ID**, nicht den Plugin-Namen, damit Umbenennungen keine Daten entwerten.
- Module lesen den Datenordner nicht aus `getDataFolder()` des Hosts, sondern aus dem Kontext.
- Pack-Pfade sind klein geschrieben (`a-z 0-9 _ - . /`); `PackPaths.validate` lehnt alles andere ab.

## Kompatibilität mit der Alpha-Version

Bleibt unverändert: Datenordner `plugins/PeakSound`, Befehle (`/peaksound`, `/peaksoundvote`), Rechte `peaksound.*`, Kanal `peaksound:main`, Item-Markierungen und gebaute Blöcke. Die neue Mod nutzt den PeakCore-Kanal, wenn der Server ihn kennt, sonst den alten.

## Weiter

- Neues Modul anlegen: [NEUES_MODUL.md](NEUES_MODUL.md)
- Gesamtplan: [PLAN.md](PLAN.md)

## Das Server-Pack (seit Phase 9)

`/peakcore pack rebuild` ruft `contributePack` aller laufenden Module auf und baut `plugins/PeakCore/pack/pack-<sha1>.zip`.
- **Deterministisch:** gleiche Dateien ergeben dieselben Bytes und denselben SHA1 (sortiert, fester Zeitstempel, ohne Kompression).
- **Ein Pfad, ein Besitzer:** liefern zwei Module denselben Pfad, fehlt das zweite ganz im Pack und `/peakcore pack status` nennt den Grund. Das Zusammenführen geteilter Vanilla-Dateien kommt in Phase 11.
- `pack.mcmeta` kommt aus der Core-Config (`pack.description`, `pack.format-major/minor`), nicht von Modulen.
- **Versand (Phase 10):** eingebauter Webserver (pack.web.enabled, Port 8123) liefert nur /pack/<sha1>.zip; Adresse aus pack.public-url oder pack.external-url (Ausweg: Pack liegt woanders). Gesendet wird per Paper-API mit fester Pack-ID, damit ein neues Pack das alte ersetzt. Alles ist standardmaessig aus.
