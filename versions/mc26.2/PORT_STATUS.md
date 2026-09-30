# Minecraft 26.2 port

This is the Minecraft 26.2 target shipped with Selective Render 1.9.20. It remains a prerelease
target pending broader real-world region and mod-compatibility testing.

## Scope

- SR only; keep SRP unchanged.
- Preserve the existing 1.20.1 and 1.21.1 implementations while publishing a separate,
  version-labelled 26.2 JAR in CI.
- Base: SR 1.9.20. This directory is a separate Gradle build using the root release version.

## Saved progress

- Migrated a copy of the 1.20.1 client sources from Yarn to Mojang names using Loom's
  `migrateMappings --input src/client/java --output versions/mc26.2/src/client/java
  --mappings net.minecraft:mappings:1.20.1`. Original sources unchanged.
- Resolved Minecraft 26.2 / Java 25, Loom 1.17.20 / Gradle 9.5.1, Fabric Loader 0.19.5,
  Fabric API 0.159.0+26.2, Sodium mc26.2-0.9.2-fabric, Mod Menu 20.0.1.
- Updated many names, GUI extraction entry points, key categories, height access (26.2 maximum Y
  is inclusive), reload APIs, boundary vertex access, and light dampening calls.
- Full build and all 24 inherited regression tests pass on September 11.
- The Sodium boundary hook no longer shadows the block-position field moved by Sodium 0.9.2.
- Rewritten vanilla/Sodium boundary geometry, block/fluid filtering, block-entity extraction,
  level extraction, lighting coordinates and debug gizmos against actual 26.2 bytecode.
- Typed plot networking compiles and preserves the protocol-2 wire format; SRP is untouched.
- Sodium traversal currently conservatively disables occlusion only during active whitelist
  filtering, retaining native cancellation/read phases/visitor handling. The 1.20.1 direct
  section collection optimization is NOT ported; assess active-mode performance before release.
- Target selection is now Minecraft.pick; final validation is migrated, but skipping disallowed
  entities before raycast selection still needs a hook in the new Player targeting path.
- The 26.2 manifest/resources and dedicated Gradle 9.5.1 wrapper are present. CI and tagged
  prereleases build/upload distinct 1.20.1, 1.21.1, and 26.2 JARs.
- SR 1.9.3 through 1.9.8 settings, interaction, keybind, player visibility, configurable virtual
  skylight, and amortized entity-light cache behavior are included.
- Axiom 5.5.0 block and fluid raycasts use the same interaction policy as vanilla targeting.
- Debug region boxes use Minecraft's always-on-top gizmo path and remain visible through terrain.

## Continue here

1. Test region activation/deactivation in a 26.2 world, including a camera outside the selected region.
2. Test Normal, Black, and Culled boundaries; slabs/stairs and virtual skylight; entities, block
   entities, fluids, particles, player visibility, and interaction modes.
3. Profile the conservative active-filtering Sodium occlusion path. Inactive/no-op rendering retains
   Sodium's original path.
4. Optional integrations that do not yet have verified 26.2 builds (CanvasBlocks, BelieveMod,
   Flywheel) are intentionally not claimed by the 26.2 artifact.

## Local tooling

- JAVA_HOME: C:/Program Files/Java/jdk-25.0.3
- GRADLE_USER_HOME: C:/Users/nicol/.gradle
- Gradle: root build/port-tooling/gradle-9.5.1/bin/gradle.bat -p versions/mc26.2 compileClientJava
- Minecraft JAR: C:/Users/nicol/.gradle/caches/fabric-loom/26.2/minecraft-client.jar
- Cached dependencies are under C:/Users/nicol/.gradle/caches/modules-2/files-2.1.
- The root clean task removes build/port-tooling; generate a dedicated wrapper before relying on it.
- Preserve unrelated deleted docs/images/selective-render-preview.png and local PROJECT_HANDOFF.md.
