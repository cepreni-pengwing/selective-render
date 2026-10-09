# Selective Render

Current stable version: **1.9.4**. Latest test version: **1.10.10**.

Selective Render is a client-side Fabric mod for Minecraft 1.20.1, 1.21.1, and 26.2 that renders
only chosen three-dimensional block regions. It is intended for builders and
PlotSquared users who want to isolate builds, hide palettes, reduce distracting
geometry, and prevent filtered terrain from contributing shader shadows.

No server installation is required for manually selected regions. The mod keeps
world state, network traffic, chunk loading, collision, and game logic unchanged;
it changes only what the client renders and can interact with.

## Requirements

- Minecraft 1.20.1 with Fabric Loader 0.15.11+, Fabric API 0.92.2+, and Sodium 0.5.x; or
- Minecraft 1.21.1 with Java 21, Fabric API 0.116.17+, and Sodium 0.8.13+
- Minecraft 26.2 with Java 25, Fabric API 0.159.0+, and Sodium 0.9.2

Download the clearly labelled JAR matching your Minecraft version. The JARs are not interchangeable.

Iris is optional and supported.

## Basic usage

`/sr` is the short form of `/selectiverender`.

```text
/sr 1
/sr 2
/sr s NAME
/sr c X1 Y1 Z1 X2 Y2 Z2 NAME [render|hidden]
/sr t NAME
/sr f NAME hide id:minecraft:stone
/sr f NAME only tag:minecraft:slabs
/sr f NAME hide tag:selectiverender:beams
/sr f NAME
/sr f NAME clear
```

The two positions are exact block corners on all three axes. Saving creates a
named cuboid and immediately enables it. Multiple render regions can be enabled
at once. `/sr t` toggles the current render group and `/sr t all` (or `/sr t a`) changes every
normal preset in the current server, world, and dimension context.

```text
/sr l
/sr r NAME
/sr n OLDNAME NEWNAME
/sr d NAME
```

The full command names `pos1`, `pos2`, `save`, `create`, `redefine`, `toggle`, `list`, `name`,
`rename`, and `delete` remain available.

**Using WorldEdit?** Select a cuboid and run `/sr c NAME` to use it directly, or
`/sr c NAME hidden` to hide it. No coordinates or SR server addon required.
WorldEditCUI is optional; the server needs to support WorldEdit selection synchronization.
If the selection is missing, select both corners again or run `/we cui`.

Filter blocks inside a region with `/sr f NAME hide id:namespace:block` or
`/sr f NAME only tag:namespace:tag`. Tab completion lists known blocks and block tags,
including Axiom tags when available. Use `tag:selectiverender:beams` for Conquest Reforged
and Architects beams, lintels, poles, and related stripped-log variants. `/sr f NAME clear` removes the region's filters.
`/sr f NAME` lists its current filters. Filters are saved with the region and do not change collision or server-side state.
Saved filters remain active when their render region is toggled off; use the unassigned filter keybind to pause them.
The hidden-region interaction setting can also disable targeting and interaction with filtered blocks.

## Hiding regions

```text
/sr h NAME
/sr l h
/sr h all  # alias: /sr h a
```

Hide presets remove selected cuboids while leaving the rest of the world visible.
They are useful for temporary block palettes, scaffolding, or unwanted structures.
Player visibility and interactions can be controlled independently. Crosshair targets, outlines,
player hitboxes, Axiom Orbit Camera, and brush targeting follow the selected modes; collision is unchanged.
The hidden-region interaction toggle also controls whether blocks removed by filters can be targeted or used.

Default keybinds are F9 for the render group, F10 for the hide group, Backspace for the current
PlotSquared region, and the physical `#`/apostrophe key for settings. Optional unassigned bindings
select positions, cycle all player visibility modes, clear temporary plots, and cycle interaction
and boundary modes, or toggle all block filters. Change bindings under Controls >
Selective Render; existing custom bindings are preserved.

Settings are also accessible through Mod Menu when installed. Boundary faces can be normal, black,
or hidden, and optional debug boxes show saved and temporary regions. Settings are preserved when updating.

Switching region rendering off restores normal interaction behavior by default, or interactions can
continue following saved regions. Virtual skylight can enter from the top, sides, both, or neither,
with separate controls for render boundaries and hidden regions.
The settings screen also controls how aggressively affected sections are refreshed.

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
Only the first plot in an empty selection automatically enables isolation. Switch it off with
`/sr t` to collect more plots while seeing the full world, then toggle it back on when ready.
The selection lasts for the current Minecraft session, including reconnects and dimension changes.
Omitted Y values use the configurable minimum (initially `-64`) and maximum `400`; a positive
margin expands X/Z and a negative one shrinks the complete outline.

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
- Custom mod renderers may require dedicated compatibility support.
- Conquest Reforged extension-toggle faces are not fully supported by black or culled boundaries.
- Breaking particles and newly dropped items can occasionally appear too dark for a short time
  beneath filtered roofs while virtual skylight updates.

Minecraft 1.20.1, 1.21.1, and 26.2 are currently supported. For requests regarding other Minecraft versions,
contact [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.
