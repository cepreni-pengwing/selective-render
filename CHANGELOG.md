# Changelog

## 1.13.2

- Picking a hue after the White or Black preset now applies the color immediately
- Shorter, clearer README and Modrinth page
- Release notes now list the changes

## 1.13.1

- Built all Minecraft targets from one Gradle project with shared sources, removing four duplicated copies of common code
- Published prereleases automatically after a successful client smoke test when the version is raised
- Skipped boundary work in 1.20.1 Sodium meshing when no regions or filters are active
- Showed the effective regional interaction state instead of "Off" when inheriting "Inside regions"
- Restricted region names in the editor to characters that commands accept
- Toggled client-grid plots by their footprint, independent of the requested height range
- Rejected a default plot minimum Y above the maximum in the Plots tab
- Refreshed tag-based filters when the server synchronizes tags and cached filter suggestions
- Avoided a brief unfiltered state while saving region editor changes

## 1.13.0

- Expanded `#selectiverender:beams` to 3,388 structural IDs from Conquest Reforged 1.7.0 and Architects 1.0.3, excluding full blocks, layers, stairs, slabs and similar volume shapes
- Added Minecraft 26.1, 26.1.1 and 26.1.2 (one shared JAR for Sodium 0.8.9 and 0.9.2) and Minecraft 26.3
- Showed the regional block texture row only when Texture is explicitly enabled for that region

## 1.12.3

- Preserved region activation when changing a region's type in the menu
- Computed entity, particle and block-entity skylight over several client ticks with a soft 2 ms budget, reusing finished terrain light where possible

## 1.12.2

- Added a chat message when timed diagnostics finish

## 1.12.1

- Refined plot controls and hidden interaction priority

## 1.12

- Added the client-only plot grid set up with `/sr plot grid`, editable in the Plots tab
- Fixed boundary overlays and menu position persistence

## 1.11.13

- Added 21 opt-in in-game visual cases per baseline version with deterministic boundary fixtures and UI screenshots
- Checked actual framebuffer colors, opacity progression, Texture + Color, Rainbow, regional/global color isolation and cut-face removal automatically
- Added in-game widget bounds/overlap and palette visibility assertions, plus an offline HTML review gallery
- Kept images outside Git and releases, with seven-day Actions artifact retention and download links in job summaries
- Made dependency download retries cover transient connection errors and reduced random-spawn placement-test failures

## 1.11.12

- Added a two-click Reset region button beside Delete region, restoring regional options offering Use global and clearing filters while preserving bounding boxes, name, type and activation
- Restored global Rainbow vertex colors in Minecraft 1.20.1 Sodium rendering
- Added runtime reset confirmation/persistence checks and verification of actual terrain rainbow vertex colors

## 1.11.11

- Corrected Minecraft 26.2 slider texture bounds in global and regional boundary palettes
- Removed duplicate Minecraft 1.21.1 menu background/blur passes that blurred previews and captions
- Used immutable opacity sprites and vertex RGB for global as well as regional Color, preserving partial alpha without live color-atlas rewrites
- Rendered rainbow colors per vertex to avoid interpolating across the entire spectrum at the red seam
- Added runtime checks for seven global opacity levels, actual GPU alpha on older targets, stable rainbow UVs, and palette screenshots

## 1.11.10

- Applied virtual skylight to entities and older block-entity render paths when only block filters are active, and excluded filtered roof blocks from direct-sky occluder checks
- Kept missing skylight samples from making entities or particles fullbright in dimensions without skylight
- Made entity target selection respect regional interaction overrides and made Only-filtered entities follow Hidden interactions
- Cached repeated policy queries per thread and immutable snapshot, with weak ownership to avoid retaining obsolete region indexes
- Added active plot-geometry, boundary inheritance and cache-isolation regressions, plus filter-only lighting/entity runtime checks
- Added CI parity checks for intentionally shared source and test files across version ports

## 1.11.9

- Restored identical vertex positions for boundary layers instead of using a distance-dependent world-space offset, and omitted covered underlays for fully opaque Color
- Restricted custom boundary appearance to actual geometric cut planes; nominal and cull directions no longer retexture protruding or rotated decorations
- Removed a Minecraft 1.20.1 region-validation side effect that could overwrite live options when reading another dimension with matching region names
- Applied renderer corrections across all three Minecraft targets and added region-isolation regression coverage

## 1.11.8

