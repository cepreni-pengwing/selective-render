# Selective Render

[Download Selective Render on Modrinth](https://modrinth.com/mod/selective-render)

Selective Render is a client-side Fabric mod for focusing the view on selected parts of a Minecraft world. Create block-accurate regions, hide unwanted areas, filter particular blocks, or work with a server's plot integration. It changes what your client draws and targets; it does not change world data, collision, or server chunk loading.

## Getting started

Install the JAR matching your Minecraft version together with Fabric Loader, Fabric API, and Sodium. Iris is optional. Open the in-game settings with its default keybind, or open the Selective Render category in Controls to change bindings.

To make a region, mark two opposite block corners with `/sr pos1` and `/sr pos2`, then save it with `/sr save NAME`. You can also create a region from coordinates in one command, or use a cuboid selection supplied by WorldEdit. Saved regions can be enabled together, hidden, filtered, and organized independently.

## Commands

`/sr` is the short form of `/selectiverender`. Every command below works with either root. Arguments in square brackets are optional. Commands have no dedicated keybind unless a default is listed; group keybinds affect the whole group, not a single named region.

<table>
<thead><tr><th>Alias</th><th>Expanded command</th><th>Purpose</th><th>Key</th></tr></thead>
<tbody>
<tr><td><code>/sr 1</code></td><td><code>pos1</code></td><td>Mark corner 1</td><td>Not Bound</td></tr>
<tr><td><code>/sr 2</code></td><td><code>pos2</code></td><td>Mark corner 2</td><td>Not Bound</td></tr>
<tr><td><code>/sr s</code></td><td><code>save NAME</code></td><td>Save and activate region</td><td></td></tr>
<tr><td rowspan="2"><code>/sr c</code></td><td><code>create NAME [render|hidden]</code></td><td>Create from the WorldEdit selection; defaults to <code>render</code></td><td></td></tr>
<tr><td><code>create X1 Y1 Z1 X2 Y2 Z2 NAME [render|hidden]</code></td><td>Create from two corners; defaults to <code>render</code></td><td></td></tr>
<tr><td rowspan="3"><code>/sr t</code></td><td><code>toggle</code></td><td>Toggle render group</td><td><code>F9</code></td></tr>
<tr><td><code>toggle NAME</code></td><td>Toggle one render region</td><td></td></tr>
<tr><td><code>toggle all</code> (<code>a</code> alias)</td><td>Select all render regions or clear selection</td><td></td></tr>
<tr><td rowspan="3"><code>/sr h</code></td><td><code>hide</code></td><td>Toggle hide group</td><td><code>F10</code></td></tr>
<tr><td><code>hide NAME</code></td><td>Toggle one hidden region</td><td></td></tr>
<tr><td><code>hide all</code> (<code>a</code> alias)</td><td>Select all hide regions or clear selection</td><td></td></tr>
<tr><td rowspan="2"><code>/sr l</code></td><td><code>list</code></td><td>List render regions</td><td></td></tr>
<tr><td><code>list hidden</code> (<code>h</code> alias)</td><td>List hidden regions</td><td></td></tr>
<tr><td><code>/sr r</code></td><td><code>redefine NAME</code></td><td>Update bounds from marked corners</td><td></td></tr>
<tr><td><code>/sr n</code></td><td><code>name OLDNAME NEWNAME</code></td><td>Rename region</td><td></td></tr>
<tr><td><code>/sr d</code></td><td><code>delete NAME</code></td><td>Delete saved region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr f</code></td><td><code>filter NAME</code></td><td>Show region filters</td><td></td></tr>
<tr><td><code>filter NAME hide SELECTOR</code></td><td>Hide matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME only SELECTOR</code></td><td>Keep only matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME clear</code></td><td>Clear region filters</td><td></td></tr>
<tr><td rowspan="3"><code>/sr p</code></td><td><code>plot [minY] [maxY] [xzMargin]</code></td><td>Add or remove current plot</td><td>Not Bound</td></tr>
<tr><td><code>plot clear</code></td><td>Clear temporary plots</td><td>Not Bound</td></tr>
<tr><td><code>plot save NAME [minY] [maxY] [xzMargin]</code> (<code>s</code> alias)</td><td>Save plot as a region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr diag</code></td><td><code>diagnose</code></td><td>Show diagnostic status</td><td></td></tr>
<tr><td><code>diagnose start [seconds]</code></td><td>Start recording (120 seconds by default)</td><td></td></tr>
<tr><td><code>diagnose mark LABEL</code></td><td>Add diagnostic marker</td><td></td></tr>
<tr><td><code>diagnose stop</code></td><td>Stop recording</td><td></td></tr>
<tr><td></td><td></td><td>Clear temporary plots</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Cycle boundary faces</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Cycle interaction mode</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Cycle player visibility</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Open settings</td><td><code>#</code></td></tr>
<tr><td></td><td></td><td>Set selection position 1</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Set selection position 2</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Toggle current plot region</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Toggle all block filters</td><td>Not Bound</td></tr>
</tbody>
</table>

Keybinds shown in the table are defaults. The settings key is `#`, hide-group toggle is `F10`, and render-group toggle is `F9`. All other keybinds are Not Bound by default. Change them under **Options → Controls → Selective Render**.

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
