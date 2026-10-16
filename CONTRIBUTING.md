# Contributing

## Project layout

| Path | Contents |
|---|---|
| `versions/mc1.20.1`, `mc1.21.1` | Yarn-mapped targets (Java 17 / 21) |
| `versions/mc26.1`, `mc26.2`, `mc26.3` | Unobfuscated targets (Java 25); `mc26.1` serves 26.1, 26.1.1 and 26.1.2 |
| `shared/common` | Code, tests and assets identical for every target |
| `shared/legacy` | Code identical for 1.20.1 and 1.21.1 |
| `shared/modern` | Code identical for all 26.x targets |
| `gradle/target.gradle` | Build logic applied to every target |

Each target directory only contains what differs for that Minecraft version, plus its `gradle.properties` with the dependency versions. Shared directories are added to every matching target's source set, so a class must exist in exactly one place per target. When a shared class needs a version-specific change, move it from `shared/` into each affected `versions/<target>` directory.

## Building

Run Gradle with Java 21 or newer. Target toolchains (Java 17, 21, 25) are detected or downloaded automatically.

```bash
./gradlew build                 # all targets, including tests
./gradlew :mc1.21.1:build       # one target
./gradlew :mc26.2:runClient     # development client with Sodium
```

Dependency versions can be overridden per target for compatibility checks, e.g. `-Pmc1.20.1.sodium_version=mc1.20.1-0.5.8`.

Keep changes focused, preserve client-only behavior, and do not alter chunk loading, networking, collision, or world state unless the change explicitly introduces and documents a new mode. Add unit tests for changes to regions, configuration migration, or region-group behavior.

## Continuous integration and releases

- **Build** compiles and tests every target and checks older Sodium versions for 1.20.1 and 26.1.x.
- **Client Smoke Test** starts every target in a real client, joins a singleplayer world and runs runtime assertions, including WorldEdit and connected-texture profiles. Menu changes automatically add UI screenshots; dispatch the workflow with **visual** enabled for the complete 23-case world/UI suite before a larger release. Reports and images stay in Actions artifacts for seven days and are never committed.
- **Release** runs after a successful smoke test on `main`. If `mod_version` in `gradle.properties` has no tag yet, it builds all targets and publishes a GitHub prerelease with all five JARs. Raising `mod_version` on `main` is therefore the release trigger.

Visual fixtures are destructive only in disposable CI worlds enabled by `-Dselectiverender.visual.smoke=true`. Do not enable them in an existing save.

## Structural block catalog

Regenerate the ID-only catalog with `.github/scripts/generate-beam-catalog.py --conquest PATH --architects PATH` using the installed mod JARs, then run `.github/scripts/test-beam-catalog.py`. Models are inspected only to exclude volume-building shapes; no third-party assets are distributed.

## Changelog

Add a short bullet list under `## <version>` in `CHANGELOG.md` when raising `mod_version`. The release workflow copies these bullets into the release notes, so write them for players.

Contact: [pengwing.ac@gmail.com](mailto:pengwing.ac@gmail.com).
