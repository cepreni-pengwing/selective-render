# Selective Render

Selective Render is a client-side Fabric mod for Minecraft 1.20.1 that renders
only chosen three-dimensional block regions. It is intended for builders and
PlotSquared users who want to isolate builds, hide palettes, reduce distracting
geometry, and prevent filtered terrain from contributing shader shadows.

No server installation is required for manually selected regions. The mod keeps
world state, network traffic, chunk loading, collision, and game logic unchanged;
it changes only what the client renders and can interact with.

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.15.11 or newer
- Fabric API 0.92.2+1.20.1 or another compatible 1.20.1 build
- Sodium 0.5.x for Minecraft 1.20.1

Iris is optional and supported. Sodium `mc1.20.1-0.5.13-fabric` is tested in
game; 0.5.8 and 0.5.11 are compile-checked by CI.

## Basic usage

`/sr` is the short form of `/selectiverender`.

```text
/sr 1
/sr 2
/sr s NAME
/sr t NAME
```

The two positions are exact block corners on all three axes. Saving creates a
named cuboid and immediately enables it. Multiple render regions can be enabled
at once. `/sr t` toggles the current render group and `/sr t all` (or `/sr t a`) changes every
normal preset in the current server, world, and dimension context.

```text
/sr l
/sr r OLDNAME NEWNAME
/sr d NAME
```

The full command names `pos1`, `pos2`, `save`, `toggle`, `list`, `rename`, and
`delete` remain available.

## Hiding regions

```text
/sr h NAME
/sr l h
/sr h all  # alias: /sr h a
```

Hide presets remove selected cuboids while leaving the rest of the world visible.
They are useful for temporary block palettes, scaffolding, or unwanted structures.
Player visibility and interactions can independently be set to none, inside regions, outside
regions, or everywhere. Crosshair targets and outlines follow interaction visibility, including
Axiom Orbit Camera and brush targeting. Player hitboxes follow player visibility; collision is unchanged.

Default keybinds are F8 for the render group, F9 for the hide group, and K to cycle all player
visibility modes. Optional unassigned bindings select positions, toggle or clear temporary plots,
cycle interaction and boundary modes, and open settings. Change bindings under Controls >
Selective Render; existing custom bindings are preserved.

Settings are also accessible through Mod Menu when installed. Region-boundary faces cycle through
normal, black, and culled; hide-region boundaries always stay normal. New settings default to
players and interactions everywhere, normal boundary faces, and debug boxes off. Saved settings
are preserved when updating.

## PlotSquared integration

With the compatible [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots)
bridge installed on the server:

```text
/sr p
/sr p [minY] [maxY] [xzMargin]
/sr p clear
/sr p s NAME [minY] [maxY] [xzMargin]
```

This uses exact PlotSquared shapes, including merged or irregular plots. Visit more plots and use
`/sr p` again to add them to the same temporary view; repeat it on an active plot to remove it.
The selection lasts for the current Minecraft session, including reconnects and dimension changes.
Omitted Y values default to `-100` and `400`; a positive margin expands X/Z and a negative one
shrinks the complete outline.

When LuckPerms or another permission manager is installed, ensure every intended user or group has
`selectiverender.plot.solo`. Grant it explicitly on Fabric or when a Paper permission policy
overrides the plugin's default, for example with
`/lp user PLAYER permission set selectiverender.plot.solo true`.

## Storage and limitations

Presets are stored locally per server or world and per dimension in
`config/selectiverender/`. Config writes are atomic and keep a recoverable
`.json.bak` backup.
The JSON data can be transferred between instances, but its hashed file name depends on the server
address or absolute single-player save path and the dimension ID. If that context changes, keep the
file name SR generates for the target context and copy the old JSON contents into it while Minecraft
is closed.

- Selected content must be within the normal client render distance.
- The mod does not reduce server-sent chunks or network traffic.
- Distant Horizons LOD geometry is not filtered.
- Selective filtering can change occlusion-culling behavior at region boundaries.

Minecraft 1.20.1 is currently supported. For requests regarding other Minecraft
versions, contact [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.
