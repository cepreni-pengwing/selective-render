# Selective Render

Selective Render is a client-side Fabric mod that lets you focus on chosen areas of a Minecraft world. Create regions, hide unwanted surroundings, or filter specific blocks while building. It changes what you see and target, not the world itself.

## Requirements

Fabric Loader, Fabric API, and Sodium are required. Iris is optional. Install the file that matches your game and loader setup.

## Commands

Use `/sr` or `/selectiverender`. Commands without a listed key have no dedicated default binding.

| Command | Purpose | Keybind (default) |
|---|---|---|
| `/sr pos1` (`/sr 1`) | Mark the first region corner. | Unassigned |
| `/sr pos2` (`/sr 2`) | Mark the opposite corner. | Unassigned |
| `/sr save NAME` (`/sr s NAME`) | Save and show the marked region. | None |
| `/sr create NAME [render\|hidden]` (`/sr c ...`) | Make a region from a WorldEdit selection. | None |
| `/sr create X1 Y1 Z1 X2 Y2 Z2 NAME [render\|hidden]` | Make a region from coordinates. | None |
| `/sr toggle [NAME\|all]` (`/sr t ...`, `all` alias `a`) | Show or hide a region, or manage the render group. | `F9` toggles the whole group |
| `/sr hide [NAME\|all]` (`/sr h ...`, `all` alias `a`) | Hide a region or manage the hide group. | `F10` toggles the whole group |
| `/sr redefine NAME` (`/sr r NAME`) | Update a region from the marked corners. | None |
| `/sr name OLDNAME NEWNAME` (`/sr n ...`) | Rename a region. | None |
| `/sr delete NAME` (`/sr d NAME`) | Delete a saved region. | None |
| `/sr list [hidden]` (`/sr l ...`, hidden alias `h`) | List saved regions. | None |
| `/sr filter NAME hide id:...` or `only tag:...` (`/sr f ...`) | Hide matching blocks or keep only matching blocks in a region. | Unassigned global filter toggle |
| `/sr filter NAME` / `/sr filter NAME clear` | View or clear a region's filters. | None |
| `/sr plot [minY] [maxY] [xzMargin]` (`/sr p ...`) | Add or remove the plot beneath you from a temporary group. | `Backspace` |
| `/sr plot clear` (`/sr p clear`) | Clear the temporary plot group. | Unassigned |
| `/sr plot save NAME [minY] [maxY] [xzMargin]` (`/sr p s ...`) | Save the plot as a regular region. | None |
| `/sr diagnose [start [seconds]\|mark LABEL\|stop]` (`/sr diag ...`) | Record or mark performance information for troubleshooting. | None |

The settings key defaults to `#` on German layouts. `F9` toggles rendering, `F10` toggles hiding, and `Backspace` toggles the current plot. Other optional controls start unassigned and can be changed in Minecraft's Controls settings.

## Optional server plot support

The [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) add-on can provide exact server plot outlines. It is optional. Manually created regions work without server setup. Plot groups are temporary for the current Minecraft session, while saved plots become regular regions.

## More information

For detailed setup, settings, limitations, and contribution information, see the [GitHub README](https://github.com/cepreni-pengwing/selective-render#readme). Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Licensed under GPL-3.0-only.
