# No More RAT 1.2.4

## Highlights

- Build and verify exact Fabric artifacts for every supported Minecraft release: 1.20.1–1.21.11 and 26.1–26.3.
- Add a configurable compatibility allowance for verified mods writing only below their own `config/<mod-id>` directory while file writes are set to **Ask**.
- Keep No More RAT data, other mods' configuration, and all paths outside `config` protected; explicit deny rules continue to take priority.
- Add the **Allow a mod's own config files** control to Settings, with English and Polish explanations.

## Why this exists

Fabric can invoke a mod's configuration setup before Minecraft has a usable permission screen. Denying that narrow, expected configuration setup crashes many otherwise harmless mods before a user could respond. The allowance is deliberately limited and can be disabled for strict blocking.

## Distribution

Use only the JAR matching the exact Minecraft version. Update the embedded/standalone Java agent together with the Fabric JAR. The `build/releases/1.2.4` folder includes individual JARs, SHA-256 checksums and the combined ZIP.
