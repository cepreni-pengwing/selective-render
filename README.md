# Selective Render

[Modrinth](https://modrinth.com/mod/selective-render) · [Releases](https://github.com/cepreni-pengwing/selective-render/releases) · [Changelog](CHANGELOG.md)

Client-side Fabric mod that shows only the parts of a world you choose. Render selected regions, hide others, or filter blocks by ID or tag. It only changes what your client draws and targets, never the world itself.

## Requirements

| Minecraft | Java | Notes |
| --- | --- | --- |
| 1.20.1 | 17 | |
| 1.21.1 | 21 | |
| 26.1 / 26.1.1 / 26.1.2 | 25 | One JAR for Sodium 0.8.9 and 0.9.2 |
| 26.2, 26.3 | 25 | |

Required: Fabric Loader, Fabric API, Sodium. Optional: Iris.

## Quick start

1. Select a cuboid with WorldEdit, or mark corners with `/sr pos1` and `/sr pos2`.
2. Save it with `/sr create NAME`.
3. Press `F9` to show only your render regions, `F10` to toggle hidden regions.
4. Press `#` to open the settings.

## Commands

`/sr` is short for `/selectiverender`. Arguments in brackets are optional. Keybinds can be changed under **Options → Controls → Selective Render**.

<table>
<thead><tr><th>Alias</th><th>Command</th><th>Action</th><th>Key</th></tr></thead>
<tbody>
<tr><td><code>/sr&nbsp;1</code></td><td><code>pos1</code></td><td>Mark corner 1 (SR positions only)</td><td></td></tr>
<tr><td><code>/sr&nbsp;2</code></td><td><code>pos2</code></td><td>Mark corner 2 (SR positions only)</td><td></td></tr>
<tr><td rowspan="2"><code>/sr&nbsp;c</code></td><td><code>create NAME [render|hidden]</code></td><td>Create region from selection</td><td></td></tr>
<tr><td><code>create X1 Y1 Z1 X2 Y2 Z2 NAME [render|hidden]</code></td><td>Create region from coordinates</td><td></td></tr>
<tr><td rowspan="3"><code>/sr&nbsp;t</code></td><td><code>toggle</code></td><td>Toggle render group</td><td><code>F9</code></td></tr>
<tr><td><code>toggle NAME</code></td><td>Toggle one render region</td><td></td></tr>
<tr><td><code>toggle all</code> (<code>a</code>)</td><td>Select all render regions or clear</td><td></td></tr>
<tr><td rowspan="3"><code>/sr&nbsp;h</code></td><td><code>hide</code></td><td>Toggle hide group</td><td><code>F10</code></td></tr>
<tr><td><code>hide NAME</code></td><td>Toggle one hidden region</td><td></td></tr>
<tr><td><code>hide all</code> (<code>a</code>)</td><td>Select all hidden regions or clear</td><td></td></tr>
<tr><td rowspan="2"><code>/sr&nbsp;l</code></td><td><code>list</code></td><td>List render regions</td><td></td></tr>
<tr><td><code>list hidden</code> (<code>h</code>)</td><td>List hidden regions</td><td></td></tr>
<tr><td><code>/sr&nbsp;r</code></td><td><code>redefine NAME</code></td><td>Update bounds from selection</td><td></td></tr>
<tr><td><code>/sr&nbsp;n</code></td><td><code>name OLD NEW</code></td><td>Rename region</td><td></td></tr>
<tr><td><code>/sr&nbsp;d</code></td><td><code>delete NAME</code></td><td>Delete region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr&nbsp;f</code></td><td><code>filter NAME</code></td><td>Show region filters</td><td></td></tr>
<tr><td><code>filter NAME hide SELECTOR</code></td><td>Hide blocks by ID or tag</td><td></td></tr>
<tr><td><code>filter NAME only SELECTOR</code></td><td>Show only blocks by ID or tag</td><td></td></tr>
<tr><td><code>filter NAME clear</code></td><td>Clear region filters</td><td></td></tr>
<tr><td rowspan="4"><code>/sr&nbsp;p</code></td><td><code>plot [minY] [maxY] [xzMargin]</code></td><td>Add or remove current plot</td><td>Not bound</td></tr>
<tr><td><code>plot clear</code> (<code>c</code>)</td><td>Clear temporary plots</td><td>Not bound</td></tr>
<tr><td><code>plot save NAME [minY] [maxY] [xzMargin]</code> (<code>s</code>)</td><td>Save plot as region</td><td></td></tr>
<tr><td><code>plot grid</code></td><td>Set up client-only plot grid</td><td></td></tr>
<tr><td rowspan="4"><code>/sr&nbsp;diagnose</code></td><td><code>diagnose</code></td><td>Show diagnostic status</td><td></td></tr>
<tr><td><code>diagnose start [seconds]</code></td><td>Record diagnostics (default 120 s)</td><td></td></tr>
<tr><td><code>diagnose mark LABEL</code></td><td>Add marker</td><td></td></tr>
<tr><td><code>diagnose stop</code></td><td>Stop recording</td><td></td></tr>
<tr><td></td><td></td><td>Open settings</td><td><code>#</code> (<code>'</code> on US)</td></tr>
<tr><td></td><td></td><td>Toggle all block filters</td><td>Not bound</td></tr>
</tbody>
</table>

## Filters

- Accept block IDs and tags, e.g. `minecraft:oak_log` or `#minecraft:logs`.
- `hide` removes matches, `only` keeps nothing but matches.
- Purely visual: collision and server state stay unchanged.

## Plots

- `/sr plot` adds or removes the plot you are standing on. The first plot enables isolation automatically.
- `/sr plot save NAME` turns a plot into a normal region.
- Plot outlines come from one of two sources, chosen in the **Plots** tab:
  - **SRP:** exact outlines from the [Selective Render Plots](https://github.com/cepreni-pengwing/selective-render-plots) server add-on.
  - **Grid:** no server needed. Run `/sr plot grid`, mark one plot, run it again, then set the road width in **Plots**.
- Y limits default to `-64` and `319`. A positive margin expands the plot, a negative one shrinks it.

## Settings

| Section | What it controls |
| --- | --- |
| Boundaries | Cut faces: Normal (default), Texture, Color with opacity, lighting and presets |
| Players | Who is visible (Everyone, Nobody, Only you, Others) and where |
| Interactions | Where you can break and place blocks (default: inside regions) |
| Skylight | Lets light reach hidden areas from above, the sides, both or neither |
| Fluids | Optionally hide water and lava faces at region edges |

Every region can use the global boundary style or its own.

## Limitations

- Does not reduce render distance, server chunks or network traffic.
- Distant Horizons LODs and some custom renderers are not filtered.
- Transparent color boundaries cost more performance than opaque ones.

## Building

Requires Java 21 or newer. Toolchains for each target are downloaded automatically.

```powershell
.\gradlew.bat build                # all targets
.\gradlew.bat :mc1.20.1:build      # one target
.\gradlew.bat :mc26.3:runClient    # development client
```

JARs end up in `versions/<target>/build/libs`. See [CONTRIBUTING.md](CONTRIBUTING.md). Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com)

Licensed under GPL-3.0-only.
