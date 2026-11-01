# No More RAT 1.2.0

## Commit summary

Add version-specific Fabric builds and harden permission cancellation.

## Release description

- Add separate Fabric downloads for Minecraft 1.20.1–26.3, using Java 17, 21 or 25 as required by the game.
- Fix permission cancellation: expired requests close correctly and late approvals cannot resume them.
- Improve GUI scaling with paginated setup instructions, readable text and preserved rule drafts when resizing.
- Fix truncated SHA-256 input in the rule editor and update bytecode support for newer Java versions.
- Add builds and package checks for every target, plus SHA-256 verification of Gradle downloads.

Install only the JAR matching your exact Minecraft version, and update the agent together with the mod. The Java-agent JVM argument is still required. NMR controls supported Java call sites; it is not an operating-system sandbox.

## Maintainer verification

See [MULTIVERSION.md](MULTIVERSION.md) for the completed checks and remaining runtime limits. Compilation alone is not a gameplay compatibility guarantee.
