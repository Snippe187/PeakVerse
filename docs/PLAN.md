# PeakVerse - Gesamtplan (20 Phasen)

Regeln: Server ist massgeblich; Spieler ohne Mod werden nie gekickt; keine Platzhalter; ein Commit pro Aufgabe; Tests fuer Logik; Testliste je Phase; Grenzen ehrlich benennen. Nach jeder Phase gibt es etwas Lauffaehiges zum Testen. Das Original `_archiv/PeakSound-Original` wird nie veraendert.

## Entschieden
- Resource-Pack-Varianten: **Standard** und **Lite**.
- Aenderungen laufen zuerst im **Testmodus** (`/texturepeak test add|remove|publish|discard`), erst nach Freigabe fuer alle.
- Mod laedt neue Packs selbst, fragt per Chat (klickbar) und erinnert alle 2 Minuten; kein Kick.
- Spieler ohne Mod bekommen das Pack ueber den normalen Minecraft-Weg (Server-Resource-Pack), automatisch vom Core gebaut und gehostet.
- Web-Oberflaeche: modernes dunkles Dashboard mit Pixel-Akzenten, DE/EN, Tag/Nacht, Klick- und Hover-Effekte, Menue-Sounds, animierter Hintergrund, Erfolgs-Meldungen, Schalter "Effekte reduzieren"; alle Grafiken selbst gezeichnet, frei lizenzierte Schrift.
- Mehrere Admins gleichzeitig (Live-Aktualisierung, Konflikt-Warnung, Aenderungsprotokoll). Zugang nur fuer Operatoren, per Einmal-Link aus dem Spiel.
- Operator-Liste im Spiel mit Archiv (geloeschte Eintraege bleiben holbar).
- Pack-Optimierung beim Bauen (verlustfreie PNG-Kompression, deterministisch).
- Vanilla-Texturen fuer Vorschauen kommen aus dem eigenen Spiel des Admins, nie vom Server oder aus dem Plugin.
- Texturen-Modul zuerst fuer Rüstung, Fähigkeiten: Werte, Effekte, Partikel.

## Kompatibilität mit der Alpha-Version (verbindlich)
Der Alpha-Stand laeuft schon auf einem Server mit Freunden. Alles Neue kommt dort als **Update**; bestehende Anlagen (Jukeboxen, Lautsprecher, DJ-Pulte, Radios, Displays, Bibliothek, Songs, Playlists, Einstellungen) bleiben weiter benutzbar.
- **Daten:** Plugin-Name `PeakSound`, Ordner `plugins/PeakSound/` und alle Dateien (`blocks.yml`, `songs.json`, `jukebox-*.json`, `radio.json`, `displays.json`, ...) behalten ihr Format. Neue Felder nur zusaetzlich mit Standardwert; Aenderungen am Format nur mit Migration (vorwaerts) und automatischer Sicherung vor dem ersten Start der neuen Version.
- **Welt:** Block-Zustaende (Notenblock `bit` Ton 15-24, nummerierte Lautsprecher `custom_head`), Item-Markierung `peaksound:type`, `custom_model_data`-Strings, Rezept-Schluessel, Befehle und Rechte (`peaksound.*`) bleiben unveraendert.
- **Protokoll:** Das PeakSound-Modul behaelt zusaetzlich den alten Kanal `peaksound:main` (Protokoll 2), damit Spieler mit der **alten Alpha-Mod** weiter funktionieren, bis sie aktualisieren. Neue Funktionen (Pack-Empfang, Texturen) gibt es nur mit der neuen Mod.
- **Installation:** Der Server braucht zusaetzlich das PeakCore-Jar (PeakSound laedt ohne PeakCore nicht, das ist so gewollt). Sonst aendert sich fuer die Spieler nichts.
- **Test:** Jede Phase hat einen Alpha-Kompatibilitaetstest: neuer Stand startet auf einer Kopie der Alpha-Serverdaten (idealerweise ein `/peaksound backup` vom echten Freunde-Server), alle Anlagen laden und spielen, `/peaksound debug` zeigt keine Fehler.

## Phasen

### Block A - Fundament
- [x] **1. Gerüst und Übernahme.** PeakSound aus dem Original nach `PeakVerse/PeakSound` kopieren, je Modul eigenes Git-Repo, Gradle-Projekte trennen, `tools/build-all`, gemeinsamer Test-Server. *Ergebnis:* alles baut und läuft wie vorher.
- [x] **2. PeakCore-Grundgerüst.** Projekte `core-api`, `core-protocol`, `core-plugin`; Plugin laedt, registriert den Dienst im ServicesManager, Versions- und Kompatibilitaetspruefung, `/peakcore modules`. *Test:* Core laedt allein.
- [x] **3. Kanal und Umschlag.** Kanal `peakcore:main`, Umschlag `[Modul-ID, Nutzdaten]`, Codec, Grenzen, Tests. *Test:* Unit-Tests fuer Umschlag und Fehlerfaelle.
- [x] **4. Handshake und Admin-Prüfung.** Hello/Ack mit Core- und Modulliste, Mod-Erkennung, Nicht-Mod-Hinweis, Diagnose, Admin-Pruefung (OP + `peakcore.admin`) im Core. *Test:* Handshake auf dem Testserver.
- [x] **5. Core-Client-Mod.** Fabric-Mod `peakcore-client`: Kanal, Handshake, Modul-Routing, Admin-Status. *Test:* Mod meldet sich beim Core.

