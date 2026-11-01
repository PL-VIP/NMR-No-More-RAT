# Multi-version builds

`versions.json` pins each build target independently. Stable releases only; snapshots are not included. The latest stable target checked against Fabric metadata on 2026-10-30 is 26.3.

Build with JDK 25 available for Gradle and JDK 21 installed for the shared test toolchain:

```sh
./gradlew :fabric:build -Pmc=1.20.1
./gradlew :fabric:build -Pmc=26.3
```

On Windows, use `gradlew.bat`; on Linux and macOS, use `./gradlew`. The cross-platform full-matrix command is `java tools/BuildMatrix.java --java-home <JDK-25-directory>`.

Each build writes to `fabric/build/<minecraft>/libs/`. Do not install two NMR variants together. Every variant has an exact Minecraft dependency; a build for one patch release does not imply compatibility with another.

`tools/BuildMatrix.java` collects successful builds under `build/releases/<mod-version>/`, generates `SHA256SUMS.txt`, and creates a ZIP when building the full matrix. It uses only the Java runtime and the repository's Gradle wrapper, so it works on Windows, Linux and macOS. Development clients use separate `fabric/run/<minecraft>/` directories:

```sh
./gradlew :fabric:runClient -Pmc=26.3
```

`runClient` selects the target's Java runtime explicitly. To launch a 1.20.1–1.20.4 development client, also install Java 17 or point Gradle's `org.gradle.java.installations.paths` property at an existing Java 17 installation.

Minecraft 1.20.1–1.20.4 uses Java 17, 1.20.5–1.21.11 uses Java 21, and 26.x uses Java 25. The security core and bootstrap bridge retain Java 17 bytecode. Minecraft 26.x UI sources are generated from the shared implementation using the explicit mappings in `gradle/modern-sources.gradle`.

## Verification

## 1.2.4 verification

On 2026-11-01, all 23 targets passed `:fabric:build` for **1.2.4**, including unit tests, Fabric metadata checks and `verifyReleaseJar`. The release folder contains one exact-target JAR per supported Minecraft version, `SHA256SUMS.txt`, and a combined ZIP. Each JAR was checked for NMR version `1.2.4`, its exact Minecraft dependency, and an embedded agent.

The 1.2.4 release adds the scoped `config/<mod-id>` compatibility allowance used only while `FILE_WRITE` is configured as **Ask**. It is shared by every target in this matrix.

Earlier 1.2.0 verification details below are historical context.

Shared checks passed: 27 core tests, 12 agent tests, and the real-agent/class-loader/loopback consent probes. One optional external-sample test was skipped. Standalone blocking probes also passed on Java 17 and Java 25.

Client checks use clean development profiles on Windows, not a Modrinth launcher install or a full gameplay session. On 2026-10-31, Computer Use successfully verified the title menu, ACTIVE agent status, dashboard and HTTP permission preview in a small window (approximately 570 × 350 pixels) on 1.20.1, 1.21.11 and 26.3. Text and decision buttons were visible. The preview sends no requests and saves no permissions. This completes the visual check that was blocked by the desktop tool failure on the previous day.

| Minecraft | Runtime | Local startup result |
| --- | --- | --- |
| 1.20.1 | Java 17 | Startup, ACTIVE dashboard and permission preview visually verified on 2026-10-31. |
| 1.21.11 | Java 21 | Startup, ACTIVE dashboard and permission preview visually verified on 2026-10-31. |
| 26.1.2 | Java 25 | Agent active; resources and sound initialized on 2026-10-30; startup check only. |
| 26.3 | Java 25 | Startup, ACTIVE dashboard and permission preview visually verified on 2026-10-31. |

The test clients were stopped after the startup checks. CI preserves separate logs/artifacts per target and never treats a failed build as successful.

## Publishing

Publish each JAR with its exact Minecraft target, rather than marking one JAR as compatible with the entire range. The combined ZIP is a convenience download, not a file to place in `mods`. Include the agent-update instructions from the README.
