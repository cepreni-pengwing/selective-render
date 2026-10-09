# Selective Render

Selective Render is a client-side Fabric mod that lets you focus on chosen areas of a Minecraft world. Create regions, hide unwanted surroundings, or filter specific blocks while building. It changes what you see and target, not the world itself.

## Requirements

Fabric Loader, Fabric API, and Sodium are required. Iris is optional. Install the file that matches your game and loader setup.

## Commands

Use `/sr` or `/selectiverender`. Commands without a listed key have no dedicated default binding.

| Short alias | Full command (without `/sr`) | Purpose | Keybind (default) |
|---|---|---|---|
| `/sr 1` | `pos1` | Mark the first region corner. | Unassigned |
| `/sr 2` | `pos2` | Mark the opposite corner. | Unassigned |
| `/sr s` | `save NAME` | Save and show the marked region. |  |
| `/sr c` | `create NAME [render\|hidden]` | Make a region from a WorldEdit selection. |  |
| `/sr c` | `create X1 Y1 Z1 X2 Y2 Z2 NAME [render\|hidden]` | Make a region from coordinates. |  |
| `/sr t` | `toggle` | Enable or disable the render group. | `F9` |
| `/sr t` | `toggle NAME` | Add or remove a region from the render group. |  |
| `/sr t a` | `toggle all` | Select all render regions or clear that selection. |  |
| `/sr h` | `hide` | Enable or disable the hide group. | `F10` |
| `/sr h` | `hide NAME` | Add a region to the hide group or toggle it. |  |
| `/sr h a` | `hide all` | Select all hide regions or clear that selection. |  |
| `/sr l` | `list` | List saved render regions. |  |
| `/sr l h` | `list hidden` | List saved hide regions. |  |
| `/sr r` | `redefine NAME` | Update a region from the marked corners. |  |
| `/sr n` | `name OLDNAME NEWNAME` | Rename a region. |  |
| `/sr d` | `delete NAME` | Delete a saved region. |  |
| `/sr f` | `filter NAME` | View the region's filters. |  |
| `/sr f` | `filter NAME hide SELECTOR` | Hide matching blocks. `SELECTOR` is an `id:...` block ID or `tag:...` block tag. |  |
| `/sr f` | `filter NAME only SELECTOR` | Keep only matching blocks. `SELECTOR` is an `id:...` block ID or `tag:...` block tag. |  |
| `/sr f` | `filter NAME clear` | Clear that region's filters. |  |
| `/sr p` | `plot [minY] [maxY] [xzMargin]` | Add or remove the plot beneath you. | `Backspace` |
| `/sr p clear` | `plot clear` | Clear the temporary plot group. | Unassigned |
| `/sr p s` | `plot save NAME [minY] [maxY] [xzMargin]` | Save a plot as a regular region. |  |
| `/sr diag` | `diagnose` | Show whether diagnostics are recording. |  |
| `/sr diag` | `diagnose start [seconds]` | Start recording (120 seconds by default). |  |
| `/sr diag` | `diagnose mark LABEL` | Add a diagnostic marker. |  |
| `/sr diag` | `diagnose stop` | Stop recording. |  |

The settings key defaults to `#` on German layouts. `F9` toggles rendering, `F10` toggles hiding, and `Backspace` toggles the current plot. Other optional controls start unassigned and can be changed in Minecraft's Controls settings.

## Optional server plot support

The [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) add-on can provide exact server plot outlines. It is optional. Manually created regions work without server setup. Plot groups are temporary for the current Minecraft session, while saved plots become regular regions.

## More information

For detailed setup, settings, limitations, and contribution information, see the [GitHub README](https://github.com/cepreni-pengwing/selective-render#readme). Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.
