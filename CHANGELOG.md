# Changelog

All notable changes to Selective Render are documented here.

## 1.8.5

- Fixed multiplayer region files not loading in 1.8.4: server identity now comes directly from
  the incoming world's connection, before the client player exists. Existing file names and
  stored region data remain compatible; no migration or recreation is needed.
- The first plot in an empty temporary selection enables isolation. Adding more plots preserves
  your current render toggle, so you can collect plots while viewing the full world.
- Avoided terrain/optional-renderer rebuilds when modifying a disabled temporary plot selection,
  and stopped re-enabling the hidden group when restoring a disabled plot selection.

## 1.8.4

- Added the SR icon, a simpler description, Modrinth website link, and an optional Mod Menu settings button.
- Added keybinds to cycle every player, interaction, and boundary mode, plus a shortcut for `/sr p clear`.
  Player visibility defaults to K; the other new shortcuts are unassigned. Existing bindings are preserved.
- Changed the interaction default to Everywhere. Players default to Everywhere, boundary faces to Normal,
  and debug boxes to Off; existing saved settings are preserved.
- Raised the local section-update threshold before a full renderer reload from 1,024 sections / 35%
  to 4,096 sections / 60%. Flywheel visibility changes still require their dedicated full refresh.
- Unified world-transition handling so the JOIN event does not reload saved groups after temporary
  plots have already been restored. The reported visual plot-arrangement issue still needs in-game verification.
- Replaced Discord contact details with pengwing.ac@gmail.com.

## 1.8.3

### Fixed

- Kept Vanilla crosshair targets, block outlines, and entity targeting consistent with the selected
  interaction mode, while allowing raycasts to continue through disallowed targets.
- Added Axiom 5.4.2 raycast compatibility, including Orbit Camera and Axiom brush targeting, by
  filtering its custom block and fluid collision-shape queries at exact region boundaries.

### Compatibility

- Leaves server-side WorldEdit, FAWE, TerraSniper, and VoxelSniper targeting authoritative while
  keeping their Vanilla client target feedback aligned with Selective Render.

## 1.8.2

### Fixed

- Filtered CanvasBlocks paintings rendered through its custom world-render callback, using each
  painting's anchor position for region visibility.
- Removed broad terrain rebuilds after individual block changes and restored bounded virtual-light
  cache invalidation to prevent placement and removal lag spikes.

## 1.8.1

### Fixed

- Filtered Create/Flywheel block entities and entities by their logical anchor position, including
  animated visuals that bypass Minecraft's normal entity and block-entity renderers.
- Filtered BelieveMod's separately batched rope entities outside visible regions.
- Invalidated cached entity, player, and block-entity skylight when roofs change far above the
  sampled section, preventing stale light below tall enclosed spaces.
- Kept ordinary block and lighting updates localized when Flywheel is installed instead of
  reloading the renderer after every placed or removed block.

### Compatibility

- Rebuilds Flywheel's persistent GPU visuals only when Selective Render visibility changes; setups
  without Flywheel retain the existing localized terrain rebuild path.

## 1.8.0

### Added

- Added temporary multi-plot isolation: `/sr p` can add or remove individual plots, `/sr p clear`
  clears the group, and temporary groups survive reconnects and dimension changes for the current
  Minecraft session.
- Added negative horizontal plot margins, allowing temporary and saved plot regions to be shrunk
  while preserving merged and irregular plot shapes.
- Added an unassigned player-visibility keybind and interaction modes for nowhere, inside regions,
  outside regions, or everywhere.
- Added default PlotSquared Y bounds of `-100` to `400`, so either or both Y values may be omitted.

### Changed

- Renamed the opaque boundary mode to `Black`, ordered the modes as `Normal`, `Black`, `Culled`, and
  kept hidden-region cut surfaces normal in every mode.
- Made terrain, player, entity, and block-entity skylight share shape-aware lighting around slabs,
  stairs, walls, overhangs, and other partial blocks.
- Reduced virtual-light cache memory, localized block and chunk invalidation, and added no-op fast
  paths when Selective Render is installed but no filtering features are active.

