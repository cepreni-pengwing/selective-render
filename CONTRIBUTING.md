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
or preset-group behavior.

GitHub's client smoke workflow starts every supported build with its matching Sodium version,
joins a singleplayer world, and uploads logs, crash reports, and screenshots. It catches startup,
Mixin, and world-entry regressions but does not replace manual visual, shader, mod-compatibility,
or performance testing.

For 1.9.x, preserve the inactive fast paths and the configurable rebuild threshold for visibility
changes. Test first/last-region transitions, plot clear, and hidden-only changes. Do not present
deferred Conquest extension-boundary support or unmeasured performance gains as verified fixes.
Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).
