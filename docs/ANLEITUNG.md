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
3. Bereiche: **Texturen** (Pixel-Editor, Eintraege, Archiv, Faehigkeiten, Duplizieren, **Standard-Texturen**: Gegenstaende/Bloecke per Vorschaubild waehlen und kopieren oder im Editor aendern), **Pack** (Status, Testmodus, Tester, Freigabe, Zurueckrollen, Basis-Pack hochladen, Dateiliste mit Vorschau), **Bereiche** (Sounds, Texte, Schriften, Partikel, HUD, animierte Texturen, Modelle mit 3D-Vorschau, Himmel, Titelbild), **PeakSound** (alle Songs ansehen, anhoeren, umbenennen, freigeben/ablehnen, loeschen; Speicher und Songlimits pro Spieler; hochgeladen wird weiter im Spiel), **Protokoll**.
4. Arbeiten zwei Admins gleichzeitig, bekommt der zweite eine Konflikt-Warnung und kann bewusst ueberschreiben.
5. **Sicherheit:** ohne HTTPS laufen Link und Cookie im Klartext. Fuer das Internet: Reverse-Proxy mit HTTPS davor und `web.public-url` mit `https://`.

## Spieler, Rollen und Rechte (Reiter "Spieler")
PeakCore verwaltet Rechte selbst: Rollen (fertig: Spieler, DJ, Texturen-Team, Scoreboard, Admin; eigene frei anlegbar) mit Rechten und Web-Zugang, dazu Einzelrechte je Spieler (Erlauben/Vererben/Verbieten, getrennt je Modul), Notizen, Verwarnungen und Sperren (je Modul, nur Web oder alles, mit Ablaufdatum). Wer keinen Eintrag hat, behaelt genau die Rechte, die der Server sonst vergibt (Alpha-Kompatibilitaet). Spieler mit einer Rolle mit Web-Zugang oeffnen die Oberflaeche per /peakweb; sie sehen nur die Seiten ihrer Rolle. Die Spieler-Seite gibt es nur fuer Operatoren und Rollen mit Voll-Zugang (sonst koennte man sich selbst Rechte geben). Sitzungen gelten nur von der IP des Logins (config web.bind-session-ip, hinter einem Proxy auf false) und web.session-hours Stunden. Gespeichert in plugins/PeakCore/roles.json und players.json.

## Updates
In der config.yml: update.enabled: true und update.repo (oeffentliches GitHub-Release-Repo, z. B. Snippe187/PeakVerse-Releases). Jedes Release braucht PeakCore-<Version>.jar und PeakCore-<Version>.jar.sha256 (SHA-256-Pruefsumme); ohne Pruefsumme wird nichts installiert. PeakCore prueft alle update.check-hours Stunden und meldet Operatoren im Chat, dass es eine neue Version gibt. Im Web (Server-Seite, nur Operatoren/Voll-Zugang) oder mit /peakcore update check|download|restart <Min.>|cancel: das Jar wird in plugins/update/ gelegt (Paper tauscht es beim naechsten Start aus), auf Wunsch startet der Server mit Countdown im Chat neu (update.restart-mode: restart braucht ein Start-Skript, das den Server wieder hochfaehrt, in der spigot.yml restart-script; stop beendet ihn nur). Ein Reload ohne Neustart gibt es bewusst nicht. **Mods:** Die Mods pruefen beim Start selbst (config/peakcore/mod-update.properties, enabled=false schaltet das ab) das Repo auf neuere Dateien PeakCore-Mod-<V>.jar bzw. PeakSound-Mod-<V>.jar (je mit .sha256). Gibt es eine, steht beim Beitritt eine Chat-Meldung da; /peakcoreupdate laedt sie (Pruefsumme wird geprueft) nach config/peakcore/update-stage. Getauscht wird beim Beenden des Spiels durch ein kleines Skript (PowerShell unter Windows, sh sonst), weil eine laufende Mod ihr Jar sperrt; beim naechsten Start ist die neue Version aktiv. Nur Mods, die als Jar im mods-Ordner liegen, werden aktualisiert.

## Eine Version veroeffentlichen
1. .\tools\release.ps1 baut alles mit Tests und legt die Dateien (Server-Plugin, zwei Mods, Pruefsummen) in dist\release\<Version> ab.
2. .\tools\release.ps1 -Upload legt sie als **Entwurf** im oeffentlichen Repo Snippe187/PeakVerse-Releases an (das Repo muss vorher existieren und oeffentlich sein).
3. Bei GitHub unter Releases den Entwurf pruefen und **Publish release** klicken. Erst dann sehen die Server das Update in der Web-Oberflaeche. Die Versionsnummer steht in Bundle\gradle.properties.

## Anzeige rechts (PeakBoard)
Im Web unter Module > PeakBoard > Anzeige. Eine Vorlage waehlen (oder eigene Seiten anlegen), Titel und Zeilen aendern, rechts siehst du sofort die Vorschau; erst mit **Speichern und anwenden** sehen es die Spieler. Farben: &a, &#ff8800, <red>, <gradient:rot:blau>Text</gradient>, <rainbow>; Fett/Kursiv/Unterstrichen mit <b> <i> <u>. Platzhalter wie %kills%, %playtime%, %score:NAME%, %top:NAME:1% stehen als Knoepfe unter dem Editor. Mehrere Seiten wechseln im eingestellten Takt; ein Titel mit mehreren Bildern wird animiert. Jeder Spieler schaltet die Anzeige mit /board fuer sich aus und ein. Eigene Zaehler (Events, Minigames) sind Objectives (Reiter Uebersicht), die du mit %score:NAME% anzeigst und mit /scoreboard players add oder in der Tabelle aenderst.

