# No More RAT

<p align="center">
  <img src="fabric/src/main/resources/assets/no_more_rat/icon.png" alt="A crossed-out rat — No More RAT" width="160" />
</p>

<p align="center">
  <strong>Know what your Minecraft mods try to do — before it happens.</strong><br />
  A client-side security firewall for Fabric 1.21.4 and Java 21.
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

- Minecraft **1.21.4**
- Java **21**
- Fabric Loader **0.16.10+**
- Fabric API **0.118.0+1.21.4+**

## Install

1. Download or build `no-more-rat-1.1.0.jar` and place it in your instance's `mods` folder.
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

From the project folder, run:

```powershell
.\tools\install-agent.ps1
```

It creates a dated backup of `launcher_profiles.json` and updates matching Fabric 1.21.4 profiles. To undo it:

```powershell
.\tools\remove-agent.ps1
```

## Choose a protection mode

Open **No More RAT Security → Settings**. Changes are drafts until you press **Save**, then apply before the next protected operation — no game restart is needed.

| Profile | Behaviour |
| --- | --- |
| **Recommended protection** | Asks for supported network/file changes; blocks process creation, dynamic code, and listening ports; blocks unidentified sources. |
| **Ask before each operation** | Asks for every supported category. A missing UI, timeout, or render-thread request still fails closed. |
| **Block all by default** | Blocks supported operations unless a saved rule explicitly permits them. |

Under **Operation controls**, override each category individually with **Default**, **Ask**, **Block**, or **Allow**. Existing saved rules have priority over profile and category choices. An explicit **Ask** for a category can override strict mode's default block; broad high-risk allow rules remain guarded. Choosing **Trust this action** records only the requesting mod's exact JAR hash, operation, and target — it is not blanket trust for a mod.

Settings also control the decision timeout, in-memory event buffer, and whether allowed operations are logged. Denials are always logged. The disk log rotates at 5 MiB.

## HTTP inspection before sending

Supported JDK HTTP calls now pass request metadata to the permission screen: host and port, method, declared content type, and body byte length when safely available. Warnings flag credential-like header names, unencrypted HTTP, private URL components, and archive/executable filename extensions. These are indicators, **not malware detection**. Unknown or uninspected data is explicitly labelled; the inspector never subscribes to a body publisher or reads a request stream.

The paginated prompt keeps decision buttons visible and asks for confirmation before remembering an allow rule. **Preview HTTP warning** on the dashboard opens a synthetic example without sending a request or saving permissions.

**Compatibility change:** `blockAutomaticHttpRedirects` defaults to `true`, including when loading older configurations. For supported JDK clients whose redirect policy can be read, it rejects the original request before transmission if automatic redirects are enabled, even when an allow rule exists. It does not inspect each redirect hop. `HttpURLConnection` commonly enables redirects by default. Prefer disabling automatic redirects in the calling mod; disabling this protection in NMR settings permits unchecked redirect destinations. Custom clients and APIs with unknown redirect policy are not covered by this safeguard.

HTTP paths, query values, user information, and fragments are hidden in new event targets and warning screens. Newly remembered targets use a SHA-256 fingerprint instead of storing the raw URL. Existing rules/logs are not rewritten. A remembered HTTP permission covers the same artifact, operation and target, **not a particular method, header set or body**; use **Allow once** for sensitive requests.

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

With Java 21 installed:

```powershell
.\gradlew.bat clean build
```

Build outputs:

```text
fabric/build/libs/no-more-rat-1.1.0.jar       Fabric mod with embedded agent
agent/build/libs/no-more-rat-agent-1.1.0.jar  Standalone Java agent
```

The automated checks cover policy rules, changed artifact hashes, configuration persistence, bytecode transformation, class-loader isolation, and pre-operation blocking for supported HTTP, TCP, file-write, process, and reflection paths. Details are in the [test matrix](docs/TEST_MATRIX.md).

## Documentation

- [Architecture and threat model](docs/ARCHITECTURE.md)
- [Security limitations and known gaps](docs/SECURITY_LIMITATIONS.md)
- [Test matrix](docs/TEST_MATRIX.md)
- [Contributing](CONTRIBUTING.md)
- [Security reporting policy](SECURITY.md)

## License

No More RAT is licensed under the [Mozilla Public License 2.0](LICENSE) (`MPL-2.0`). If you distribute modified MPL-covered source files, make those modified files available under MPL-2.0. Separate files may use another compatible license.
