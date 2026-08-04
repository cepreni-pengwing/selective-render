# Selective Render

Selective Render is a client-side Fabric mod for Minecraft 1.20.1. It keeps loaded
chunks, network traffic, world state, and collision unchanged while removing
everything outside a saved rectangular chunk region from the render lists.

## Usage

1. Stand in the first corner chunk and run `/selectiverender pos1`.
2. Stand in the opposite corner chunk and run `/selectiverender pos2`.
3. Run `/selectiverender save` to save the inclusive rectangular region.
4. Run `/selectiverender toggle` to enable or disable the render filter.

The region and its enabled state are stored per server or single-player world
and dimension in `config/selectiverender/<sha256>.json`. The configuration is loaded
automatically when joining the world. The hashed file name prevents server
addresses from being exposed as file names.

Players are always rendered. Every other entity, block entity, and particle is
hidden outside the selected region.

## Implementation

- Vanilla sections are filtered in `WorldRenderer.addBuiltChunk` before terrain
  render lists and chunk rebuild work are created.
- Sodium 0.5.x sections are filtered through `VisibleChunkCollector` before
  render lists, draw commands, and rebuild queues are created. While the filter
  is active, graph traversal remains independent of occlusion data from
  unrendered outer sections, allowing the plot to remain visible from outside.
- Iris normal and shadow passes use the already filtered Vanilla or Sodium
  terrain lists, so geometry outside the region never enters a shadow pass.
- Entities, block entities, and particle geometry use separate render filters.

The mod does not change render distance, server packets, chunk loading, game
logic, or collision. Sodium support is optional and its mixins are skipped when
Sodium is not installed. The implementation does not use reflection.

The selected region must still be within the normal Minecraft render distance.

## Building

Requirements: JDK 17 or newer and internet access for the first build.

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The installable file is generated at `build/libs/selective-render-1.0.2.jar`.
Fabric Loader and Fabric API are required. Sodium and Iris are optional.

## Target versions

- Minecraft 1.20.1
- Fabric API 0.92.2+1.20.1
- Sodium 0.5.x
- Iris for Minecraft 1.20.1

## License

MIT. See `LICENSE`.
