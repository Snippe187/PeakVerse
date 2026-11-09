# Changelog

## 0.1.8
- Neues Modul **PeakAdmin**: Seite "Verwaltung" in der Web-Oberflaeche.
- **Spieler**: Liste (online/offline), Kick, Ban/Entbannen, Stummschalten, Whitelist, OP (nur Operatoren), Heilen, Spielmodus, XP-Level, Teleport zu Spieler; Inventar und Endertruhe im Minecraft-Look mit Item-Icons und Tooltips ansehen, Gegenstaende entfernen/geben/alles leeren; Fortschritte (Advancements) ansehen, einzeln vergeben/entziehen (Baum wie im Spiel mit Icons und Tooltips). Inventar, Endertruhe und Fortschritte nur bei Spielern, die online sind.
- **Konsole**: Live-Log (logs/latest.log) und Befehle senden (nur Operatoren, protokolliert).
- **Status**: TPS, MSPT, RAM, Spieler, Uptime, Welten (Entitaeten/Chunks) mit Verlauf-Diagrammen.
- **Chat**: Live-Chat lesen, Nachricht an alle senden, Stummschaltungen verwalten (`mutes.json`).
- **Sicherungen und Neustart**: Welt-Sicherung als ZIP (von Hand oder alle X Stunden, alte werden aufgeraeumt), Neustart mit Countdown und Chat-Warnungen, optional taeglicher Neustart (`restart.daily-at`).
- Alle Aenderungen stehen im Protokoll (Reiter Protokoll).- **Rechte (LuckPerms)**: ist LuckPerms installiert, erscheinen Gruppen, Rechte und Vererbung im Reiter 'Rechte'; Rechte aller Plugins stehen in einer Auswahlliste. Ohne LuckPerms bleiben die PeakCore-Rollen im Reiter 'Spieler'. (Noch nicht mit einem echten LuckPerms geprueft.)
- **Creative-Inventar**: Creative hat ein eigenes Inventar (beim Wechsel wird das andere gesichert und wiederhergestellt; creative.separate-inventory).
- **Item-Log**: Items aus dem Creative-Menue, per /give /item /summon, gedroppt oder in Behaelter gelegt im Creative-Modus, Moduswechsel und Web-give (Reiter Item-Log, Datei itemlog.jsonl). Noch nicht im Spiel mit mehreren Spielern geprueft.
