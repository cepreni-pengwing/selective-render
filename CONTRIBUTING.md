# Contributing

Use Java 17 and build the project before opening a pull request:

```bash
./gradlew build
```

Keep changes focused, preserve client-only behavior, and do not alter chunk loading, networking,
collision, or world state unless the change explicitly introduces and documents a new mode.

Rendering changes should be tested with Minecraft 1.20.1, Sodium 0.5.13, and both Iris shaders
enabled and disabled. Include reproduction steps, logs, screenshots, and the exact mod list for
visual or compatibility bugs. Add unit tests for changes to regions, configuration migration,
or preset-group behavior.

For 1.9.x, preserve the inactive fast paths and the configurable rebuild threshold for visibility
changes. Test first/last-region transitions, plot clear, and hidden-only changes. Do not present
deferred Conquest extension-boundary support or unmeasured performance gains as verified fixes.
Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).
