# Selective Render

Selective Render is a client-side Fabric mod that lets you focus on chosen areas of a Minecraft world. Create regions, hide unwanted surroundings, or filter specific blocks while building. It changes what you see and target, not the world itself.

## Requirements

Fabric Loader, Fabric API, and Sodium are required. Iris is optional. Install the file that matches your game and loader setup.

## Commands

Use `/sr` or `/selectiverender`. Commands without a listed key have no dedicated default binding.

| Alias | Expanded command | Purpose | Key |
|---|---|---|---|
| `/sr 1` | `pos1` | Mark corner 1. | Not Bound |
| `/sr 2` | `pos2` | Mark corner 2. | Not Bound |
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
| `/sr f` | `filter NAME` | View region filters. |  |
| `/sr f` | `filter NAME hide SELECTOR` | Hide matches by block ID or tag. |  |
| `/sr f` | `filter NAME only SELECTOR` | Keep only matches by block ID or tag. |  |
| `/sr f` | `filter NAME clear` | Clear region filters. |  |
| `/sr p` | <small>`plot [minY] [maxY] [xzMargin]`</small> | Add or remove current plot. | Not Bound |
| `/sr p` | `plot clear` | Clear temporary plots. | Not Bound |
| `/sr p` | <small>`plot save NAME [minY] [maxY] [xzMargin]` (`s` alias)</small> | Save plot as a region. |  |
|  |  | Clear temporary plots. | Not Bound |
|  |  | Cycle boundary faces. | Not Bound |
|  |  | Cycle interaction mode. | Not Bound |
|  |  | Cycle player visibility. | Not Bound |
|  |  | Open settings. | `#` |
|  |  | Set selection position 1. | Not Bound |
|  |  | Set selection position 2. | Not Bound |
|  |  | Toggle current plot region. | Not Bound |
|  |  | Toggle all block filters. | Not Bound |
|  |  | Toggle hide group. | `F10` |
|  |  | Toggle render group. | `F9` |

Keybinds shown in the table are defaults. The settings key is `#`, hide-group toggle is `F10`, and render-group toggle is `F9`. All other keybinds are Not Bound by default and can be changed in Minecraft's Controls settings.

## Optional server plot support

The [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) add-on can provide exact server plot outlines. It is optional. Manually created regions work without server setup. Plot groups are temporary for the current Minecraft session, while saved plots become regular regions.

## More information

For detailed setup, settings, limitations, and contribution information, see the [GitHub README](https://github.com/cepreni-pengwing/selective-render#readme). Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.
