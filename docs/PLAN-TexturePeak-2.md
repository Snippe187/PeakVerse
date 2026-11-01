# TexturePeak 2: Plan für die neuen Funktionen

Auswahl des Besitzers (alles gewünscht, Reihenfolge von mir nach Nutzen und Risiko):
Editor: Ebenen/Auswahl, Formen/Verlauf, Farbwerkzeuge, Animation. Verwaltung: Sammlungen/Tags, Vorschau im Spiel-Look, Versionen, Massen-Import/Export.
Im Spiel: Rüstung am Körper, mehr Fähigkeiten, Overlay-Effekte, Rezepte/Vergabe. Team: Entwurf und Freigabe.

Regeln wie bisher: eine Stufe = ein Commit je Repo, Tests, Testliste, ehrlich über Grenzen. Jede Stufe ist einzeln nutzbar.

## Stufe 1: Editor-Werkzeuge (nur Web, kaum Risiko)
- [ ] Formen: Linie, Rechteck (leer/gefüllt), Kreis/Ellipse, Farbverlauf
- [ ] Spiegel-Zeichnen (waagrecht/senkrecht) und Raster an/aus, Zoom per Mausrad
- [ ] Farbwerkzeuge: Umfärben (Farbton), Helligkeit/Kontrast, Farbe ersetzen, Palette aus der Textur
- [ ] Auswahl: Rechteck markieren, verschieben, kopieren/einfügen, drehen/spiegeln
- [ ] Ebenen: mehrere Ebenen, sichtbar/unsichtbar, Reihenfolge, Deckkraft, beim Speichern zusammenfügen

## Stufe 2: Verwaltung (Web + kleine Server-Änderung)
- [ ] Tags und Sammlungen (Felder in entries.json, Filter in der Liste)
- [ ] Versionen: jede Texturänderung wird gesichert, alte Stände ansehen und wiederherstellen
- [ ] Massen-Import (mehrere PNGs, Kennung aus Dateiname) und Export als ZIP
- [ ] Vorschau im Spiel-Look: Gegenstand im Inventar-Feld, in der Hand (einfache 3D-Ansicht), auf dem Boden

## Stufe 3: Animation (Web + Pack)
- [ ] Bilder-Streifen zeichnen, Vorschau abspielen, Geschwindigkeit, `.mcmeta` wird erzeugt
- [ ] Server baut die animierte Textur ins Pack (Prüfung: Streifen = Vielfaches der Breite)

## Stufe 4: Fähigkeiten und Rezepte (Server)
- [ ] Neue Auslöser: Treffer, Sprinten, Blocken, Tod, Blockabbau; Abklingzeit
- [ ] Rezepte: eigenen Gegenstand craftbar machen; Vergabe per Befehl und Web-Knopf
- [ ] Tests mit Paper-Mock wo möglich, Rest im Spiel (Testliste)

## Stufe 5: Team (Server + Web)
- [ ] Entwurf: Änderungen sammeln, ohne das Pack zu bauen
- [ ] Veröffentlichen als eine Freigabe (ein Pack-Bau), Verwerfen, Liste der Änderungen vorher ansehen

## Stufe 6: Mod-Seite (größtes Risiko, zuletzt)
- [ ] Rüstung am Körper: eigene Rüstungstexturen am Spieler (Equipment-Modelle im Pack, Client-Prüfung im Spiel)
- [ ] Overlay-Effekte: Bildschirm-Overlay per Fähigkeit schaltbar (z. B. nass bei Regen oder unter Wasser); braucht Mod-Rendering und echten Test im Spiel

## Offene Fragen
- Overlay: Bildschirm-Overlay oder Item-Optik? Auslöser (Regen, Wasser, Fähigkeit)? Nur mit Mod oder auch ohne?
- Rüstung: nur Aussehen oder auch eigene Werte?

## Entscheidungen aus den 20 Fragen (vom Besitzer)
- **Reihenfolge:** zuerst Spieler-Reiter und Rechte (Stufe 0), danach Editor, PeakSound-Web, zuletzt Overlay und Rüstung.
- **Spieler-Reiter (neu, Stufe 0):** Rechte pro Spieler **getrennt je Modul** (PeakCore, PeakSound, TexturePeak), dazu Limits und Zugriff, Verlauf und Notizen, Sperren und Verwarnen (mit Grund und Ablaufdatum). Alles in einem eigenen Reiter, der Spieler verwaltet.
- **Rechte-System:** eigene Verwaltung in PeakCore (kein LuckPerms nötig), **fertige Rollen plus eigene Rollen** (z. B. Spieler, DJ, Texturen-Team, Admin).
- **Web-Zugang:** Rollen mit Teilzugriff (z. B. Texturen-Team sieht nur Texturen, DJs nur PeakSound), nicht nur Operatoren. Sitzung zusätzlich **an die IP gebunden**, Sitzungsdauer einstellbar.
- **Server-Seite:** Spieler online (mit Köpfen), Leistung (TPS, RAM), Konsole, Pack-Auslieferung (wer hat das Pack, wer fehlt).
- **Meldungen:** Discord-Webhook, im Spiel an Admins, im Browser. **Handy:** egal. **Stil:** Mix (Startseite und Logos pixelig, Verwaltung ruhig und modern). **Ein Server** pro Oberfläche.
- **PeakSound-Web:** Song-Upload im Browser (mit Umwandlung und Prüfung), Playlists und Radio, Statistiken, Block-Übersicht.
- **TexturePeak Import:** anderes Resource-Pack öffnen und einzelne Texturen übernehmen, aus großem Bild zuschneiden, Blockbench-Modelle.
- **Item-Baukasten:** Beschreibung und Farbe, Werte und Verzauberungen, eigene Sounds, Bedingungen (Welt, Biom, Zeit, Permission).
- **Effekte:** visueller Partikel-Editor mit Vorschau im Browser.
- **Overlay:** nur mit Mod. Art (Tropfen, Rahmen, Vollbild, animiert) und Auslöser (Item, Wetter, Ort, Befehl) blieben offen ("keine Präferenz"): ich schlage vor, Tropfen/Nässe bei Regen und unter Wasser sowie ein Item-Overlay als Start und lasse Spieler die Stärke abschwächen oder abschalten (Barrierefreiheit).
- **Updates:** Modrinth für Spieler plus GitHub-Releases (öffentliches Release-Repo, Code bleibt privat) für die Server-Jar und die Update-Meldung.

## Stufe 0 (neu): Spieler, Rechte, Rollen
- [ ] Rechte-Speicher in PeakCore (`plugins/PeakCore/players.json`, `roles.json`), Rechte werden beim Beitritt und bei Änderung per Bukkit-Berechtigung vergeben
- [ ] Rechte-Katalog: jedes Modul meldet seine Rechte mit Erklärung an (getrennt je Modul)
- [ ] Rollen: fertige (Spieler, DJ, Texturen-Team, Admin) plus eigene; Rolle = Rechte-Satz + Web-Teilzugriff
- [ ] Web-Zugang für Nicht-Operatoren (Rolle mit Web-Recht), Sitzung an IP gebunden, Dauer einstellbar
- [ ] Reiter "Spieler": Liste (online/offline), Detail mit Rechten je Modul, Limits, Verlauf, Notizen, Sperren/Verwarnen
- [ ] Alte Rechte (`peaksound.*`) bleiben gültig: Alpha-Kompatibilität; Standard = wie bisher, wenn nichts eingestellt ist
