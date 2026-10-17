# PlayerAdminPlugin (Paper 1.21.11)

Ein kleines Admin-Plugin mit `/player <Name>` und Inventar-Menü.

## Funktionen
- Spieler-Verwaltungsmenü für online Spieler
- Spieler einfrieren/auftauen
- Unsterblichkeit umschalten (verhindert eingehenden Schaden)
- sichtbaren Namen ändern
- Tablist-Namen ändern
- Skin-Profil eines anderen Spielers übernehmen (abhängig von Paper-Version/Client; ggf. erneutes Verbinden nötig)
- Berechtigung `playeradmin.use` (standardmäßig OP)

## Voraussetzungen
- Minecraft Java Server mit Paper 1.21.11
- Java 21
- Maven 3.9 oder neuer zum Bauen

## Bauen
Im Ordner `PlayerAdminPlugin` ausführen:

```bash
mvn clean package
```

Die JAR liegt anschließend unter `target/PlayerAdminPlugin-1.0.0.jar`.
Kopiere sie in den Serverordner `plugins/` und starte den Server neu.

## Verwendung
```text
/player Steve
```

Im Menü anklicken und bei Namens-/Skin-Aktionen den gewünschten Wert in den Chat schreiben.
Mit `-cancel` kannst du abbrechen.

## Hinweise
- Das Plugin ist für Paper gedacht, nicht garantiert für reines Spigot.
- Der echte Minecraft-Accountname kann nicht serverseitig geändert werden; geändert werden nur sichtbare Namen.
- Skin-Änderungen über Profile können je nach Paper-Build und Client Aktualisierung/erneutes Verbinden benötigen.
- Freezes und Unsterblichkeit werden aktuell nur gespeichert, solange der Server läuft; nach Neustart sind sie zurückgesetzt.
