# Plot Render

Rein clientseitige Fabric-Mod für Minecraft 1.20.1. Plot Render lässt geladene
Chunks, Netzwerkverkehr, Weltzustand und Kollision unverändert, nimmt aber alles
außerhalb einer gespeicherten rechteckigen Chunk-Region aus den Renderlisten.

## Verwendung

1. Im ersten Eck-Chunk `/plotrender pos1` ausführen.
2. Im gegenüberliegenden Eck-Chunk `/plotrender pos2` ausführen.
3. Mit `/plotrender save` die inklusive Rechteckregion speichern.
4. Mit `/plotrender toggle` den Filter ein- oder ausschalten.

Die Region und der Aktivierungszustand werden pro Server bzw. Singleplayer-Welt
und Dimension unter `config/plotrender/<sha256>.json` gespeichert und beim
Betreten automatisch geladen. Die gehashte Bezeichnung verhindert, dass eine
Serveradresse als Dateiname offengelegt wird.

Spieler werden immer gerendert. Alle anderen Entities, BlockEntities und
Partikel werden außerhalb der Region nicht gerendert.

## Technische Umsetzung

- Vanilla: Filter in `WorldRenderer.addBuiltChunk`, bevor sichtbare Terrainlisten
  und Chunk-Rebuild-Arbeit entstehen.
- Sodium 0.5.x: optionaler `VisibleChunkCollector`-Mixin, der Sections vor
  Renderlisten, Draw-Commands und Rebuild-Queues verwirft. Während der Filter
  aktiv ist, bleibt die reine Graph-Traversierung unabhängig von Occlusion-Daten
  ungezeichneter Außen-Sections, damit der Plot auch von außen erreichbar bleibt.
- Iris: dessen normale und Shadow-Passes verwenden die bereits gefilterten
  Vanilla-/Sodium-Terrainlisten. Damit gelangt außerhalb der Region keine
  Chunk-Geometrie in den Shadow-Pass.
- Separate Filter für Entities, BlockEntities und die eigentliche
  Partikel-Geometrie.

Es werden weder Render Distance noch Serverpakete, Chunk-Laden, Logik oder
Kollision verändert. Die Sodium-Integration ist optional und wird ohne Sodium
über `@Pseudo`-Mixins sauber übersprungen. Es werden keine Reflection-Hacks
verwendet.

## Build

Voraussetzungen: JDK 17 oder neuer und Internetzugriff beim ersten Build.

```bash
./gradlew build
```

Unter Windows:

```powershell
.\gradlew.bat build
```

Die installierbare Datei entsteht als `build/libs/plot-render-1.0.2.jar`.
Benötigt werden Fabric Loader und Fabric API. Sodium und Iris sind optional.

## Zielversionen

- Minecraft 1.20.1
- Fabric API 0.92.2+1.20.1
- Sodium 0.5.11 (optionale Integration; 0.5.x-Klassenlayout)
- Iris für Minecraft 1.20.1 über dessen Vanilla-/Sodium-Renderpfade

## Lizenz

MIT, siehe `LICENSE`.
