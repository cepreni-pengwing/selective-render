# Selective Render

[Download Selective Render on Modrinth](https://modrinth.com/mod/selective-render)

Selective Render is a client-side Fabric mod for focusing the view on selected parts of a Minecraft world. Create block-accurate regions, hide unwanted areas, filter particular blocks, or work with a server's plot integration. It changes what your client draws and targets; it does not change world data, collision, or server chunk loading.

## Getting started

Install the JAR matching your Minecraft version together with Fabric Loader, Fabric API, and Sodium. Iris is optional. Open the in-game settings with its default keybind, or open the Selective Render category in Controls to change bindings.

To make a region, mark two opposite block corners with `/sr pos1` and `/sr pos2`, then save it with `/sr create NAME`. You can also create a region from coordinates in one command, or use a cuboid selection supplied by WorldEdit. Saved regions can be enabled together, hidden, filtered, and organized independently.

## Commands

`/sr` is the short form of `/selectiverender`. Every command below works with either root. Arguments in square brackets are optional. Commands have no dedicated keybind unless a default is listed; group keybinds affect the whole group, not a single named region. Commands may differ for older versions. This table lists all commands for the latest mod releases, including pre-releases.

<table>
<thead><tr><th>Alias</th><th>Expanded command</th><th>Purpose</th><th>Key</th></tr></thead>
<tbody>
<tr><td><code>/sr 1</code></td><td><code>pos1</code></td><td>Mark corner 1</td><td></td></tr>
<tr><td><code>/sr 2</code></td><td><code>pos2</code></td><td>Mark corner 2</td><td></td></tr>
<tr><td rowspan="2"><code>/sr c</code></td><td><code>create NAME [render|hidden]</code></td><td>Create from the selection source in settings; defaults to <code>render</code></td><td></td></tr>
<tr><td><code>create X1 Y1 Z1 X2 Y2 Z2 NAME [render|hidden]</code></td><td>Create from two corners; defaults to <code>render</code></td><td></td></tr>
<tr><td rowspan="3"><code>/sr t</code></td><td><code>toggle</code></td><td>Toggle render group</td><td><code>F9</code></td></tr>
<tr><td><code>toggle NAME</code></td><td>Toggle one render region</td><td></td></tr>
<tr><td><code>toggle all</code></td><td>Select all render regions or clear selection</td><td></td></tr>
<tr><td rowspan="3"><code>/sr h</code></td><td><code>hide</code></td><td>Toggle hide group</td><td><code>F10</code></td></tr>
<tr><td><code>hide NAME</code></td><td>Toggle one hidden region</td><td></td></tr>
<tr><td><code>hide all</code></td><td>Select all hide regions or clear selection</td><td></td></tr>
<tr><td rowspan="2"><code>/sr l</code></td><td><code>list</code></td><td>List render regions</td><td></td></tr>
<tr><td><code>list hidden</code></td><td>List hidden regions</td><td></td></tr>
<tr><td><code>/sr r</code></td><td><code>redefine NAME</code></td><td>Update bounds from the selection source in settings</td><td></td></tr>
<tr><td><code>/sr n</code></td><td><code>name OLDNAME NEWNAME</code></td><td>Rename region</td><td></td></tr>
<tr><td><code>/sr d</code></td><td><code>delete NAME</code></td><td>Delete saved region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr f</code></td><td><code>filter NAME</code></td><td>Show region filters</td><td></td></tr>
<tr><td><code>filter NAME hide SELECTOR</code></td><td>Hide matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME only SELECTOR</code></td><td>Keep only matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME clear</code></td><td>Clear region filters</td><td></td></tr>
<tr><td rowspan="3"><code>/sr p</code></td><td><code>plot [minY] [maxY] [xzMargin]</code></td><td>Add or remove current plot</td><td>Not Bound</td></tr>
<tr><td><code>plot clear</code> / <code>/sr p c</code></td><td>Clear temporary plots</td><td>Not Bound</td></tr>
<tr><td><code>plot save NAME [minY] [maxY] [xzMargin]</code></td><td>Save plot as a region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr diagnose</code></td><td><code>diagnose</code></td><td>Show diagnostic status</td><td></td></tr>
<tr><td><code>diagnose start [seconds]</code></td><td>Start recording (120 seconds by default)</td><td></td></tr>
<tr><td><code>diagnose mark LABEL</code></td><td>Add diagnostic marker</td><td></td></tr>
<tr><td><code>diagnose stop</code></td><td>Stop recording</td><td></td></tr>
<tr><td></td><td></td><td>Open settings</td><td><code>#</code></td></tr>
<tr><td></td><td></td><td>Toggle current plot region</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Toggle all block filters</td><td>Not Bound</td></tr>
</tbody>
</table>

Keybinds shown in the table are defaults. The settings key is `#`, hide-group toggle is `F10`, and render-group toggle is `F9`. All other keybinds are Not Bound by default. Change them under **Options → Controls → Selective Render**.

Filters accept block IDs and block tags known to the client. A built-in `selectiverender:beams` tag covers supported beam, lintel, and pole blocks from Conquest Reforged and Architects. Filtered blocks can follow the hidden-region interaction setting. Filters are visual/client-side and do not change server state or collision.

## Server plot integration

The optional [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) server add-on supplies exact plot outlines to the client. The commands above are client commands; the server add-on has no separate player command. The first plot added to an empty group enables isolation automatically. Further plots can be added or removed independently, and the temporary group survives reconnects and dimension changes during the current Minecraft session. `/sr plot save` turns a plot into a normal saved region.

Optional Y limits are inclusive. When omitted, both limits come from the Selective Render settings (defaults `-64` and `319`). A positive X/Z margin expands the plot; a negative margin shrinks it. If a margin would remove the entire plot, it is rejected. Servers using LuckPerms or another permission manager must grant `selectiverender.plot.solo` to players allowed to use the integration.

## Client-only plot grid

Use `/sr plot grid`, mark two opposite block corners of one plot with the current WorldEdit/SR selection source, then repeat the command. The first plot is saved immediately and appears in **Plots** with three editable values: Corner 1 (X Z), Corner 2 (X Z), and Road width. Enter the road width manually, or mark a directly adjacent, equally sized plot and run `/sr plot grid` a third time to calculate it. Both axes use the same road width; merged plots and irregular grids are not inferred.

The saved grid belongs to the current server and dimension. Choose **Grid** or **SRP** in Plots, edit the values, or delete the grid using its top button. Valid edits save on Enter or focus loss. Once road width is known, equivalent corners are normalized to the smallest nonnegative X/Z plot example. Existing `/sr plot`, its keybind, and `/sr plot save` use the selected source. Streets are excluded. Grid data survives reconnects and restarts in the settings file; moving that file to another device preserves multiplayer profiles when the server address and dimension match. Singleplayer save paths still require the same world assignment.

## Settings and behavior

- Player visibility combines a player group (Everyone, Nobody, Only you or Other players) with an independent region scope (Everywhere, Inside regions, Outside regions or Nowhere). Scope follows the currently rendered area; player hitboxes use the same policy. Existing player choices are preserved.
- Selection source is a single WorldEdit / SR positions toggle. New settings default to WorldEdit; existing selections are retained. Explicit coordinate commands remain independent of the source.
- The region editor lists only regions in the current dimension and groups controls into Filters, Boundaries, Interactions and Bounding boxes. Texture and Color can each inherit global settings or have a region-specific appearance, including their own texture ID, color, opacity, lighting and presets.
- Interactions can be limited to regions, allowed outside them, or left unrestricted. “Interactions while rendering is off” offers Unrestricted (default) or Follow region rules. Active filters keep their hidden-block interaction rules independently of this choice. A separate option controls interactions with hidden regions and filtered blocks, taking priority over the general interaction scope for that content.
- Boundary faces offer independent Normal, Texture and Color toggles. Normal is enabled by default; Texture replaces it, and Color overlays either surface. With both replacement layers off, Normal controls whether the original face is visible. Texture receives normal lighting, including virtual skylight; Color defaults to unlit; **Shade overlay** applies ordinary world lighting to it when enabled. Opacity from 0–100% affects only Color. Texture now queries loaded block models at the boundary, including resource-pack replacements and supported Fabric connected-texture transforms with virtual boundary neighbors. CTM queries use an isolated scratch context during terrain rendering, and model UV rotation/mirroring is preserved. The representative texture is retained as a fallback for unsupported models; multipart/compact CTM implementations are not universally reproduced. Color presets include Rainbow, which maps one hue cycle around each cuboid's horizontal perimeter, plus White and Black. Saturation, brightness and opacity remain adjustable; clicking Hue leaves Rainbow for a single color. Optional Iris hooks prevent Color from inheriting the underlying block's material classification, while shaderpack post-processing can still affect final appearance. Each region exposes its own inline appearance controls; disabled Color hides the entire palette, presets, preview and lighting controls, while disabled Texture hides its block input. Use global follows the corresponding global layer toggle. Section expansion and scroll position are remembered per region. Color edits inherit global lighting unless lighting is explicitly overridden. Transparent Color boundaries may cost more than opaque faces. Legacy Black settings become Color with black. Only block filters also hide non-player entities inside their region; player visibility stays separate. Hidden-region boundaries stay normal. Debug boxes are independent and can outline inactive regions too.
- An optional fluid-boundary setting can hide fluid faces cut by a render boundary, including lava, flowing states, waterlogged blocks, and modded fluids using the supported fluid renderer, independently of the block boundary mode.
- One shared virtual-skylight setting lets light reach render regions, hidden regions and active block filters from above, from the sides, from both, or from neither, independently of the inactive-interaction setting.
- The settings screen controls how much local section rebuilding is attempted before a full renderer reload is used.
- With no active filters or render/hide regions, rendering takes the normal no-op path. Interaction restrictions are also inactive by default; Follow region rules explicitly keeps them active for saved regions. Fresh installations do not change world rendering or interactions.
- Water, fire, in-wall overlays, and underwater fog are suppressed when the camera is inside content the mod hides.

## Compatibility and limitations

Selective Render has builds for Minecraft 1.20.1, 1.21.1, and 26.2. Use the matching JAR; these builds are not interchangeable. The 1.20.1 and 1.21.1 builds target their corresponding Java/Fabric environments, and the 26.2 build targets Java 25. See the release assets and project metadata for exact dependency requirements.

The mod does not reduce render distance, server-sent chunks, or network traffic. Selected content must already be within the normal client render distance. Distant Horizons LODs and some custom mod renderers may not be filtered. Conquest Reforged extension-toggle boundaries are not fully supported in custom/culled modes. Particle and dropped-item lighting can briefly be inaccurate under filtered roofs.

## Building and contributing

Use JDK 17 or newer and run:

```powershell
.\gradlew.bat build
```

The installable JAR is generated in `build/libs`. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change. Report issues with the requested logs and game/mod versions. Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only. See [LICENSE](LICENSE).
