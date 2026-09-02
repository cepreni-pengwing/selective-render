# Selective Render

[Download Selective Render on Modrinth](https://modrinth.com/mod/selective-render)

<p align="center">
  <img src="docs/images/selective-render-on.png" width="49%" alt="Selective Render ON">
  <img src="docs/images/selective-render-off.png" width="49%" alt="Selective Render OFF">
</p>

Selective Render is a client-side Fabric mod for Minecraft 1.20.1. It keeps loaded
chunks, network traffic, world state, and collision unchanged while removing
everything outside selected three-dimensional block regions from the render lists.
This prevents hidden buildings and terrain from contributing geometry, lighting,
or shader shadows outside the selected area. Player visibility is configurable;
other entities, block entities, particles, block models, and fluids are
restricted to the active regions.

For requests regarding support for other Minecraft versions, contact [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

## Usage

`/sr` is the short alias for `/selectiverender`; both command names provide the
same features.

1. Stand at one corner of the desired cuboid and run `/sr pos1`.
2. Stand at the diagonally opposite corner and run `/sr pos2`.
3. Run `/sr s NAME` to save the inclusive cuboid and activate it immediately.
4. Save additional regions or use `/sr t NAME` to add existing presets to the render group.
5. Run `/sr t` to enable or disable the complete render group at once.

Both positions use whole-block X, Y, and Z coordinates. Make sure the two corners
cover the full width, height, and depth you want to render. For example, one
position can be the lower north-west corner and the other the upper south-east
corner. The order of `pos1` and `pos2` does not matter, and both boundary blocks
are included.

Available short commands:

```text
/sr pos1  # alias: /sr 1
/sr pos2  # alias: /sr 2
/sr s NAME
/sr t NAME
/sr t all  # alias: /sr t a
/sr h NAME
/sr h
/sr h all  # alias: /sr h a
/sr d NAME
/sr r OLDNAME NEWNAME
/sr list
/sr list h
/sr l h
```

- `/sr s NAME` saves the current selection and immediately activates it. A name
  is always required and must not already exist.
- `/sr t NAME` toggles a preset in the render context. Using it on a hide preset
  moves that preset back to the regular render context.
- `/sr t` enables or disables the entire render group while preserving its members.
- `/sr t all` or `/sr t a` deselects every regular preset when any are selected; when none are
  selected, it selects all regular presets.
- Global render toggles and the render keybind use a HUD overlay instead of chat.
- `/sr h NAME` registers a preset in the hide context and toggles its selected state.
- `/sr h` enables or disables the entire hide group while preserving its members.
- `/sr h all` or `/sr h a` deselects every hide preset when any are selected; when none are
  selected, it selects all registered hide presets.
  Global hide toggles and the hide keybind use a HUD overlay instead of chat.
- `/sr d NAME` permanently deletes a preset.
- `/sr r OLDNAME NEWNAME` renames a preset while preserving its group memberships.
- `/sr list` displays regular presets on separate lines with status and a corner coordinate.
- `/sr list h`, `/sr list hidden`, `/sr l h`, or `/sr l hidden` exclusively displays hide-group regions in the same format.

The long `save`, `toggle`, `hide`, `delete`, and `rename` subcommands remain available as
`/sr save NAME`, `/sr toggle NAME`, `/sr hide NAME`, `/sr delete NAME`, and
`/sr rename OLDNAME NEWNAME`.

All regions in the enabled render group are combined. A block is rendered when
it is inside at least one of them, so separate areas can be visible at the same
time. Active hide regions are then subtracted from that result. When the normal
render group is disabled, the hide group can remove regions from the full world.

Default keybinds:

- `F8`: toggle the render group
- `F9`: toggle the hide group
- `K`: cycle player visibility through everywhere, none, inside regions, and outside regions
- Unassigned: set Pos1, set Pos2, toggle the current PlotSquared region, clear temporary plots
  (`/sr p clear`), cycle interactions, cycle boundary faces, and open settings

All keybinds can be reassigned in Minecraft's Controls settings under the
Selective Render category.
Existing custom bindings are preserved. The settings screen is also available through Mod Menu
when installed. Keybinds and the settings screen change the same options.

Preset arguments support tab completion for toggle, hide, delete, and rename
commands. Chat feedback uses a compact `SR:` prefix; list entries are grouped
under a single header without repeating the prefix on every line.
The preset names `all` and `a` are reserved for group commands.

## PlotSquared integration

Servers running the optional [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots)
can provide their exact PlotSquared regions, including merged and non-rectangular plots.
Plot integration is part of the normal Selective Render command tree:

- `/selectiverender plot` or `/sr plot` adds the plot under the player to temporary isolation using
  the default Y range `-100` to `400`. Use it again on that plot to remove only that plot.
- `/sr p [minY] [maxY] [xzMargin]` does the same with inclusive vertical bounds. A positive margin
  expands the outline; a negative margin shrinks the complete plot shape.
- `/sr p clear` clears all temporarily selected plots.
- The first plot in an empty temporary selection enables isolation. After switching it off with
  `/sr t`, you can add more plots without switching it back on; `/sr t` then shows the whole selection.
- `/selectiverender plot save NAME [minY] [maxY] [xzMargin]` permanently saves the exact plot shape as one
  normal preset and immediately activates it. The Y boundaries are inclusive, and the X/Z margin
  adjusts the complete PlotSquared shape without creating seams between merged parts.

`p` is the short alias for `plot`, and `s` is the short alias for `save`, so
`/sr p s NAME minY maxY xzMargin` is equivalent. Omitted Y values default to `-100` and `400`;
omitting `xzMargin` preserves the exact PlotSquared X/Z bounds.

Plot mode is temporary for the current Minecraft session and is remembered across reconnects and
dimension changes for the same server/world and dimension. It does not alter saved presets, and active hide regions
continue to be subtracted from the plot regions. A saved merged or irregular plot
appears as one entry in `/sr list`, even though it contains multiple internal cuboids.

Servers using LuckPerms or another permission manager must ensure that users or groups allowed to
use SRP have `selectiverender.plot.solo`. Grant it explicitly on Fabric or whenever a Paper
permission policy overrides the plugin's default. For example:

```text
/lp user PLAYER permission set selectiverender.plot.solo true
```

Presets, render-group membership, and the group's enabled state are stored per server or single-player world
and dimension in `config/selectiverender/<sha256>.json`. The configuration is loaded
automatically when joining or changing dimensions. The hashed file name prevents server
addresses from being exposed as file names.
The JSON contents are portable, but the file name is derived from the server address or absolute
single-player save path together with the dimension ID. Between instances, the same multiplayer
address and dimension use the same name. For a different address, save path, or dimension, let SR
create the target context's file, close Minecraft, then replace its JSON contents with the copied
configuration while keeping the target-generated file name.
Writes are atomic and preserve the previous file as a `.json.bak` backup. If the
primary file is damaged, Selective Render attempts to recover the latest valid
backup automatically.

Players can be rendered nowhere, inside regions, outside regions, or everywhere. Their debug
hitboxes follow the same setting. Every other entity, block entity, and particle is hidden outside
the combined active regions.

The settings screen cycles block faces directly adjacent to invisible space through normal exposed
cut faces, fully opaque black faces, and culled faces. Boundaries created by hide regions always
remain normal. Region wireframe boxes remain available as a separate off/on debug option.

Interactions can be allowed nowhere, inside regions, outside regions, or everywhere. This covers
block breaking and use, placement, entity attacks and use, pick block, and the matching client
raycasts. Vanilla crosshair targets and outlines follow the same mode, and Axiom's Orbit Camera
and brush raycasts are supported. Collision is unchanged.

New settings default to players and interactions everywhere, normal boundary faces, and debug
boxes off. Updating the mod preserves your saved settings.

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
  Player, entity, and block-entity lightmaps use the same shape-aware virtual skylight as terrain,
  preventing mismatched brightness below filtered roofs and around partial blocks. Compact cached
  section results are invalidated only where block or chunk changes can affect them.
- Region changes rebuild only intersecting 16 x 16 x 16 render sections plus the
  virtual-light influence area. Large updates automatically fall back to a full
  renderer reload.
- With no render or hide regions active, hot render and lighting hooks immediately use their
  normal game paths; unrestricted player and interaction settings do the same.

The mod does not change render distance, server packets, chunk loading, game
logic, or collision. The implementation does not use reflection.

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

The installable file is generated in `build/libs`.
Fabric Loader, Fabric API, and Sodium are required. Iris is optional.

## Target versions

- Minecraft 1.20.1
- Fabric API 0.92.2+1.20.1 or newer for Minecraft 1.20.1
- Sodium 0.5.13: build-compatible and tested in game
- Sodium 0.5.8 and 0.5.11: compile-checked by CI, but not claimed as fully tested in game
- Iris for Minecraft 1.20.1

## Known limitations

- Selective Render does not change render distance, chunk loading, or server network traffic.
- Selected regions must already be inside the normal client render distance.
- Selective filtering deliberately changes which sections participate in occlusion culling.
- Very large or numerous simultaneous region changes can still increase section
  rebuild and virtual-light work while the new visibility state is applied.
- Distant Horizons LOD geometry is not filtered outside selected regions.

## Support and contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change. Use the GitHub issue forms for
crashes, rendering bugs, and compatibility reports, and include the requested logs and versions.
Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Release history is maintained in [CHANGELOG.md](CHANGELOG.md).

## License

GNU General Public License v3.0 only. See `LICENSE`.
