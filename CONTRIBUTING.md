# Contributing

Use Java 17 for Minecraft 1.20.1, Java 21 for Minecraft 1.21.1, and Java 25 for Minecraft 26.2:

```bash
./gradlew build
./gradlew -p versions/mc1.21.1 build
./versions/mc26.2/gradlew -p versions/mc26.2 build
```

Keep changes focused, preserve client-only behavior, and do not alter chunk loading, networking,
collision, or world state unless the change explicitly introduces and documents a new mode.

Rendering changes should be tested with Minecraft 1.20.1/Sodium 0.5.13,
Minecraft 1.21.1/Sodium 0.8.13, and Minecraft 26.2/Sodium 0.9.2. Test with Iris shaders both
enabled and disabled. Include reproduction steps, logs,
screenshots, and the exact mod list for
visual or compatibility bugs. Add unit tests for changes to regions, configuration migration,
or region-group behavior.

GitHub's client smoke workflow starts every supported build with its matching Sodium version,
joins a singleplayer world, and uploads logs, crash reports, and screenshots. It catches startup,
Mixin, and world-entry regressions but does not replace manual visual, shader, mod-compatibility,
or performance testing.

For 1.9.x, preserve the inactive fast paths and the configurable rebuild threshold for visibility
changes. Test first/last-region transitions, plot clear, and hidden-only changes. Do not present
deferred Conquest extension-boundary support or unmeasured performance gains as verified fixes.
Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).

Shared pure logic and matching tests listed in `.github/scripts/source-parity.json` must stay in sync across their version roots. Run `python .github/scripts/check-source-parity.py` before publishing; mappings and renderer APIs remain version-specific. Update the manifest explicitly if a listed file needs an intentional version difference.

## Runtime and optional visual checks

Every main push tests startup, world join, compatibility profiles and runtime assertions for all
three versions. Screenshots are off for ordinary logic, docs and version changes. Menu changes
automatically select only the UI screenshot scenarios on baseline profiles. No screenshots are
created by the compatibility profiles.

Before a full release, explicitly dispatch **Client Smoke Test** with its **visual** input enabled
to run the complete 23-case world/UI suite, including boundary colors, opacity, textures and Rainbow.
The visual selection policy is tested by `.github/scripts/test-visual-selection.py`.

Download a baseline artifact and open `index.html` when a visual run was selected. Reports and
images stay in expiring GitHub Actions artifacts (seven days), never in Git or release assets.
Ordinary runs retain logs. Full images need inspection only when relevant to the change.

Visual fixtures are destructive only in disposable integrated-server CI worlds, enabled by
`-Dselectiverender.visual.smoke=true`. Do not enable them in an existing save. Automatic menu runs
also set `-Dselectiverender.visual.uiOnly=true`. The suite refuses multiplayer worlds.

Runtime assertions compare saved and temporary plot lighting/geometry under each skylight mode,
check hidden interaction priority, grid setup/persistence and plot-clear aliases. Visual assertions
check widget bounds, palette visibility, region reset, normalized grid values and automatic saving.
These checks do not certify every resource pack, shader or far-distance camera angle.
