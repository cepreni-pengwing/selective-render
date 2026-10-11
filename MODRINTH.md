# Selective Render

Selective Render is a client-side Fabric mod that lets you create block-accurate regions anywhere in a Minecraft world and choose what stays visible. Render selected areas, hide regions you do not want to see, or filter individual block types by their IDs or tags. Hiding large parts of the world can also improve FPS, depending on what is hidden and your setup. An optional setting can also hide water along region edges, including waterlogged blocks. It changes what you see and target, not the world itself.

## Requirements

Fabric Loader, Fabric API, and Sodium are required. Iris is optional. Install the file that matches your game and loader setup.

## Commands

Use `/sr` or `/selectiverender`. Commands without a listed key have no dedicated default binding.

<table>
<thead><tr><th>Alias</th><th>Expanded command</th><th>Purpose</th><th>Key</th></tr></thead>
<tbody>
<tr><td><code>/sr&nbsp;1</code></td><td><code>pos1</code></td><td>Mark corner 1</td><td></td></tr>
<tr><td><code>/sr&nbsp;2</code></td><td><code>pos2</code></td><td>Mark corner 2</td><td></td></tr>
<tr><td rowspan="2"><code>/sr&nbsp;c</code></td><td><code>create NAME [render|hidden]</code></td><td>Create from the selection source in settings; defaults to <code>render</code></td><td></td></tr>
<tr><td><code>create X1 Y1 Z1 X2 Y2 Z2 NAME [render|hidden]</code></td><td>Create from two corners; defaults to <code>render</code></td><td></td></tr>
<tr><td rowspan="3"><code>/sr&nbsp;t</code></td><td><code>toggle</code></td><td>Toggle render group</td><td><code>F9</code></td></tr>
<tr><td><code>toggle NAME</code></td><td>Toggle one render region</td><td></td></tr>
<tr><td><code>toggle all</code></td><td>Select all render regions or clear selection</td><td></td></tr>
<tr><td rowspan="3"><code>/sr&nbsp;h</code></td><td><code>hide</code></td><td>Toggle hide group</td><td><code>F10</code></td></tr>
<tr><td><code>hide NAME</code></td><td>Toggle one hidden region</td><td></td></tr>
<tr><td><code>hide all</code></td><td>Select all hide regions or clear selection</td><td></td></tr>
<tr><td rowspan="2"><code>/sr&nbsp;l</code></td><td><code>list</code></td><td>List render regions</td><td></td></tr>
<tr><td><code>list hidden</code></td><td>List hidden regions</td><td></td></tr>
<tr><td><code>/sr&nbsp;r</code></td><td><code>redefine NAME</code></td><td>Update bounds from the selection source in settings</td><td></td></tr>
<tr><td><code>/sr&nbsp;n</code></td><td><code>name OLDNAME NEWNAME</code></td><td>Rename region</td><td></td></tr>
<tr><td><code>/sr&nbsp;d</code></td><td><code>delete NAME</code></td><td>Delete saved region</td><td></td></tr>
<tr><td rowspan="4"><code>/sr&nbsp;f</code></td><td><code>filter NAME</code></td><td>View region filters</td><td></td></tr>
<tr><td><code>filter NAME hide SELECTOR</code></td><td>Hide matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME only SELECTOR</code></td><td>Keep only matches by block ID or tag</td><td></td></tr>
<tr><td><code>filter NAME clear</code></td><td>Clear region filters</td><td></td></tr>
<tr><td rowspan="3"><code>/sr&nbsp;p</code></td><td><code>plot [minY] [maxY] [xzMargin]</code></td><td>Add or remove current plot</td><td>Not Bound</td></tr>
<tr><td><code>plot clear</code></td><td>Clear temporary plots</td><td></td></tr>
<tr><td><code>plot save NAME [minY] [maxY] [xzMargin]</code></td><td>Save plot as a region</td><td></td></tr>
<tr><td></td><td></td><td>Open settings</td><td><code>#</code></td></tr>
<tr><td></td><td></td><td>Toggle current plot region</td><td>Not Bound</td></tr>
<tr><td></td><td></td><td>Toggle all block filters</td><td>Not Bound</td></tr>
</tbody>
</table>

## Optional server plot support

The [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) add-on can provide exact server plot outlines. It is optional. Manually created regions work without server setup. Plot groups are temporary for the current Minecraft session, while saved plots become regular regions.

## More information

For detailed setup, settings, limitations, and contribution information, see the [GitHub README](https://github.com/cepreni-pengwing/selective-render#readme). Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.

Choose **SR positions** or **WorldEdit** as the selection source in settings. With WorldEdit selected, SR position commands are unavailable. Explicit coordinates still work.

Filters accept block IDs or tags prefixed with `#`. Add several at once by separating them with commas. Hidden regions can also have filters: active hiding overrides them, and switching hiding off restores their usual behavior.

Settings are organized into **General**, **Regions**, **Interactions**, **Boundaries**, and **Plots** tabs. Option labels, buttons and input boxes share a row where space allows. Content scrolls with the mouse wheel or Page Up/Page Down when needed. One virtual-skylight option applies to all region types. **Interactions** controls which blocks you can target, including hidden content and behavior while rendering is off. **Boundaries** also lets you hide cut fluid surfaces, including water, lava, and supported modded fluids.

**Regions** groups your world's or server's saved regions by dimension. Select a region to rename it, switch between rendering and hiding, or select/deselect it. Buttons apply immediately; confirm typed values with Enter or by leaving the input box. Expand **Bounding boxes** to inspect its bounds and edit the corners when there is exactly one box. Expand **Filters** to add, edit or remove block filters, with Tab completion for IDs and tags. No separate save button is needed.
