# Selective Render

<p align="center">
  <img src="docs/images/selective-render-on.png" width="49%" alt="Selective Render ON">
  <img src="docs/images/selective-render-off.png" width="49%" alt="Selective Render OFF">
</p>

Selective Render is a client-side Fabric mod for Minecraft 1.20.1. It keeps loaded
chunks, network traffic, world state, and collision unchanged while removing
everything outside selected three-dimensional block regions from the render lists.
This prevents hidden buildings and terrain from contributing geometry, lighting,
or shader shadows outside the selected area. Players remain visible everywhere;
all other entities, block entities, particles, block models, and fluids are
restricted to the active regions.

Version 1.3 adds a separate hide group for temporarily removing named areas such
as floating block palettes. Hide regions take priority over the normal render
group and can be controlled individually or all at once.

## Usage

`/sr` is the short alias for `/selectiverender`; both command names provide the
same features.

1. Stand at one corner of the desired cuboid and run `/sr pos1`.
2. Stand at the diagonally opposite corner and run `/sr pos2`.
3. Run `/sr s NAME` to save the inclusive cuboid as a named preset.
4. Run `/sr t NAME` to add it to the render group. Add any other presets the same way.
5. Run `/sr t` to enable or disable every region in the render group at once.

Both positions use whole-block X, Y, and Z coordinates. Make sure the two corners
cover the full width, height, and depth you want to render. For example, one
position can be the lower north-west corner and the other the upper south-east
corner. The order of `pos1` and `pos2` does not matter, and both boundary blocks
are included.

Available short commands:

```text
/sr pos1
/sr pos2
/sr s NAME
/sr t NAME
/sr h NAME
/sr h
/sr d NAME
/sr list
/sr list h
```

- `/sr s NAME` saves the current selection. A name is always required.
- `/sr t NAME` adds a preset to the render group or removes it again.
- `/sr t` enables or disables the entire render group while preserving its members.
- `/sr h NAME` adds a preset to the hide group or removes it again.
- `/sr h` enables or disables the entire hide group while preserving its members.
- `/sr d NAME` permanently deletes a preset.
- `/sr list` displays regular presets on separate lines with status and a corner coordinate.
- `/sr list h` or `/sr list hidden` exclusively displays hide-group regions in the same format.

The long `save`, `toggle`, `hide`, and `delete` subcommands remain available as
`/sr save NAME`, `/sr toggle NAME`, `/sr hide NAME`, and `/sr delete NAME`.

All regions in the enabled render group are combined. A block is rendered when
it is inside at least one of them, so separate areas can be visible at the same
time. Active hide regions are then subtracted from that result. When the normal
render group is disabled, the hide group can remove regions from the full world.

The default keybind for toggling the entire render group is the physical
minus key, which is the `ß` key on German keyboard layouts. It can be reassigned
under Minecraft's Controls settings in the Selective Render category.

The hide-group keybind is the physical equals key, which is the `´` key directly
to the right of `ß` on German keyboard layouts. It can also be reassigned.

Presets, render-group membership, and the group's enabled state are stored per server or single-player world
and dimension in `config/selectiverender/<sha256>.json`. The configuration is loaded
automatically when joining the world. The hashed file name prevents server
addresses from being exposed as file names.

Players are always rendered. Every other entity, block entity, and particle is
hidden outside the combined active regions.

## Implementation

- Vanilla sections are filtered in `WorldRenderer.addBuiltChunk` before terrain
  render lists and chunk rebuild work are created. Boundary chunks are retained,
  then individual block models and fluids are filtered by X/Y/Z block position.
- Sodium 0.5.x sections are filtered through `VisibleChunkCollector` before
  render lists, draw commands, and rebuild queues are created. While the filter
  is active, graph traversal remains independent of occlusion data from
  unrendered outer sections, allowing the region to remain visible from outside.
  Sodium's render-only world slice exposes blocks outside the cuboid as air, so
  standard and custom-rendered terrain is clipped at the exact block boundaries.
  Light samples beyond those boundaries use unobstructed sky light, preventing
  hidden terrain from darkening newly exposed cut faces. Vertical skylight is
  recalculated against occluding blocks inside the selected Y range, so roofs
  above the cuboid cannot leave baked darkness behind.
- Iris normal and shadow passes use the already filtered Vanilla or Sodium
  terrain lists, so geometry outside the region never enters a shadow pass.
- Entities, block entities, and particle geometry use separate render filters.

The mod does not change render distance, server packets, chunk loading, game
logic, or collision. Sodium support is optional and its mixins are skipped when
Sodium is not installed. The implementation does not use reflection.

The selected regions must still be within the normal Minecraft render distance.
All region boundaries use inclusive whole-block coordinates. Existing horizontal
presets are migrated without a vertical limit so their previous behavior is kept.

## Building

Requirements: JDK 17 or newer and internet access for the first build.

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The installable file is generated at `build/libs/selective-render-1.3.1.jar`.
Fabric Loader and Fabric API are required. Sodium and Iris are optional.

## Target versions

- Minecraft 1.20.1
- Fabric API 0.92.2+1.20.1
- Sodium 0.5.x
- Iris for Minecraft 1.20.1

## License

MIT. See `LICENSE`.
