# Selective Render

Show only the parts of your world you choose. Render selected regions, hide others, or filter blocks by ID or tag. Client-side only, nothing changes on the server.

- Block-accurate regions from WorldEdit or `/sr pos1` / `/sr pos2`
- Hide regions or filter blocks like `#minecraft:logs`
- Custom boundary faces: original, texture or color (with rainbow preset)
- Plot isolation via the [Selective Render Plots](https://modrinth.com/plugin/selective-render-plots) server add-on or a client-only grid
- Can improve FPS when large areas are hidden

## Requirements

Fabric Loader, Fabric API and Sodium. Iris is optional.

## Quick start

| Command / Key | Action |
| --- | --- |
| `/sr create NAME` | Save the current selection as a region |
| `F9` | Show only render regions |
| `F10` | Toggle hidden regions |
| `/sr filter NAME hide SELECTOR` | Hide blocks by ID or tag |
| `/sr plot` | Isolate the plot you are standing on |
| `#` | Open settings |

All commands and settings: [GitHub](https://github.com/cepreni-pengwing/selective-render#readme) · Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com)