- Separated Color boundaries from native model decals and deduplicated nearby overlay planes while preserving original Normal faces and tint
- Rebuilt regional boundary widgets cleanly on layout changes and resolved Color/Texture control visibility directly from their independent local or inherited toggles
- Kept disabled Color palettes from committing stale edits and synchronized regional slider values when choosing presets
- Applied the fixes and regression checks to all three Minecraft builds

## 1.11.7

- Added a default-on Normal boundary layer beneath Color; Texture replaces the original surface, while Color opacity affects only its overlay
- Embedded each region’s texture input, color sliders, presets and preview directly in its Boundaries section, showing appearance controls only for enabled layers
- Kept Rainbow, White and Black presets with stable labels and removed Red, Green and Blue
- Preserved accordion expansion and scroll position independently for each region, dimension and world/server
- Kept global color-lighting inheritance when changing regional colors, with a separate regional lighting reset
- Selected regions when switching their type in the menu, matching named render/hide commands and keeping global render toggles effective
- Preserved original tint/CTM model overlays beneath Color and separated the color plane to avoid coplanar interference

## 1.11.6

- Split player visibility into independent player-group and region-scope settings, preserving legacy choices; new installations default to the WorldEdit selection source with a single source toggle
- Organized the region editor into Filters, Boundaries, Interactions and Bounding boxes, including independent regional texture/color appearance and global inheritance
- Renamed inactive interaction choices to Unrestricted / Follow region rules and decoupled virtual skylight from those choices; active filters retain lighting while empty installations remain no-op
- Isolated nested boundary-model CTM processing from Continuity's active terrain context, preserving the outer renderer state and resourcepack feature switches
- Preserved model UV rotation, mirroring and cropped-tile scale instead of rebuilding texture mapping from min/max coordinates
- Added distinct 8x8 repeat-pattern, nested-context restoration and actual terrain CTM checks, plus UV regression tests

## 1.11.5

- Resolved boundary textures through loaded block models and supported connected-texture transforms, with virtual boundary neighbors and a safe fallback for unsupported models
- Added optional Iris material-metadata handling and consistent material selection for inset/overlay boundary quads
- Moved the remaining color preview to an opaque native texture, matching the slider rendering path
- Added a dedicated preset column beside equal-width sliders, including a perimeter Rainbow preset and basic solid colors; clicking Hue exits Rainbow
- Added rainbow/preview/shader-scope tests and a disposable resourcepack/Continuity/Indium/Iris runtime CI profile across the multi-version workflow

## 1.11.4

- Fixed Only filters blocking placement into air while keeping hidden blocks and region interaction rules protected
- Moved region selection toggles into the region navigation list
- Split boundary Texture and Color into independent render layers; Texture receives lighting and no longer uses Color opacity, with an optional lighting toggle for Color
- Preserved resolved source UVs/textures when the selected texture ID matches the actual block, including supported connected/animated textures; other IDs retain the documented representative-texture fallback
- Replaced slider gradient batching with opaque GUI textures and extended placement, opacity and menu runtime checks across all three builds

## 1.11.3

- Replaced exclusive boundary modes with independent Color and Texture toggles, including combined layers and an unchanged-face fallback when both are off
- Deduplicated coplanar boundary model overlays and baked custom colors into SR-owned textures to prevent grass tint/overlay differences
- Replaced per-pixel slider strips with continuous gradients and kept Up/Down autocomplete navigation inside focused block/tag fields
- Applied the changes to all three Minecraft builds and extended unit/runtime checks

## 1.11.2

- Rebuilt the Boundaries page with vertical mode buttons and inline texture-ID, hue, saturation, brightness and opacity controls
- Added continuous 1% opacity using private boundary atlas slots instead of Sodium’s ambient-occlusion vertex channel; 0% emits no boundary quad
- Disabled boundary diffuse/AO shading and supplied full-bright light coordinates across supported render paths
- Kept fully opaque boundary textures in opaque materials, with per-quad material selection in Sodium 0.5 to avoid moving entire waterlogged blocks into translucent rendering
- Only block filters now hide non-player entities inside their region; players and block entities keep their respective visibility/filter policies
- Shader-pack lighting, shadows and postprocessing still need visual validation and can affect final appearance
- Applied changes to Minecraft 1.20.1, 1.21.1 and 26.2

## 1.11.1

