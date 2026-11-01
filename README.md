# No More RAT

<p align="center">
  <img src="fabric/src/main/resources/assets/no_more_rat/icon.png" alt="A crossed-out rat — No More RAT" width="160" />
</p>

<p align="center">
  <strong>Know what your Minecraft mods try to do - before it happens.</strong><br />
  A client-side security firewall for Fabric, with version-specific builds from Minecraft 1.20.1 to 26.3.
</p>

<p align="center">
  <a href="https://github.com/PL-VIP/NMR-No-More-RAT">Source code</a> ·
  <a href="https://github.com/PL-VIP/NMR-No-More-RAT/issues">Report an issue</a> ·
  <a href="docs/SECURITY_LIMITATIONS.md">Security limitations</a>
</p>

> [!WARNING]
> No More RAT reduces risk from common Java-based malicious mods. It is **not** a process sandbox and cannot promise complete containment in the same JVM. Native code, JNI/JNA, `Unsafe`, additional Java agents, or code injected into excluded Minecraft/Fabric classes can bypass the controls. Use an operating-system firewall, a restricted user account, and ideally a sandbox or container for stronger isolation.

## What No More RAT does

No More RAT combines a Fabric interface with a Java agent. The agent inserts checks immediately before supported operations made by mod code, so a decision is made before the original operation proceeds.

