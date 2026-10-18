# Selective Render

Show only the parts of your world you choose. Render selected regions, hide others, or filter blocks by ID or tag. Client-side only, nothing changes on the server.

**Requires:** Fabric API, Sodium · **Optional:** Iris

## Commands

`/sr` = `/selectiverender`. Brackets are optional. Keys can be changed under **Controls → Selective Render**.

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
<tr><td></td><td></td><td>Open settings</td><td><code>'</code></td></tr>
<tr><td></td><td></td><td>Toggle all block filters</td><td>Not bound</td></tr>
</tbody>
</table>

## Highlights

- Regions from WorldEdit or `/sr pos1` / `/sr pos2`
- Boundary faces: original, texture or color with rainbow preset
- Per-region or global boundary style
- Block filters per region or global, with word search for IDs and tags
- Plot isolation via [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) or a client-only grid
- Can improve FPS when large areas are hidden

More: [GitHub](https://github.com/cepreni-pengwing/selective-render#readme) · Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com)