- Remember the settings tab and scroll position, with selected region, collapsed dimensions and expanded filter/bounds sections saved per world/server
- Fix invisible typed filter text in Minecraft 26.2
- Allow leaving boundary appearance with an invalid block ID without applying pending appearance changes
- Remove the diagnostic command alias; use `/sr diagnose`
- Apply the menu and command fixes across all three Minecraft builds

## 1.11.0

- Added Texture boundary mode using loaded block-model textures and block-ID autocomplete
- Replaced Black with Color, with an HSV color wheel and brightness control; existing Black settings migrate to black Color
- Added shared boundary opacity from 0–100% in 10% steps, using translucent render paths
- Added opt-in Color/Texture/Opacity exercises to the supported Sodium runtime profiles in disposable CI worlds
- Applied changes to Minecraft 1.20.1, 1.21.1 and 26.2

## 1.10.20

- Added a dimension-grouped region editor with immediate toggles and validated name, corner and filter edits committed on Enter or focus loss
- Added expandable bounding-box/filter sections and registry/tag Tab completion; multi-box bounds remain read-only
- Added world/server and dimension metadata without renaming existing region files; other-dimension edits do not change the current renderer
- Moved interaction controls into their own tab, unified virtual skylight across all region types, aligned input fields and simplified labels/tooltips

## 1.10.19

- Standardized region terminology throughout the code, commands and documentation while preserving existing saved-region storage
- Arranged colored setting labels and choices horizontally, wrapping choices on narrower screens
- Removed Up/Down buttons; scrolling remains available through the mouse wheel and keyboard

## 1.10.18

- Replaced the settings menu with General, Regions, Boundaries and Plots tabs, separate navigation and scrollable content
- Added direct option selection with disabled selected values and horizontal numeric fields
- Made SR position commands unavailable to the command parser while WorldEdit is selected
- Removed position, option-cycling and temporary-plot-clear keybindings from all builds and documentation
- Extended optional boundary culling to all fluids using supported render paths, retaining existing saved water-culling settings
- Applied the changes to Minecraft 1.20.1, 1.21.1 and 26.2

## 1.10.17

- Added comma-separated block filter batches, bare block IDs and #tag selectors, while keeping id:/tag: compatibility
- Allowed filters on hidden regions; active hiding still overrides block filtering
- Added readable filter feedback and a filter overview, with a more contrasting shared in-game palette
- Disabled SR selection position commands and keybinds when WorldEdit is the selected source
- Made settings scrollable and added a read-only region list with bounds, group state and filter details
- Applied the changes to Minecraft 1.20.1, 1.21.1 and 26.2

## 1.10.16

- Unified region creation under /sr create (/sr c); removed /sr save (/sr s).
- Added a saved selection source setting for SR positions or WorldEdit, shared by create and redefine.
- Refreshed command feedback and settings colors, and removed the SR prefix from notifications.

## 1.10.15
- Water boundary face culling now works independently of the Normal, Black, or Culled block boundary mode in all three Minecraft builds.

## 1.10.14
- Added an optional setting to cull water boundary faces, including flowing and waterlogged water.

## 1.10.13

- Suppressed underwater fog and fluid submersion effects when the camera is inside content hidden by Selective Render.
- Applied the fix to Minecraft 1.20.1, 1.21.1, and 26.2.

## 1.10.12

- Suppressed water, fire, and in-wall screen overlays when the camera is inside content hidden by Selective Render.
- Made virtual skylight follow saved block filters while rendering is off only in `Filtered` mode; `Vanilla` mode keeps the no-op behavior.
- Applied both changes to Minecraft 1.20.1, 1.21.1, and 26.2; added regression coverage for the virtual-light no-op policy.

## 1.10.11

- Prevented a Sodium/Indium chunk-meshing crash when a cached source section contains a null block state; the renderer now falls back to the live chunk data.
- Applied the same crash guard to Minecraft 1.20.1, 1.21.1, and 26.2.
- Cached block IDs and tags used by filters instead of rebuilding them for every sampled block during virtual-skylight scans; cache is cleared on resource reload and world changes.
- Kept the opt-in performance recording commands and cache counters available for follow-up diagnosis.

## 1.10.10

- Reuse captured source-block data across overlapping virtual-skylight volumes for entities, particles, and block entities, with bounded memory and block/chunk invalidation.
- Reuse chunk-section block snapshots across Sodium terrain-light mesh builds; cache entries are tied to chunk revisions so edits cannot reuse stale source data.
- Added diagnostic cache hit/miss counters to help compare future performance recordings. This is a performance test build; in-game profiling is still needed to measure the improvement.
- Applied the changes consistently to Minecraft 1.20.1, 1.21.1, and 26.2.

