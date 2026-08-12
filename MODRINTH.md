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
at once. `/sr t` toggles the current render group and `/sr t all` changes every
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
/sr h all
```

Hide presets remove selected cuboids while leaving the rest of the world visible.
They are useful for temporary block palettes, scaffolding, or unwanted structures.
Filtered blocks cannot be broken, used, placed against, or picked, but collision
is intentionally unchanged. Players always remain visible.

Default keybinds are F8 for the render group and F9 for the hide group. Position
selection and PlotSquared toggling are unassigned by default. All bindings can be
changed under Controls > Selective Render.

## PlotSquared integration

With a compatible Selective Render Plots bridge installed on the server:

```text
/sr p
/sr p minY maxY [xzMargin]
/sr p s NAME minY maxY [xzMargin]
```

This uses the exact PlotSquared region, including merged or irregular plots. The
margin is optional and expands X/Z client-side.

## Storage and limitations

Presets are stored locally per server or world and per dimension in
`config/selective-render/`. Config writes are atomic and keep a recoverable
`.json.bak` backup.

- Selected content must be within the normal client render distance.
- The mod does not reduce server-sent chunks or network traffic.
- Distant Horizons LOD geometry is not filtered.
- Selective filtering can change occlusion-culling behavior at region boundaries.

Minecraft 1.20.1 is currently supported. For requests regarding other Minecraft
versions, contact `cepreni` on Discord.

Licensed under GPL-3.0-only.
