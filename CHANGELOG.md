# Changelog

All notable changes to Selective Render are documented here.

## 1.7.2-test.3

### Fixed

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
