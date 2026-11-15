# PeakVerse – Projektbeschreibung (Stand 2026-11-15, Version 0.1.9)

Zweck dieses Dokuments: Ein anderer Agent soll das Projekt verstehen, Ideen dazu entwickeln und daraus einen Arbeits-Prompt für Claude Code bauen.

## 1. Überblick

PeakVerse ist ein selbst entwickeltes Minecraft-Paket für **Minecraft 26.2** (Paper-Server, Fabric-Client). Es besteht aus zwei Dateien pro Version:

- **`PeakCore-<Version>.jar`** – ein Paper-Plugin („All-in-one“), das alle Server-Module enthält.
- **`PeakVerse-Mod-<Version>.jar`** – eine einzige Fabric-Client-Mod, die PeakCore-Mod und PeakSound-Mod eingebettet enthält.

Alles ist ein Gesamtpaket mit gemeinsamem Web-Bereich („PeakWeb“) zur Verwaltung. Veröffentlicht wird bei GitHub (`Snippe187/PeakVerse-Releases`), später Modrinth (ein Projekt, beide Dateien je Version). Die Entwicklung läuft in privaten Repos.

Sprache: Der Nutzer schreibt Deutsch; Oberflächen, Texte und Doku sind deutsch (Mod auch Englisch).

## 2. Module (Server)

| Modul | Aufgabe |
|---|---|
| **PeakCore** | Kern: Modulsystem, PeakWeb (Web-Verwaltung mit Einmal-Login-Link), Updater (`/peakcore update`), Ressourcenpaket-Bau, Status/Fehlerprotokoll |
| **TexturePeak** | Texturen/Item-Icons; liefert Vanilla-Icons per Web-API (`/api/texturepeak/vanilla/...`) |
| **PeakBoard** | Scoreboard/Anzeigen mit festen Zeilen |
| **PeakAchievements** | Eigene Erfolge (Namespace `peakachievements`), Web-Editor für den Erfolgsbaum |
| **PeakEnchants** | Eigene Verzauberungen |
| **PeakAdmin** | Spielerverwaltung im Web: Minecraft-Look-Inventar mit Icons und Tooltips, Fortschritte als Baum, getrenntes Kreativ-Inventar, Item-Log (Kreativ, `/give` `/item` `/summon`, Drops, Container) |
| **PeakWarps** | Warps (`/setwarp <Name>`), Anbindung an SquareMap (optional) |
| **PeakRegions** | Regionen/Rechte, optional WorldGuard/WorldEdit/LuckPerms |
| **PeakStats** | Statistiken in SQLite (Spielzeit, AFK, Logins, Kills, Tode, Schaden, Blöcke, Erze, Distanz, Crafting; je Mob/Block), sofortiges Speichern, Vanilla-Import einmalig, Köpfe aus Skins, Admin-Web (Übersicht/Spieler/Bestenlisten/Freigabe, Live-Aktualisierung per SSE) |

Optionale Fremd-Plugins (LuckPerms, SquareMap, WorldGuard, WorldEdit) werden nur angesprochen, wenn sie installiert sind.

## 3. Client-Mod

- **PeakSound / PeakVerse-Mod** (Fabric): neue Fortschrittsansicht (Tabs PeakVerse/Vanilla, Zoom bis weit heraus, Pan, Suche, Filter, Pins als HUD, Sidebar mit Details, „Fehlt noch“-Checkliste), Sound-Optionen, Mod-Updater (bleibt an, auch für Modrinth – bewusste Entscheidung des Nutzers).
- Datenkanal Server↔Mod über PeakCore.

## 4. Technische Eckdaten