| Protected category | Examples |
| --- | --- |
| Web requests | HTTP/HTTPS `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| Network sockets | Outgoing TCP, listening ports, supported UDP paths |
| File changes | Common NIO write operations |
| Program launches | Process creation |
| Dynamic behaviour | Selected reflection and dynamic-code entry points |

For each event, the interface can show the requesting mod, target, calling class, and (when available) the source JAR's SHA-256. You can **allow once**, remember an allow/block rule, or block the action. Local logs redact common secrets and no telemetry is sent.

## Requirements

- Minecraft **1.20.1–26.3**, using the JAR for your **exact** game version. See [build targets and verification](docs/MULTIVERSION.md).
- Java **17** for 1.20.1–1.20.4, **21** for 1.20.5–1.21.11, or **25** for 26.x.
- Fabric Loader **0.20.1+**.
- Fabric API for your game version; the minimum per target is pinned in [versions.json](versions.json).

## Install

1. Download the JAR for your exact Minecraft version from the [latest GitHub release](https://github.com/PL-VIP/NMR-No-More-RAT/releases/latest), or build `no-more-rat-<minecraft>-<mod-version>.jar` yourself. Place it in your instance's `mods` folder and install only one NMR variant.
2. Start Minecraft once. The mod extracts its bundled agent here:

   ```text
   config/no-more-rat/agent/no-more-rat-agent.jar
   ```

3. On the first-run screen, choose **Copy JVM argument**.
4. Close the game and add the copied argument to the instance's JVM arguments.
5. Start the game again, then choose **No More RAT Security** on the title screen. The dashboard must say **Security agent: ACTIVE**.

The argument looks like this:

```text
"-javaagent:C:\absolute path\to\config\no-more-rat\agent\no-more-rat-agent.jar"
```

Keep the quotation marks when your launcher has a single JVM-arguments text field and the path contains spaces. If the launcher stores arguments as separate list items, add the entire value as one item without splitting it at spaces.

### Modrinth App

Open the instance's **Options** or **Settings**, find Java/JVM arguments, then paste the exact value copied by No More RAT. The project does not directly edit Modrinth profiles because their internal settings format can vary.

### Official Minecraft Launcher

Open **Installations → your Fabric installation → Edit → More Options → JVM Arguments**. Append the argument copied by NMR, keeping the existing launcher arguments. To remove the agent, remove just that `-javaagent` argument.

### Updating from an older NMR version

Update both the mod and its extracted agent with the game closed. Install the matching new mod, open its JAR as a ZIP archive, and copy `nmr-agent/no-more-rat-agent.jar` over the instance's `config/no-more-rat/agent/no-more-rat-agent.jar`. Alternatively, use the standalone agent supplied with the same release and keep the filename expected by your existing JVM argument. Keep the `-javaagent` argument enabled. Replacing only the mod does not update the agent.

## Choose a protection mode

Open **No More RAT Security → Settings**. Changes are drafts until you press **Save**, then apply before the next protected operation — no game restart is needed.

| Profile | Behaviour |
| --- | --- |
| **Recommended protection** | Asks for supported network/file changes; blocks process creation, dynamic code, and listening ports; blocks unidentified sources. |
| **Ask before each operation** | Asks for every supported category once the game permission UI is available. Unavailable consent or a timeout fails closed. A verified mod's own configuration subtree has a narrowly scoped startup compatibility allowance. Other early render-thread requests fail closed. |
| **Block all by default** | Blocks supported operations unless a saved rule explicitly permits them. |

Under **Operation controls**, override each category individually with **Default**, **Ask**, **Block**, or **Allow**. Existing saved rules have priority over profile and category choices. An explicit **Ask** for a category can override strict mode's default block; broad high-risk allow rules remain guarded. Choosing **Trust this action** records only the requesting mod's exact JAR hash, operation, and target — it is not blanket trust for a mod.

Settings also control the decision timeout, in-memory event buffer, and whether allowed operations are logged. Denials are always logged. The disk log rotates at 5 MiB.

### Configuration during startup (1.2.4)

Ordinary file reads are not intercepted. Minecraft initializes client mods before it can safely render its own permission screen; blocking a normal `config/<mod-id>` write at that point makes many mods crash before the game opens.

Therefore, while the decision for `FILE_WRITE` is **Ask** and **Allow a mod's own config files** is enabled, NMR permits a verified mod to create or update only its own `config/<mod-id>` subtree. This is logged as `mod-config-compatibility`. It never permits No More RAT data, another mod's configuration, or paths outside `config`; explicit **Block** rules still win. Disable the setting in **Settings** for strict blocking.

All other supported early-start operations fail closed until NMR's in-game permission UI is available. Once it is available, permission prompts show the origin and requested action before it happens.

## HTTP inspection before sending

Supported JDK HTTP calls now pass request metadata to the permission screen: host and port, method, declared content type, and body byte length when safely available. Warnings flag credential-like header names, unencrypted HTTP, private URL components, and archive/executable filename extensions. These are indicators, **not malware detection**. Unknown or uninspected data is explicitly labelled; the inspector never subscribes to a body publisher or reads a request stream.

The paginated prompt keeps decision buttons visible and asks for confirmation before remembering an allow rule. **Preview HTTP warning** on the dashboard opens a synthetic example without sending a request or saving permissions.

**Compatibility change:** `blockAutomaticHttpRedirects` defaults to `true`, including when loading older configurations. For supported JDK clients whose redirect policy can be read, it rejects the original request before transmission if automatic redirects are enabled, even when an allow rule exists. It does not inspect each redirect hop. `HttpURLConnection` commonly enables redirects by default. Prefer disabling automatic redirects in the calling mod; disabling this protection in NMR settings permits unchecked redirect destinations. Custom clients and APIs with unknown redirect policy are not covered by this safeguard.

HTTP paths, query values, user information, and fragments are hidden in new event targets and warning screens. Discord webhook events retain the recognizable `/api/webhooks/<ID>/` route while hiding the final webhook token. Newly remembered targets use a SHA-256 fingerprint instead of storing the raw URL. Existing rules/logs are not rewritten. A remembered HTTP permission covers the same artifact, operation and target, **not a particular method, header set or body**; use **Allow once** for sensitive requests.

This update does not inspect body contents or responses, scan downloaded JAR/ZIP files, quarantine downloads, or modify TLS certificates. See [security limitations](docs/SECURITY_LIMITATIONS.md). Update both the Fabric mod and the Java agent together; the metadata bridge requires matching builds.

## Rules and local data

No More RAT stores data locally at `config/no-more-rat/`:

```text
rules.json                     Policy configuration and remembered decisions
events.jsonl                   Redacted, rotating event log
agent/no-more-rat-agent.jar    Extracted Java agent
```

Rules can match operation type, mod ID, version, artifact SHA-256, and target/domain pattern. In strict mode, a high-risk allow rule needs both the operation type and an exact artifact SHA-256. See [the example configuration](config/rules.example.json).

The user interface follows Minecraft's selected language. English (`en_us`) is the fallback; Polish (`pl_pl`) is included.

## Build from source

The project has one exact Fabric build per Minecraft version. Do not mark one JAR as compatible with the entire range.

### Prerequisites

- JDK **25** for Gradle and the 26.x targets. Pass its location explicitly when it is not your default JDK.
- Java **17** to run 1.20.1–1.20.4 clients, Java **21** for 1.20.5–1.21.11 clients, and Java **25** for 26.x clients.
- Internet access the first time Gradle downloads the target's Fabric/Minecraft dependencies.

### Build one exact Minecraft version

```text
# Windows
gradlew.bat :fabric:build -Pmc=1.21.4 -Dorg.gradle.java.home="C:\Program Files\Java\jdk-25.0.4"

