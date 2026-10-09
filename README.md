# Selective Render

[Download Selective Render on Modrinth](https://modrinth.com/mod/selective-render)

Selective Render is a client-side Fabric mod for focusing the view on selected parts of a Minecraft world. Create block-accurate regions, hide unwanted areas, filter particular blocks, or work with a server's plot integration. It changes what your client draws and targets; it does not change world data, collision, or server chunk loading.

## Getting started

Install the JAR matching your Minecraft version together with Fabric Loader, Fabric API, and Sodium. Iris is optional. Open the in-game settings with the `#` key on a German keyboard, or open the Selective Render category in Controls to change bindings.

To make a region, mark two opposite block corners with `/sr pos1` and `/sr pos2`, then save it with `/sr save NAME`. You can also create a region from coordinates in one command, or use a cuboid selection supplied by WorldEdit. Saved regions can be enabled together, hidden, filtered, and organized independently.

## Commands

`/sr` is the short form of `/selectiverender`. Every command below works with either root. Arguments in square brackets are optional. Commands have no dedicated keybind unless a default is listed; group keybinds affect the whole group, not a single named region.

| Priority | Command | What it does | Keybind (default) |
|---|---|---|---|
| Basic | `/sr pos1` (`/sr 1`) | Set the first corner at your current block. | Unassigned |
| Basic | `/sr pos2` (`/sr 2`) | Set the opposite corner at your current block. | Unassigned |
| Basic | `/sr save NAME` (`/sr s NAME`) | Save the marked cuboid as a region and enable it. | None |
| Basic | `/sr toggle NAME` (`/sr t NAME`) | Add or remove one saved region from the render group. | None; `F9` toggles the whole render group |
| Basic | `/sr toggle` (`/sr t`) | Enable or disable the render group without changing its members. | `F9` |
| Basic | `/sr hide NAME` (`/sr h NAME`) | Add a saved region to the hide group or toggle its hidden state. | None; `F10` toggles the whole hide group |
| Basic | `/sr hide` (`/sr h`) | Enable or disable the hide group without changing its members. | `F10` |
| Create regions | `/sr create NAME [render\|hidden]` (`/sr c NAME ...`) | Create a region from the current WorldEdit cuboid selection. | None |
| Create regions | `/sr create X1 Y1 Z1 X2 Y2 Z2 NAME [render\|hidden]` (`/sr c ...`) | Create a region directly from two block corners. | None |
| Manage regions | `/sr redefine NAME` (`/sr r NAME`) | Replace a saved region's bounds with the current marked corners. | None |
| Manage regions | `/sr name OLDNAME NEWNAME` (`/sr n ...`) | Rename a saved region. | None |
| Manage regions | `/sr delete NAME` (`/sr d NAME`) | Permanently delete a saved region. | None |
| Manage regions | `/sr toggle all` (`/sr t all`, `/sr t a`) | Select all render regions, or clear the render selection if any are selected. | None; `F9` toggles the group on/off |
| Manage regions | `/sr hide all` (`/sr h all`, `/sr h a`) | Select all hide regions, or clear the hide selection if any are selected. | None; `F10` toggles the group on/off |
| Manage regions | `/sr list` (`/sr l`) | List saved render regions. | None |
| Manage regions | `/sr list hidden` (`/sr l hidden`, `/sr list h`, `/sr l h`) | List saved hide regions. | None |
| Block filters | `/sr filter NAME hide id:...` (`/sr f ...`) | Hide matching block IDs inside a region. | None; global filter toggle is unassigned |
| Block filters | `/sr filter NAME only tag:...` (`/sr f ...`) | Keep only blocks matching a block ID or tag inside a region. | None; global filter toggle is unassigned |
| Block filters | `/sr filter NAME` (`/sr f NAME`) | Show the region's current filter rules. | None |
| Block filters | `/sr filter NAME clear` (`/sr f NAME clear`) | Remove that region's filter rules. | None |
| Server plots | `/sr plot [minY] [maxY] [xzMargin]` (`/sr p ...`) | Add or remove the plot beneath you in the temporary plot group. | `Backspace` |
| Server plots | `/sr plot clear` (`/sr p clear`) | Clear the temporary plot group. | Unassigned |
| Server plots | `/sr plot save NAME [minY] [maxY] [xzMargin]` (`/sr p save` or `/sr p s ...`) | Save the current plot as a regular region and enable it. | None |
| Diagnostics | `/sr diagnose` (`/sr diag`) | Show whether performance diagnostics are recording. | None |
| Diagnostics | `/sr diagnose start [seconds]` | Record performance data; defaults to 120 seconds. | None |
| Diagnostics | `/sr diagnose mark LABEL` | Add a marker to the diagnostic log. | None |
| Diagnostics | `/sr diagnose stop` | Stop recording. | None |

The default settings key is `#` on German layouts (the apostrophe key in Minecraft's key notation). `F9` toggles rendering, `F10` toggles hiding, and `Backspace` toggles the current plot. Position, player visibility, interaction, boundary, block-filter, and clear-plot bindings are initially unassigned. All bindings can be changed under **Options → Controls → Selective Render**.

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