- Java, Gradle (je Modul eigenes Projekt; `Bundle` bündelt per `includeBuild`), Tests mit JUnit.
- Modulsystem: `PeakModule` mit `enable(ModuleContext)`; Kontext liefert Host-Plugin, Datenordner, Config, Web, Logger.
- PeakWeb: `WebRegistry` (Panel, Routen `/api/<modul>/...`, `changed(...)` → Server-Sent-Events), POST braucht Header `X-Peak: 1`, Login per Einmal-Link (`/peakcore web`). Panel-JS pro Modul als ES-Modul.
- Werkzeuge: `tools\build-all.ps1` (bauen, `-Install` auf Test-Server), `tools\release.ps1` (Release-Dateien; `-Upload` legt Entwurf an).
- Testumgebung: lokaler Paper-Server (`run\server`, Spiel-Port 25565, Web-Port 8123), Dev-Client per `gradlew runClient`.
- Arbeitsablauf siehe `docs/ARBEITSABLAUF.md`.

## 5. Arbeitsregeln (wichtig für den Prompt)

1. **Erst Fragen, dann bauen** (Umfang, Aussehen, Rechte, Daten).
2. Kleine, testbare Pakete; Gradle-Tests für alles Prüfbare; Eingaben prüfen, Web-Ausgaben maskieren.
3. Auf dem Test-Server prüfen; Ehrlich berichten, was **nicht** im echten Spiel geprüft wurde.
4. Commit + Push nach fertigen, getesteten Paketen (private Repos erlaubt).
5. **Veröffentlichen (GitHub-Release, Modrinth) nur mit ausdrücklichem Ja.** Nichts Unumkehrbares ohne Nachfrage (Welt, Releases, Daten löschen).
6. Datenschutz: Admin entscheidet je Statistik-Wert, was Spieler sehen. Server nur im Heimnetz freigeben.

## 6. Stand

Fertig und veröffentlicht (0.1.9): alle oben genannten Module, neue Fortschrittsansicht, PeakStats-Grundpaket mit Live-Web, Logos aller Module im Web.

**Noch nicht im echten Spiel geprüft:** Fortschrittsansicht und Pins, Kreativ-Inventar und Item-Log, echte Skins/Köpfe, Block-/Kill-Zählung, LuckPerms/SquareMap/WorldGuard/WorldEdit, die Mod im echten Fabric-Client, Verhalten bei vielen Spielern.

## 7. Geplante Pakete (Vorschlag, umsortierbar)

1. PeakStats: `/stats`, `/top` (klickbar, nur freigegebene Werte), PeakBoard-Zeile
2. PeakStats: Spieler-Webseite per `/stats web` (eigene Werte, Bestenlisten, Vergleich)
3. PeakStats: Mod-Bildschirm im Pausenmenü (Stil der Fortschrittsansicht)
4. Achievements: Web-Bereich je Spieler; 5. Punkte und Rang in den Mod
6. PeakAdmin: Drag-and-drop im Inventar; 7. Offline-Spieler; 8. Live-Karte; 9. Alarme/Regeln
10. Enchants-Rezepte (Villager/Truhen/Amboss) im Web
11. Warps-Menü im Spiel; 12. Claims/Grundstücke; 13. Quests mit Belohnungen

Die Wunschliste „Etwas anderes“ des Nutzers ist offen (er erinnert sich nicht mehr).

## 8. Offene Punkte

- Modrinth: Icon (10 Entwürfe gezeigt, noch keine Wahl), Galerie, Slug, Lizenz, Quell-Link; Updater-Regeln prüfen.
- Alter Entwurf v0.1.7 bleibt bestehen; alte Welt `world-alt-20261109` nur mit Ja löschen.

## 9. Fragen an den anderen Agenten

- Welche Funktionen fehlen einem Survival-/Community-Server wirklich (Wirtschaft, Jobs, Clans, Events, Wiki im Web)?
- Welche der Pakete haben den größten Nutzen für Spieler vs. Admins?
- Welche Risiken (Performance, Datenschutz, Modrinth-Regeln) sind zu klären?
- Ergebnis gewünscht: ein Prompt für Claude Code mit Ziel, Umfang, Fragen zuerst, Testplan und Regeln aus Abschnitt 5.