# Linux / macOS
./gradlew :fabric:build -Pmc=1.21.4 -Dorg.gradle.java.home="/path/to/jdk-25"
```

Replace `1.21.4` with any value in [versions.json](versions.json). The result is written to:

```text
fabric/build/<minecraft>/libs/no-more-rat-<minecraft>-<mod-version>.jar  Fabric mod with embedded agent
agent/build/libs/no-more-rat-agent-<mod-version>.jar                    Standalone Java agent
```

### Build the complete release matrix

```text
# Windows, Linux and macOS
java tools/BuildMatrix.java --java-home "/path/to/jdk-25"

# Optional: build and collect only selected targets (does not create the combined ZIP)
java tools/BuildMatrix.java --java-home "/path/to/jdk-25" --versions 1.20.1 1.21.4 26.3
```

This builds all supported targets, stops on the first failed target, then creates:

```text
build/releases/1.2.4/
  no-more-rat-<minecraft>-1.2.4.jar   one JAR per exact Minecraft version
  SHA256SUMS.txt                       checksums for the individual JARs
  no-more-rat-1.2.4-multiversion.zip  convenience archive; do not place this ZIP in mods
```

For normal installation, use an exact-version JAR from [GitHub Releases](https://github.com/PL-VIP/NMR-No-More-RAT/releases). Official releases also include `SHA256SUMS.txt` for verifying the downloaded JARs. The combined ZIP is only a convenience archive and must not be placed in `mods`.

The automated checks cover policy rules, changed artifact hashes, configuration persistence, bytecode transformation, class-loader isolation, and pre-operation blocking for supported HTTP, TCP, file-write, process, and reflection paths. Details are in the [test matrix](docs/TEST_MATRIX.md).

## Documentation

- [Architecture and threat model](docs/ARCHITECTURE.md)
- [Security limitations and known gaps](docs/SECURITY_LIMITATIONS.md)
- [Test matrix](docs/TEST_MATRIX.md)
- [Multi-version builds and verification](docs/MULTIVERSION.md)
- [1.2.0 summary and release notes](docs/RELEASE-1.2.0.md)
- [1.2.1 startup consent fix](docs/RELEASE-1.2.1.md)
- [1.2.4 multi-version release notes](docs/RELEASE-1.2.4.md)
- [Contributing](CONTRIBUTING.md)
- [Security reporting policy](.github/SECURITY.md)

## License

No More RAT is licensed under the [Mozilla Public License 2.0](LICENSE) (`MPL-2.0`). If you distribute modified MPL-covered source files, make those modified files available under MPL-2.0. Separate files may use another compatible license.
