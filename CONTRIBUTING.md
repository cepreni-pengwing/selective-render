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

## Automated visual checks

The client workflow runs a shared 21-case visual plan on the three baseline versions: a fixed
stone/dirt/glass/grass fixture, Normal, Color 0/25/50/75/100, Texture, Texture + Color,
Rainbow, Culled, Only stone, regional/global colors, and UI palette/inheritance/reset states.
Visible widget bounds and overlaps, cut/interior policy and reset behavior are asserted in-game.
`visual-report.py` checks actual framebuffer colors, opacity progression and Rainbow hue variation.

Open a Client Smoke Test run, download its version's `client-smoke-...-baseline` artifact,
extract it and open `index.html`. The offline gallery contains full-size images and machine results.
Images are never committed or attached to releases; artifacts expire after seven days. Extended
images run only on baseline and vanilla profiles to limit storage. Compatibility profiles retain their existing
runtime assertions and two palette images.

The fixture is destructive **only in disposable, integrated-server CI worlds**. It is disabled unless
`-Dselectiverender.visual.smoke=true` is explicitly set; ordinary users never run it. Do not enable it
in an existing save. The suite refuses multiplayer worlds. It keeps the runtime harness alive with
an unpaused scene screen, positions a detached camera, then restores the camera before completion.

These checks reduce manual work; they do not certify all shader/resource packs, far-distance depth
precision or real-pack FPS. Review unusual screenshot changes and test the affected real setup.

The 1.12 visual suite also covers partial/completed plot-grid menus and checks grass-side overlay suppression. All three Minecraft versions run the visual suite both with Sodium and with the vanilla renderer. Grid runtime assertions cover partial persistence, neighbor validation, settings reload and client-only plot selection.