### Fixed

- Prevented virtual skylight in dimensions without skylight and kept entity lighting aligned with
  terrain around distant partial shapes.
- Invalidated affected lighting when chunks load or unload and when nearby blocks change.

### Documentation

- Documented the Selective Render Plots LuckPerms permission and transferable context-hashed region
  files.

## 1.8.0-test.5

### Changed

- Reduced entity, player, and block-entity virtual-light cache memory and cache misses by retaining
  compact section results while reusing the larger calculation workspace.
- Skipped unnecessary boundary and light-propagation work when it cannot affect the result.
- Added explicit no-op fast paths across hot terrain, lighting, entity, and interaction checks when
  no render or hide regions are active and the related settings remain unrestricted.

### Fixed

- Invalidated only virtual-light sections affected by block and chunk changes, including newly
  loaded chunks, instead of discarding the complete cache.
- Kept virtual skylight disabled in dimensions without skylight and aligned entity light scans with
  terrain around distant partial shapes.

## 1.8.0-test.4

### Fixed

- Made entity, player, and block-entity skylight use the same shape-aware virtual light propagation
  as terrain, including correct face occlusion for slabs, stairs, walls, and overhangs.
- Cached virtual entity-light volumes per section and invalidated them for visibility, chunk, and
  block-shape changes to avoid repeating the full calculation every frame.

## 1.8.0-test.3

### Added

- Added temporary multi-plot isolation: using `/sr p` on more plots adds them to the current view,
  using it again on an active plot removes that plot, and `/sr p clear` clears the group.
- Temporary plot groups now survive reconnects and dimension changes during the current Minecraft
  session while remaining separate per server/world and dimension.
- Added negative X/Z margins for temporary and saved plot commands to shrink plot outlines.

### Fixed

- Negative margins treat merged and irregular PlotSquared parts as one shape, avoiding artificial
  gaps at internal cuboid borders and rejecting changes that erase the entire plot.

## 1.8.0-test.2

### Fixed

- Made virtual skylight respect Vanilla's adjacent block-face occlusion in both direct vertical
  lighting and six-direction propagation, preventing partial shapes such as slabs from being
  treated as fully open air.

### Documentation

- Documented the SRP LuckPerms permission and how to transfer context-hashed region files between
  instances.

## 1.8.0-test.1

### Added

- Added an unassigned keybind that switches player rendering directly between everywhere and
  nowhere while remaining synchronized with the player visibility setting.
- Added interaction modes for nowhere, inside regions, outside regions, or everywhere.
- Added default PlotSquared Y bounds of `-100` to `400`, allowing either or both Y arguments to be
  omitted from temporary and saved plot commands.

### Changed

- Renamed the opaque `Colored` boundary mode to `Black` and changed the cycle order to `Normal`,
  `Black`, then `Culled`.
- Hidden-region cut surfaces now always use normal exposed faces, independent of the selected
  boundary mode.

## 1.7.7

### Added

- Added configurable player visibility modes for nowhere, inside regions, outside regions, or
  everywhere, including matching debug-hitbox visibility.
- Added an unassigned keybind and settings screen for player visibility, boundary faces, and the
  separate region-box debug view.
- Added HUD overlay feedback for global render, hide, and plot toggles without chat noise.
- Added `Normal`, `Culled`, and `Colored` boundary-face modes. `Colored` renders a fully opaque black
  cut surface across Vanilla, Sodium, and Indium model paths.

### Changed

- Enabling render or plot isolation now also enables selected hide regions automatically.
- Improved virtual-skylight opacity handling for partial, translucent, and modded blocks.

### Fixed

- Prevented selected hide regions from silently conflicting with newly enabled render isolation.
- Used actual boundary-face directions for Sodium and custom Fabric Renderer API models.

## 1.7.7-test.6

### Changed

- Made colored boundary faces fully opaque by replacing their sampled block texture with a single
  solid atlas texel. The color no longer acts as a translucent-looking texture tint.

### Fixed

- Rendered the color wheel through Minecraft's normal GUI draw context so the visible wheel matches
  its interactive area.

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
