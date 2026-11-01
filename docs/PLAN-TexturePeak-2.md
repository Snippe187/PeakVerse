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
