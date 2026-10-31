# PeakVerse 0.1.0 - Anleitung

## Dateien (Ordner `dist\`)
| Datei | Wohin | Wofuer |
|---|---|---|
| `dist\PeakCore\Plugin\PeakCore-0.1.0.jar` | Server, `plugins/` | **Das einzige Server-Jar**: Core + PeakSound + TexturePeak. Die alte `PeakSound.jar` loeschen. |
| `dist\PeakCore\Mod\PeakCore-0.1.0.jar` | Spieler, `mods/` (Fabric, Minecraft 26.2) | Core-Mod: Verbindung zum Server, Pack-Empfang, Taste fuer die Web-Oberflaeche |
| `dist\PeakSound\Mod\PeakSound-0.1.0.jar` | Spieler, `mods/` | PeakSound-Mod (braucht die Core-Mod und die Fabric-API) |

Spieler ohne Mod werden nie gekickt. Sie sehen Texturen ueber das normale Server-Resource-Pack (Annahme-Dialog).

## Update vom Alpha-Stand (Freunde-Server)
1. **Sicherung**: `plugins/PeakSound/` kopieren (oder im Spiel `/peaksound backup`).
2. `PeakSound.jar` aus `plugins/` loeschen, `PeakCore-0.1.0.jar` hineinlegen, Server neu starten.
3. `plugins/PeakSound/` bleibt unveraendert in Benutzung; Rechte `peaksound.*`, Befehle und gebaute Bloecke bleiben. Spieler mit der alten Mod funktionieren weiter (alter Kanal), neue Funktionen nur mit der neuen Mod.
4. Das ist **noch nicht mit echten Alpha-Daten geprueft** (es gibt keine Sicherung vom echten Server). Bitte zuerst auf einer Kopie testen.

## Einstellungen (`plugins/PeakCore/config.yml`)
- `modules.<id>.enabled`: Module ein/aus (`peaksound`, `texturepeak`).
- **Resource-Pack an die Spieler**: `pack.web.enabled: true` (Port `pack.web.port`, in der Firewall freigeben), `pack.public-url: "http://dein-server:8123"`, dann `pack.send.enabled: true`. Mod-Spieler laden das Pack selbst und bekommen alle 2 Minuten eine Chat-Erinnerung, bis es beim naechsten Beitritt aktiv wird.
- **Web-Oberflaeche**: `web.enabled: true`, `web.public-url` (so wie du sie im Browser erreichst). Neustart.

## Web-Oberflaeche
1. Als Operator im Spiel `/peakcore web` (oder die Taste aus dem Steuerungs-Menue, "PeakVerse Web-Oberflaeche oeffnen"), den Link anklicken. In der Konsole: `peakcore web <operator>`.
2. Der Link gilt 5 Minuten und nur einmal; die Sitzung 8 Stunden und endet sofort nach `/deop`.
3. Bereiche: **Texturen** (Pixel-Editor, Eintraege, Archiv, Faehigkeiten, Duplizieren), **Pack** (Status, Testmodus, Tester, Freigabe, Zurueckrollen, Basis-Pack hochladen, Dateiliste mit Vorschau), **Bereiche** (Sounds, Texte, Schriften, Partikel, HUD, animierte Texturen, Modelle mit 3D-Vorschau, Himmel, Titelbild), **Protokoll**.
4. Arbeiten zwei Admins gleichzeitig, bekommt der zweite eine Konflikt-Warnung und kann bewusst ueberschreiben.
5. **Sicherheit:** ohne HTTPS laufen Link und Cookie im Klartext. Fuer das Internet: Reverse-Proxy mit HTTPS davor und `web.public-url` mit `https://`.

## Texturen: Ablauf
Web: Textur zeichnen oder PNG importieren, Gegenstand (z. B. `DIAMOND_HELMET`) und Namen eintragen. Im Spiel: Gegenstand am Amboss genau so benennen (oder `/texturepeak apply <kennung>` mit dem Gegenstand in der Hand). Mit Testmodus (`Pack` > Testmodus einschalten, Tester eintragen) sehen es erst die Tester; "Test-Pack fuer alle freigeben" macht es oeffentlich.

## Befehle
`/peakcore version|modules|status|players|pack|web`, `/peakcore pack status|rebuild|resend|versions|rollback|publish|test|variant`, `/texturepeak list|archive|add|remove|restore|apply|check|ability|reload`, plus alle `/peaksound ...` wie bisher.

## Bekannte Grenzen (ehrlich)
- Ruestung zeigt nur das Inventar-Symbol, **keine Koerper-Textur** am Spieler.
- Texturen im Spiel (sichtbar im Inventar/Hand) und Partikel wurden **nicht mit eigenen Augen im Spiel** geprueft; Effekte und Werte wurden im Spiel geprueft.
- Dateien aus "Bereiche" (Sounds, Partikel, HUD, Himmel, Titelbild, Animation) werden korrekt ins Pack gebaut, ihre Wirkung im Spiel ist nicht geprueft.
- 3D-Vorschau: keine Drehung einzelner Quader, keine `display`-Einstellungen; keine 3D-Ansicht fuer Ruestung am Koerper oder Item in der Hand.
- Kein grafischer Editor fuer Dateien des Basis-Packs; keine Vanilla-Textur-Vorschau.
- Der Annahme-Dialog fuer Spieler ohne Mod ist nicht getestet. Voice Chat/Upload/Mikrofon im Gesamt-Jar sind nicht neu getestet.
- Modrinth: Release-Skript `tools\modrinth-release.ps1` (Trockenlauf Standard); die Projekt-IDs in `tools\modrinth.json` sind leer.