### Block B - PeakSound als Modul
- [x] **6. PeakSound-Server als Modul.** `depend: [PeakCore]`, Netzwerk auf Modul-Kanal umstellen, Handshake-Teile in den Core verlagern, Name/Daten/Rechte unveraendert, alter Kanal `peaksound:main` bleibt (Alpha-Mods funktionieren weiter). *Test:* alle bisherigen Tests, Testserver-Regression und Alpha-Kompatibilitaetstest.
- [ ] **7. PeakSound-Mod als Modul.** Mod haengt an der Core-Mod, Protokollversion hochziehen, Menues und Audio unveraendert. *Test:* Upload, Wiedergabe, Radio im Spiel.
- [ ] **8. API-Feinschliff und Modul-Vorlage.** Öffentliche API festziehen (Pack-Beitrag, Events, Kanal), Beispiel-Modul-Vorlage, Architektur-Doku. *Ergebnis:* neues Modul in wenigen Schritten anlegbar.

### Block C - Pack-Dienst im Core
- [ ] **9. Pack bauen.** Deterministisches ZIP, `pack.mcmeta`, Beitraege je Namensraum, SHA1, Cache, Tests (stabiler Hash).
- [ ] **10. Webserver und Versand.** Eingebauter HTTP-Server, Port/URL-Config, externe URL als Ausweg, Versand per Paper-API, Neusenden bei Aenderung, `/peakcore pack status|rebuild|resend`.
- [ ] **11. Merge und Konflikte.** Basis-Pack (Upload) plus Modul-Beitraege, Zusammenfuehren der geteilten Vanilla-Dateien (Item-Definitionen, Blockstates, Lang), Konflikt-Erkennung fuer Pfade und Block-Zustaende, Tests.
- [ ] **12. Versionen, Testmodus, Varianten.** Pack-Versionen mit Zurueckrollen, Testmodus fuer ausgewaehlte Spieler per Befehl, Varianten Standard/Lite, PNG-Optimierung.
- [ ] **13. Pack-Empfang in der Mod.** Mod laedt das Pack selbst, Chat-Erinnerung alle 2 Minuten, Aktivierung beim naechsten Beitritt, Modell-Zuordnung per Code (PeakModels) im Core.

### Block D - Texturen-Modul
- [ ] **14. TexturePeak-Server.** Regeln (Item-Typ + Name -> Textur), Eintrags-Datenbank mit Archiv, Pruefung (Item-Typ, PNG-Groesse), Items markieren, `/texturepeak reload`.
- [ ] **15. Fähigkeiten.** Werte, Effekte, Partikel serverseitig, getragen oder gehalten, Tests.

### Block E - Web-Oberfläche
- [ ] **16. Web-Gerüst und Design.** Einmal-Link-Anmeldung, Seiten-Rahmen im Core fuer Module, Design-System (Pixel-Look, DE/EN, Tag/Nacht, Effekte, Sounds), Live-Aktualisierung, Mehrfach-Admin mit Konflikt-Warnung und Protokoll.
- [ ] **17. Pack-Manager und 2D-Editor.** Pack hochladen, Dateiliste mit Vorschau, Pixel-Editor, Duplizieren unter anderem Namen, Uebernehmen ueber den Testmodus.
- [ ] **18. Weitere Bereiche.** Partikel, HUD, Sounds, Texte, Schriften, animierte Texturen, Himmel und Titelbild.
- [ ] **19. 3D und Modelle.** 3D-Ansicht fuer Rüstung und Items in der Hand, Block-Modelle, Blockbench-Import.

### Block F - Abschluss
- [ ] **20. Mod-Menüs, Gesamttest, Release.** Operator-Liste im Spiel mit Archiv, Admin-Button, Gesamttest aller Module einzeln und zusammen, Doku, Showroom, Release-Pakete und Modrinth-Texte. Modrinth-Release-Skript je Modul (Version + Changelog aus `CHANGELOG.md`, Trockenlauf als Standard, Upload nur mit ausdruecklichem Schalter und deinem API-Token als Umgebungsvariable). Danach: `PeakStats` und `ServerPackKeeper` als spaetere Module.

## Offene Punkte
- Liste im Spiel: nur ansehen und holen (Bearbeiten im Browser) - Empfehlung A, noch zu bestaetigen.
- Wo laeuft der Server (Port/HTTPS)? Entscheidet, ob ein offener Port und HTTPS moeglich sind.
- Ablageort `PeakVerse/_archiv/PeakSound-Original`: Original liegt noch unter `Desktop/SpottyBlock/PeakSound` (Ordner war gesperrt), wird nach dem Schliessen dieser Sitzung verschoben.