## 1.10.9

- Added opt-in performance recording with `/sr diagnose start [seconds]`, `mark LABEL`, and `stop`; recordings automatically end and write bounded summaries to `logs/latest.log`.
- Measure virtual-skylight solver phases, terrain cache behavior, particle/entity lighting, cold and dirty light-volume builds, Sodium chunk work, block updates, frame/tick timing, memory, and garbage collection.
- Diagnostics are disabled by default and independent of debug boxes; this build does not change lighting behavior or claim another lag fix.
- Available for Minecraft 1.20.1, 1.21.1, and 26.2.

## 1.10.8

- Removed the legacy `rename` command alias so typing `/sr r` no longer suggests an unrelated rename operation.
- Use `/sr r NAME` or `/sr redefine NAME` to redefine, and `/sr n OLDNAME NEWNAME` or `/sr name OLDNAME NEWNAME` to rename, on all three Minecraft builds.
- Includes the virtual-skylight performance changes from 1.10.7.

## 1.10.7

- Added a bounded Sodium terrain-skylight cache so mesh rebuilds after light-equivalent material changes can reuse their lighting instead of repeating the full solver.
- Invalidate affected chunk columns on optical changes and chunk loading/unloading; reject stale asynchronous block snapshots and keep worlds, region states, and lighting settings separate.
- Compare light-occlusion shapes and block filters as well as opacity, preserving updates for slab/shape changes and hidden roofs.
- Reduced repeated chunk lookups during uncached terrain-light calculations and skipped propagation checks that cannot pass an opaque block.
- Keep the vanilla-light fast path conservative around block filters, and apply the changes to Minecraft 1.20.1, 1.21.1, and 26.2.
- This is a performance test build; improvements during real server editing still need player verification.

## 1.10.6

- Reduced virtual-skylight work during block updates: changes that do not affect light opacity no longer invalidate cached light volumes.
- Spaced out expensive volume rebuilds so bursts of edits cannot trigger rebuilds on consecutive ticks.
- Applied the same behavior to Minecraft 1.20.1, 1.21.1, and 26.2.

## 1.10.5

- Saved region filters now apply independently of whether their render regions are toggled on.
- Filter-hidden blocks follow the hidden-region interaction setting across all supported Minecraft builds.

## 1.10.4

- Added an unassigned keybind to toggle all saved block filters together, with an in-game status notification.
- Made filtered-out blocks follow the hidden-region interaction setting, including targeting, hitboxes, and block interactions.
- Expanded `tag:selectiverender:beams` to include Architects stripped-log beam variants and both mods' lintels and poles.
- Applied the toggle consistently to Minecraft 1.20.1, 1.21.1, and 26.2; its enabled state is saved between sessions.

## 1.10.3

- Added the built-in `tag:selectiverender:beams` filter for Conquest Reforged and Architects blocks whose IDs contain `beam`, including beam layers, stairs, and other variants.
- Made the beam filter available consistently in all three Minecraft builds, including when the server does not sync a matching block tag.

## 1.10.2

- Fixed block-filter command parsing so namespaced IDs and tags such as `tag:axiom:solid` work as intended.
- Applied the command fix consistently to all three Minecraft builds.

## 1.10.1

- Added per-region block filters with `/sr filter` (`/sr f`) using block IDs or registered block tags, including Axiom tags when available.
- Added `hide`, `only`, and `clear` filter modes; filters persist with each saved region and work across overlapping regions.
- Applied block, fluid, block-entity, and lighting behavior consistently to all three Minecraft builds.

## 1.10.0

- Added client-side WorldEdit cuboid selection import with `/sr c NAME [render|hidden]`
  and `/sr create NAME [render|hidden]`. Explicit-coordinate commands still work.
- Receive WorldEdit CUI updates directly, or observe an installed WorldEditCUI without
  replacing its network receiver. No SR server addon is needed.
- Reject incomplete, malformed and non-cuboid selections instead of creating incorrect
  regions. Clear the temporary selection mirror on disconnect and world changes.
- Applied the integration to Minecraft 1.20.1, 1.21.1 and 26.2, with selection regression tests.

## 1.9.24

