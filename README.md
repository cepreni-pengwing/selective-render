# Selective Render

[Download Selective Render on Modrinth](https://modrinth.com/mod/selective-render)

Selective Render is a client-side Fabric mod for focusing the view on selected parts of a Minecraft world. Create block-accurate regions, hide unwanted areas, filter particular blocks, or work with a server's plot integration. It changes what your client draws and targets; it does not change world data, collision, or server chunk loading.

## Getting started

Install the JAR matching your Minecraft version together with Fabric Loader, Fabric API, and Sodium. Iris is optional. Open the in-game settings with its default keybind, or open the Selective Render category in Controls to change bindings.

To make a region, mark two opposite block corners with `/sr pos1` and `/sr pos2`, then save it with `/sr save NAME`. You can also create a region from coordinates in one command, or use a cuboid selection supplied by WorldEdit. Saved regions can be enabled together, hidden, filtered, and organized independently.

## Commands

`/sr` is the short form of `/selectiverender`. Every command below works with either root. Arguments in square brackets are optional. Commands have no dedicated keybind unless a default is listed; group keybinds affect the whole group, not a single named region.

| Alias | Expanded command | Purpose | Key |
|---|---|---|---|
| `/sr 1` | `pos1` | Mark corner 1. | Unassigned |
| `/sr 2` | `pos2` | Mark corner 2. | Unassigned |
| `/sr s` | `save NAME` | Save and activate region. |  |
| `/sr c` | `create NAME [mode]` | Use WorldEdit selection. Mode: `render` or `hidden`. |  |
| `/sr c` | <small>`create X1 Y1 Z1 X2 Y2 Z2 NAME [mode]`</small> | Create from two corners. Mode: `render` or `hidden`. |  |
| `/sr t` | `toggle` | Toggle render group. | `F9` |
| `/sr t` | `toggle NAME` | Toggle one render region. |  |
| `/sr t` | `toggle all` (`a` alias) | Select all render regions or clear selection. |  |
| `/sr h` | `hide` | Toggle hide group. | `F10` |
| `/sr h` | `hide NAME` | Toggle one hidden region. |  |
| `/sr h` | `hide all` (`a` alias) | Select all hide regions or clear selection. |  |
| `/sr l` | `list` | List render regions. |  |
| `/sr l` | `list hidden` (`h` alias) | List hidden regions. |  |
| `/sr r` | `redefine NAME` | Update bounds from marked corners. |  |
| `/sr n` | `name OLDNAME NEWNAME` | Rename region. |  |
| `/sr d` | `delete NAME` | Delete saved region. |  |
| `/sr f` | `filter NAME` | Show region filters. |  |
| `/sr f` | `filter NAME hide SELECTOR` | Hide matches by block ID or tag. |  |
| `/sr f` | `filter NAME only SELECTOR` | Keep only matches by block ID or tag. |  |
| `/sr f` | `filter NAME clear` | Clear region filters. |  |
| `/sr p` | <small>`plot [minY] [maxY] [xzMargin]`</small> | Add or remove current plot. | `Backspace` |
| `/sr p` | `plot clear` | Clear temporary plots. | Unassigned |
| `/sr p` | <small>`plot save NAME [minY] [maxY] [xzMargin]` (`s` alias)</small> | Save plot as a region. |  |
| `/sr diag` | `diagnose` | Show diagnostic status. |  |
| `/sr diag` | `diagnose start [seconds]` | Start recording (120 seconds by default). |  |
| `/sr diag` | `diagnose mark LABEL` | Add diagnostic marker. |  |
| `/sr diag` | `diagnose stop` | Stop recording. |  |

The settings key defaults to `#`. Keybinds shown in the table are defaults. `F9` toggles rendering, `F10` toggles hiding, and `Backspace` toggles the current plot. Position, player visibility, interaction, boundary, block-filter, and clear-plot bindings are initially unassigned. All bindings can be changed under **Options → Controls → Selective Render**.

Filters accept block IDs and block tags known to the client. A built-in `selectiverender:beams` tag covers supported beam, lintel, and pole blocks from Conquest Reforged and Architects. Filtered blocks can follow the hidden-region interaction setting. Filters are visual/client-side and do not change server state or collision.

## Server plot integration

The optional [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) server add-on supplies exact plot outlines to the client. The commands above are client commands; the server add-on has no separate player command. The first plot added to an empty group enables isolation automatically. Further plots can be added or removed independently, and the temporary group survives reconnects and dimension changes during the current Minecraft session. `/sr plot save` turns a plot into a normal saved region.

Optional Y limits are inclusive. When omitted, the minimum comes from the Selective Render setting (default `-64`) and the maximum is `400`. A positive X/Z margin expands the plot; a negative margin shrinks it. If a margin would remove the entire plot, it is rejected. Servers using LuckPerms or another permission manager must grant `selectiverender.plot.solo` to players allowed to use the integration.

## Settings and behavior

- Player visibility has six modes, including local-player-only and everyone-except-local-player; player hitboxes follow the same policy.
- Interactions can be limited to regions, allowed outside them, or left unrestricted. A separate option controls interactions with hidden regions and filtered blocks.
- Boundary faces can be normal, black, or culled. Hidden-region boundaries stay normal. Debug boxes are independent and can outline inactive regions too.
- Virtual skylight can reach render and hidden regions from above, from the sides, from both, or from neither, with separate settings for hidden regions.
- The settings screen controls how much local section rebuilding is attempted before a full renderer reload is used.
- When filtering is inactive and no render or hide regions are active, the renderer and interaction hooks take their normal no-op paths.
- Water, fire, in-wall overlays, and underwater fog are suppressed when the camera is inside content the mod hides.

## Compatibility and limitations

Selective Render has builds for Minecraft 1.20.1, 1.21.1, and 26.2. Use the matching JAR; these builds are not interchangeable. The 1.20.1 and 1.21.1 builds target their corresponding Java/Fabric environments, and the 26.2 build targets Java 25. See the release assets and project metadata for exact dependency requirements.

The mod does not reduce render distance, server-sent chunks, or network traffic. Selected content must already be within the normal client render distance. Distant Horizons LODs and some custom mod renderers may not be filtered. Conquest Reforged extension-toggle boundaries are not fully supported in black/culled modes. Particle and dropped-item lighting can briefly be inaccurate under filtered roofs.

## Building and contributing

Use JDK 17 or newer and run:

```powershell
.\gradlew.bat build
```

The installable JAR is generated in `build/libs`. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change. Report issues with the requested logs and game/mod versions. Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only. See [LICENSE](LICENSE).
