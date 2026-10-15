# mc26.1

Java 25 client target, with matching Fabric API, Sodium and Mod Menu dependencies.

One JAR supports Minecraft 26.1, 26.1.1 and 26.1.2; conditional Mixin bindings select Sodium 0.8.9 or 0.9.2 APIs. CI tests each patch and both Sodium lines.

Shared logic is checked by the source-parity manifest. Runtime startup, world join and assertions run in client-smoke.yml before publication.