- Avoided building Sodium virtual-skylight volumes inside regions when the entire light halo
  and every non-air block above it remain visible. This targets unnecessary chunk-build work
  in large, mostly unobstructed render regions.
- Retained the full lighting solver near cuts, hidden regions and potentially hidden roofs.
- Applied the fast path to all three Minecraft builds and added conservative boundary tests.

## 1.9.23

- Reduced virtual skylight propagation work by queuing only direct-light cells that can
  brighten a neighbor, and checking brightness before spatial region policies.
- Skipped spatial policy lookups when render and hidden skylight modes are identical.
- Applied the optimization to terrain and entity lighting on all three Minecraft builds.
- Added reference-comparison tests for propagation with directional barriers and attenuation.

## 1.9.22

- Added one-command cuboid creation with render/hidden context, region redefinition, and clearer
  rename aliases.
- Added an independent virtual-skylight mode for hidden regions.

## 1.9.21

- Fixed Minecraft 1.21.1 worlds crashing on join with Sodium 0.8.13 because the visible-section collector hook used an outdated method signature.

## 1.9.20

- Prevented continuous block updates from indefinitely postponing virtual-light cache rebuilds, fixing intermittent dark underground particles and items without restoring synchronous rebuild spikes.

## 1.9.19

- Added rate-limited virtual-light diagnostics while debug boxes are enabled to isolate intermittent underground particle and item lighting.

## 1.9.18

- Stabilized virtual lighting for fast breaking particles and briefly dark dropped items across every supported Minecraft version.

## 1.9.17

- Matched breaking-particle lighting to the filtered world across every supported Minecraft version, ignoring hidden roofs while preserving visible indoor shadows.

## 1.9.16

- Kept breaking particles on current vanilla light values instead of stale virtual-skylight cache data across every supported Minecraft version.

## 1.9.15

- Kept Minecraft 26.2 breaking particles on current vanilla light values instead of stale virtual-skylight cache data.
- Removed the unnecessary creative-click cooldown workaround after isolating that behavior to noclip.

## 1.9.14

- Fixed the Minecraft 26.2 render-group toggle re-enabling the hide group after two F9 presses.
- Prevented filtered misses from swallowing the next valid creative-mode block click on Minecraft 26.2.
- Improved Minecraft 26.2 breaking-particle lighting immediately after block changes.

## 1.9.13

- Updated the Minecraft 1.21.1 build for the maintained Sodium 0.8.x branch, starting with Sodium 0.8.13.
- Added Minecraft 26.2 Axiom raycast filtering so Orbit Camera and Infinite Reach follow the same interaction-region rules as vanilla targeting.
- Rendered debug region boxes as unobstructed overlays on every supported Minecraft version.

## 1.9.12

- Fixed Black boundary faces crashing Minecraft 26.2 chunk workers by using the registered block-atlas identifier.

## 1.9.11

- Fixed the settings screen crashing on Minecraft 26.2 because its background blur was requested twice per frame.

## 1.9.10

- Fixed the Minecraft 26.2 build disconnecting or crashing when entering a world with Sodium 0.9.2.
- Made the 26.2 Sodium boundary hook less dependent on Sodium's internal field layout.

## 1.9.9

- Restored the Minecraft 26.2 Fabric build and brought it up to date with the current settings,
  interaction, player-visibility, virtual-skylight, keybind, and lighting-performance behavior.
- GitHub prereleases now publish clearly labelled JARs for Minecraft 1.20.1, 1.21.1, and 26.2
  together.

## 1.9.8

- Reduced block placement and breaking frame spikes while virtual lighting is enabled by keeping stale entity-light volumes usable and rebuilding at most one affected volume per client tick.

## 1.9.7

- Fixed a second Minecraft 1.21.1 world-entry crash caused by an outdated Sodium visible-section collector method signature.

## 1.9.6

- Fixed Minecraft 1.21.1 worlds disconnecting during login because the client chunk unload hook still used the Minecraft 1.20.1 method signature.

## 1.9.5

- Added a separate Minecraft 1.21.1 Fabric build using Java 21, Fabric API 0.116.17, and Sodium
  0.6.13. The established Minecraft 1.20.1 build remains unchanged.
- Ported rendering filters, boundary modes, virtual lighting, interactions, settings, keybinds,
  region persistence, and the PlotSquared client protocol to Minecraft 1.21.1.