Grenzen: Jeder Spieler bekommt ein eigenes Scoreboard. Teams, Tab-Liste und die Anzeige unter dem Namen werden uebernommen; was andere Plugins ueber ein eigenes Scoreboard setzen, sieht der Spieler dann nicht, solange die Anzeige an ist. Die Anzeige hoechstens 8 Seiten, 15 Zeilen je Seite.

## Handy verbinden
Auf der Anmeldeseite der Weboberflaeche unter "Vom Handy verbinden" den Minecraft-Namen eingeben (du musst dafuer im Spiel sein, als Operator oder mit einer Rolle mit Web-Zugang). Am Handy erscheint ein 4-stelliger Code, im Spiel kommt eine Nachricht mit demselben Code und [Erlauben]/[Ablehnen]. **Nur erlauben, wenn der Code auf deinem Handy steht**: wer nur deinen Namen kennt, kommt sonst nicht hinein. Danach bleibt das Handy angemeldet, ohne neuen Link. Alle verbundenen Geraete siehst du auf der Server-Seite ("Verbundene Geraete") und sperrst sie dort, oder im Spiel mit /peakweb devices und /peakweb revoke <id>. Abmelden am Handy vergisst das Geraet auch auf dem Server. Config: web.remote-login (an/aus) und web.device-expire-days (0 = nie, sonst Tage ohne Benutzung). Achtung: Ueber http:// (ohne https) kann der Schluessel im fremden WLAN mitgelesen werden. Das Handy daher nur im eigenen WLAN oder Mobilnetz benutzen oder eine Frist setzen.

## Pack-Bibliothek (Server-Pack aussuchen)
Im Web unter Server > Pack > Pack-Bibliothek: ein fertiges Resource-Pack (ZIP, bis 250 MB) hochladen oder auf die Flaeche ziehen. Der Server prueft es (Aufbau, pack.mcmeta, Grenzen) und repariert ein falsch gepacktes ZIP (alles in einem Unterordner oder Windows-Pfade mit Rueckwaerts-Schraegstrich). Jedes Pack ist eine Karte mit Vorschaubild, Format, Groesse und Notiz. **Als Server-Pack** schaltet es um und schickt es sofort an alle Spieler online; vorher kannst du mit **Konflikte pruefen** sehen, welche Dateien auch von TexturePeak oder PeakSound kommen (dort gewinnt die Textur des Moduls, damit die Gegenstaende funktionieren). Den alten Stand holst du ueber Versionen > Zurueckrollen. Speicher: Ein grosses Pack wird beim Bauen komplett in den Arbeitsspeicher geladen; fuer 250-MB-Packs sollte der Server mindestens 2 GB Heap haben. Download im Browser nur bis 100 MB (groessere liegen in plugins/PeakCore/pack-library/).

## Texturen-Inventar (TexturePeak)
Im Web unter Module > Texturen > Inventar: ein Raster wie das Kreativ-Inventar mit Reitern (Bausteine, Werkzeuge & Waffen, Rüstung, Essen & Tränke, Sonstiges, Alle, Eigene Items und GUI). Beim ersten Mal auf **Jetzt herunterladen** klicken: der Server holt einmalig die Bilder deiner Spielversion (Gegenstaende, Bloecke, Oberflaeche) von den offiziellen Mojang-Servern und legt sie nur bei dir ab. Oben stellst du ein, was du siehst: **Standard**, **Server-Pack** (so wie es im Spiel aussieht, mit den Texturen deines aktiven Packs) oder **Beides**; ein gruener Punkt in der Ecke heisst "vom Server-Pack geaendert", ein gelber "deine eigene Aenderung".

**Rechtsklick auf ein Bild** oeffnet das Menue: *Textur bearbeiten* (Pixel-Editor, das Bild ersetzt diese Textur fuer alle Spieler und wird sofort gesendet), *Kopieren als neues Item* (neuer Eintrag aus Gegenstand + Name am Amboss mit dieser Textur), *Faehigkeiten zuweisen* (legt dafuer eine Kopie an und oeffnet die Faehigkeiten), *Zuruecksetzen auf Standard* (nur wenn du etwas geaendert hast). Ein Klick zeigt Standard und Server-Pack nebeneinander. Im Reiter **GUI** liegen Inventar- und Container-Fenster, Hotbar, Herzen, Hunger, Knoepfe und Menue-Hintergruende; sie lassen sich genauso bearbeiten (jede Bildgroesse, nicht nur quadratisch). Animierte Texturen (mehrere Bilder untereinander) bearbeitest du als ganzes Bild. Deine Aenderungen liegen in plugins/TexturePeak/overrides/ und gewinnen gegen das Basis-Pack, weil sie als Textur des Moduls ins Pack kommen.

## Neue Texturen ohne Neu-Beitreten
Mod-Spieler laden ein geaendertes Pack selbst und es wird sofort aktiv (Minecraft laedt die Ressourcen neu). Im Test kam eine geaenderte Textur ohne Neu-Beitreten an; falls etwas fehlt, hilft F3 + T.

## Aussehen der Web-Oberflaeche
Kopfleiste wie bei minecraft.net, Startseite mit Pixel-Landschaft (Parallaxe beim Scrollen, Wolken ziehen), Tag/Nacht ueber den Schalter oben, Effekte (FX) abschaltbar. Alle Bilder sind selbst gezeichnet, nichts von Mojang.

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


