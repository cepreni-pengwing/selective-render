# Changelog

All notable changes to Selective Render are documented here.

## 1.7.7-test.5

### Fixed

- Moved the shared Indium render context outside the reserved Mixin package so custom-model chunk
  meshing no longer fails with an `IllegalClassLoadError`.

## 1.7.7-test.4

### Added

- Replaced the three RGB sliders with a color wheel, brightness control, live preview, and hex value.
- Added colored boundary-face support for custom Fabric Renderer API models rendered through Indium.

### Changed

- Changed the default boundary color for new settings to black.

### Fixed

- Used each Sodium quad list's actual cull direction instead of its lighting direction when coloring
  boundary faces.
- Limited unculled custom-model coloring to geometry that lies on the corresponding block boundary.

## 1.7.7-test.3

### Added

- Added RGB sliders, a color preview, and a hex display for colored boundary faces.

### Fixed

- Applied Sodium boundary tinting to all directional boundary quads instead of requiring exact
  full-block plane coordinates, covering partial and custom model geometry more reliably.

## 1.7.7-test.2

### Changed

- Replaced the initial region-box styling controls with block-face boundary modes.
- `Normal` preserves exposed cut faces, `Culled` removes faces adjacent to invisible space,
  and `Colored` marks those exact boundary faces with the configured color.
- Kept region wireframe boxes as a separate simple off/on debug option without color controls.

## 1.7.7-test.1

### Added

- Added configurable player visibility modes: none, inside regions, outside regions, and everywhere.
- Added an initial region wireframe visualization and settings screen.
- Added an unassigned keybind for the new Selective Render settings screen.
- Added HUD overlay feedback for global render, hide, and plot toggles without adding chat noise.

### Changed

- Enabling render or plot isolation now also enables selected hide regions automatically.
- Precomputed compact per-build-volume opacity masks for virtual skylight propagation.

### Fixed

- Initialized virtual skylight columns from each visible block's actual opacity so partial,
  translucent, and modded blocks no longer become binary full-column occluders.

## 1.7.6

### Added

- Added `a` as the short alias for `all` in regular and hidden group commands.

### Documentation

- Added direct Modrinth project links to the GitHub README.

## 1.7.5

### Fixed

- Prevented a Sodium/Indium chunk-meshing crash when virtual skylight queried a block state that
  was temporarily unavailable in the render-world slice.

## 1.7.4

### Changed

- Published visibility, plot, hidden, override, lookup, and traversal data as one immutable render snapshot.
- Cached traversal section keys and rejected out-of-frustum fallback sections before Sodium receives them.
- Reused bounded virtual-skylight buffers and accounted for partial block opacity during propagation.
- Expanded interaction filtering and allowed client raycasts to pass through invisible blocks and fluids.
- Added an unassigned PlotSquared toggle keybind for `/sr p`.
- Added temporary PlotSquared Y bounds and X/Z margins through `/sr p minY maxY xzMargin`.
- Added optional X/Z margins to saved plot presets through `/sr p s NAME minY maxY xzMargin`.

### Fixed

- Removed stale light-cache entries on chunk unload and isolated cached heights by snapshot generation.
- Rebuilt the full 14-block virtual-light influence area after visibility or occluder changes.
- Recovered invalid configs from backups and uniquely identified singleplayer saves while migrating legacy data.
- Rebuilt visible hidden overrides correctly when their preset is deleted.
- Guarded region block counts against integer overflow and applied virtual direct skylight to entities and block entities.

### Build

- Pinned Fabric Loom and rejected release tags that do not exactly match `mod_version`.
- Added regression coverage for snapshots, backup recovery, rebuild ranges, preset deletion, traversal keys, and cache cleanup.

## 1.7.3

### Changed

- Indexed region lookups spatially so large preset collections no longer require linear scans in render hotpaths.
- Classified sections before block-level filtering and limited virtual skylight work to affected chunk columns.
- Combined Sodium's native occlusion traversal with a near-to-far fallback for disconnected selected regions.

### Fixed

- Removed the measurable hidden-only chunk-build slowdown in unaffected world sections.
- Prioritized fallback region loading by three-dimensional distance from the player instead of west to east.
- Preserved virtual-skylight corrections while avoiding unnecessary propagation and allocation.

## 1.7.2-test.10

### Fixed

- Prioritized directly traversed region sections by three-dimensional distance from the player instead of loading them west to east.

## 1.7.2-test.9

### Changed

- Classified 16-cubed sections as unchanged, partial, or hidden so only boundary sections perform block-level region checks.
- Limited virtual-skylight work to cached chunk columns near an active render or hidden region.
- Preserved Sodium's native occlusion traversal while the camera is inside a rendered section.

### Fixed

- Removed most Selective Render chunk-build overhead from ordinary sections in hidden-only mode.

## 1.7.2-test.8

### Changed

- Avoided virtual-skylight propagation when vanilla lighting is already at its maximum.
- Reused Sodium skylight buffers between section builds instead of allocating them for every build.
- Skipped out-of-frustum sections before they reach Sodium's rebuild collector.
- Cached derived traversal lists and reused overlap-deduplication storage.
- Replaced the unbounded boxed per-column occluder map with a bounded, chunk-local cache.

### Fixed

- Invalidated virtual-light caches consistently across Plot mode state changes.

## 1.7.2-test.7

### Fixed

- Applied virtual skylight to the topmost visible non-full surface block without propagating light through it.
- Removed 16-block lighting seams by preserving full skylight through unobstructed vertical columns and propagating it through a 14-block halo around each Sodium build volume.
- Replaced boxed light maps and queue nodes with fixed primitive arrays to bound allocation and chunk-build overhead.
- Corrected the exact `WorldRenderer.updateBlock(BlockView, ...)` mixin descriptor that prevented `test.3` and `test.4` from starting.
- Added a shared, invalidated visible-occluder column cache so vertically distant hidden roofs can seed local Sodium skylight without per-sample searches.
- Replaced per-sample virtual-skylight searches with one cached propagation pass per Sodium build volume.
- Visible hide-group overrides are now included in Sodium section traversal outside the normal whitelist.

### Changed

- Render-group and hide-group keybinds now default to `F8` and `F9`.
- Suffixed version tags are prereleases while plain version tags publish stable releases.

## 1.7.1

### Added

- Automated Java 17 builds, Sodium compile-compatibility checks, and unit tests.
- Contribution guidance and structured issue forms.
- Unassigned keybinds for selection positions and `/sr 1` and `/sr 2` command aliases.

### Fixed

- Visible hide-group regions now override the regular render whitelist.
- Virtual skylight now propagates around visible overhangs instead of retaining hidden-roof artifacts.

### Changed

- Project metadata now identifies `cepreni-pengwing` as maintainer and Codex as contributor.
- Compatibility and known limitations are documented more precisely.
- Loader metadata now matches the tested Minecraft 1.20.1 and Sodium 0.5.8+ baseline.
- Licensing changed from MIT to GPL-3.0-only.

## 1.7.0

### Added

- Optional Selective Render Plots integration through `/sr plot`.

### Fixed

- Plot mode state now remains consistent with normal render-group toggles.