- GitHub builds and prereleases now produce clearly versioned JARs for Minecraft 1.20.1 and 1.21.1.
  The older experimental 26.2 target remains excluded from releases.

## 1.9.4

- Corrected the fresh-install keybind defaults: F9 toggles the render group, F10 toggles the hide
  group, Backspace toggles the current PlotSquared region, and the German `#`/apostrophe key opens
  settings. Selection positions and player visibility remain unassigned.
- This prerelease targets Minecraft 1.20.1 Fabric; the experimental 26.2 port is unchanged.

## 1.9.3

- Changed fresh-install defaults to F9 for the render group, F10 for the hide group, Backspace for
  PlotSquared region toggling, the keyboard's `#` key for settings, and `-64` for the default
  PlotSquared minimum Y. Existing custom bindings and non-default saved heights remain untouched.
- Added own-player-only and all-players-except-own visibility modes.
- Added independent hidden-region interaction control. Inactive filtered interactions now use every
  saved region rather than only currently selected regions; Vanilla mode remains a true no-op.
- Added virtual-skylight entry modes for top and sides, top only, sides only, or none. Both remains
  the default.
- Fixed normal render toggles unexpectedly re-enabling a disabled hide group.
- This prerelease targets Minecraft 1.20.1 Fabric; the experimental 26.2 port is unchanged.

## 1.9.2

- Fixed Axiom targeting for decorative blocks whose outline shape differs from their collision
  shape. Allowed blocks now retain Axiom's original outline hitbox instead of being replaced by
  their collision hitbox.
- This patch targets the Minecraft 1.20.1 Fabric build; the experimental 26.2 port is unchanged.

## 1.9.1

- Added a separate Minecraft 26.2 client build using Java 25, Fabric API 0.159.0, and Sodium 0.9.1.
- GitHub builds and prereleases now provide clearly labelled JARs for both Minecraft 1.20.1 and 26.2.
- Ported region filtering, boundary modes, virtual lighting, entity and particle visibility,
  interactions, settings, debug boxes, and the existing PlotSquared bridge protocol to 26.2.
- Preserved the established Minecraft 1.20.1 implementation and its compatibility checks.

All notable changes to Selective Render are documented here.

## 1.9.0

Stable release of the 1.8.11 implementation, with no additional gameplay or rendering changes.

- Includes the compatibility, interaction, region-loading, and configurable render-rebuild improvements
  developed across 1.8.1–1.8.11.
- Includes rendering hot-path optimizations, improved backup recovery, and boundary/light-cache fixes.
- Conquest Reforged extension-toggle boundaries remain a known limitation. Flywheel compatibility
  fallback can still require a full renderer reload independently of the configured threshold.
- Requires Minecraft 1.20.1, Fabric API, and Sodium. Existing settings and region files are preserved.

See the version entries below for the detailed changes.

## 1.8.11

### Fixed

- Rendering and plot toggles now use the configured rebuild threshold instead of forcing a full
  renderer reload. Removed the additional 85% cutoff; transitions rebuild previously hidden loaded
  sections too, including when the last active region is removed. Flywheel compatibility fallback
  and Minecraft resource reloads remain exceptions.
- Clearing temporary plots also cancels a pending plot request.
- Region-file recovery validates region contents before accepting a primary file, allowing recovery
  from backups when JSON parses but contains invalid region data.
- Scoped general interaction ray filtering to client-thread player/camera outline raycasts rather
  than affecting unrelated collision and integrated-server raycasts.
- Virtual entity skylight caches are invalidated by roof changes above their local section halo.
- Sodium Black boundary faces consistently use opaque texture coordinates. Cached UV pairs are
  published together and invalidated after model resource reloads.

### Performance and cleanup

- Stop collecting rebuild sections as soon as the configured threshold is exceeded; skip unloaded
  chunks and keep unchanged whitelist regions out of hidden-region-only updates.
- Skip inactive block-update lighting checks and classify collector sections only after cheaper
  loaded, distance, already-visited, and frustum checks.
- Reject non-finite boundary geometry and restore short-circuit boundary visibility checks.
- Conquest Reforged extension-toggle compatibility remains incomplete and is deferred.

### Tests

- Added regression coverage for whitelist/plot transitions, hidden-only updates, inactive selections,
  invalid backup candidates, non-finite planes, and the revised skylight/reload policies.

## 1.8.10

### Performance

- Added immediate Normal-mode fast paths so Vanilla, Sodium, and Indium skip transformed boundary
  geometry analysis unless Black or Culled boundaries actually need it.
- Reduced allocations and duplicate plane calculations in transformed boundary checks, cached the
  black-boundary texture coordinates across frames, and refreshes that cache after resource reloads.
- Increased the render-thread section-classification cache and avoids scanning very large flat
  traversal indexes when a camera-local region walk is cheaper. This targets the Sodium collector
  hotspot found in profiling on large modded worlds.
- Removed the redundant virtual-skylight radius argument from its influence cache while preserving
  vertically unbounded skylight checks required by hide regions.

### Changed

- Replaced boxed section de-duplication with a primitive long set when building traversal indexes.
- Settings are now written through a temporary file and atomically replaced where supported, with
  automatic backup recovery matching region configuration files.
- Release validation now requires tagged commits to include the current `main` history as well as a
  matching project version. The previously separate `main` documentation history is merged here.

### Tests

- Added explicit boundary-policy coverage for visible-to-filtered cuts, hidden boundaries, fully
  visible neighbors, invisible owners, fractional geometry, and translated cut planes.

## 1.8.9

### Fixed

- Restricted transformed-model boundary handling to quads lying exactly on the region cut plane.
  Conquest Reforged extension toggles now affect only the relevant two-dimensional boundary face
  instead of coloring or culling the complete duplicated three-dimensional extension.

## 1.8.8

### Fixed

- Applied Black and Culled boundary handling after Fabric Renderer/Indium quad transforms, allowing
  Conquest Reforged extension-toggle geometry to be detected at its final translated position.
- Debug boxes now show every saved region, including inactive and hidden regions, as well as the
  current temporary PlotSquared selection.

## 1.8.7

### Fixed

- Fixed a startup crash with Create/Flywheel caused by an incompatible internal reset-method
  parameter in 1.8.6.
- Made the optional Flywheel reset compatibility path fail safely. If a future Flywheel version
  changes the method again, SR falls back to a full renderer reload instead of preventing the game
  from starting.

## 1.8.6

### Added

- Added settings for the full-renderer reload threshold, the default `/sr p` minimum Y value, and
  whether interaction filtering remains active while region rendering is switched off.
- Added boundary handling for model geometry extending beyond its owning block, including Conquest
  Reforged extension toggles in Black and Culled modes on every axis.

### Changed

- Raised the default localized-rebuild threshold to 8,192 affected sections and the relative
  fallback to 85%. The section limit can be set from 256 to 65,536 in the settings screen.
- Replaced Flywheel/Create's unconditional full Minecraft renderer reload on every visibility
  change with localized terrain rebuilds and a dedicated Flywheel visualization reset.
- Replaced the bundled icon with the smaller, more tightly cropped version supplied by the maintainer.
- Removed the Codex contributor credit from project metadata.

### Performance

- Preserved the render no-op path when no render or hide region is active. Interaction filtering
  while rendering is inactive is opt-in and defaults to Vanilla behavior, so merely storing regions
  does not add an active interaction policy.

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
- Added optional X/Z margins to saved plot regions through `/sr p s NAME minY maxY xzMargin`.

### Fixed

- Removed stale light-cache entries on chunk unload and isolated cached heights by snapshot generation.
- Rebuilt the full 14-block virtual-light influence area after visibility or occluder changes.
- Recovered invalid configs from backups and uniquely identified singleplayer saves while migrating legacy data.
- Rebuilt visible hidden overrides correctly when their region is deleted.
- Guarded region block counts against integer overflow and applied virtual direct skylight to entities and block entities.

### Build

- Pinned Fabric Loom and rejected release tags that do not exactly match `mod_version`.
- Added regression coverage for snapshots, backup recovery, rebuild ranges, region deletion, traversal keys, and cache cleanup.

## 1.7.3

### Changed

- Indexed region lookups spatially so large region collections no longer require linear scans in render hotpaths.
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

- Project metadata now identifies `cepreni-pengwing` as maintainer.
- Compatibility and known limitations are documented more precisely.
- Loader metadata now matches the tested Minecraft 1.20.1 and Sodium 0.5.8+ baseline.
- Licensing changed from MIT to GPL-3.0-only.

## 1.7.0

### Added

- Optional Selective Render Plots integration through `/sr plot`.

### Fixed

- Plot mode state now remains consistent with normal render-group toggles.
