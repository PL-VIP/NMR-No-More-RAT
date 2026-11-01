# Architecture and threat model

## Trust boundary

The protected assets are the user's data and operating system. Mod code and bundled mod libraries are treated as untrusted. Minecraft, Mojang, Fabric, Mixin, MixinExtras, the agent's libraries, and No More RAT itself are excluded from transformation to keep the client bootable and prevent instrumentation recursion. This is an explicit trade-off: malicious code injected directly into an excluded class may bypass enforcement.

The transformer requires a class to come from a path containing a `mods` or `.fabric` directory, independent of the launcher's `user.dir`. This matters for launchers such as Modrinth that relaunch Minecraft from a working directory outside the selected profile. Classes created by an untrusted custom loader without a `CodeSource` are also transformed. Launcher and bootstrap packages are explicitly excluded.

## Decision flow

1. A `java.lang.instrument.ClassFileTransformer` receives a mod class while it is loading.
2. ASM finds a call to a controlled API and inserts `GuardBridge.before(...)` immediately before it.
3. The stack resolver selects the first class outside the JDK and No More RAT, obtains its `CodeSource`, reads `fabric.mod.json` from its JAR, and calculates the artifact's SHA-256.
4. The policy engine evaluates rules from most specific to least specific, preserving stored order. Rules are snapshotted for each evaluation.
5. `DENY` throws `BlockedOperationException` before the original instruction. `ASK` uses the asynchronous Minecraft UI, except supported configuration writes from high-confidence sources: those use an independent desktop event thread so startup can wait for consent. This requires a standard `config/no-more-rat` agent directory and a target within `config`, outside NMR's own data. Closing, timeout, unavailable desktop or errors deny the call. Other render-thread requests fail closed. Explicit `DENY` never opens the consent dialog.
6. A redacted event is appended to a rotating JSONL log.

For `HttpClient.send/sendAsync`, the transformer preserves the operand stack, passes both the client and request to the hook, and restores the receiver and arguments only after the decision. `HttpInspection` extracts bounded metadata without subscribing to the body or reading a stream. For standard JDK `HttpURLConnection`, it reads request properties and redirect policy, never response headers or response content length (which could connect). Known NIO file writes similarly expose their destination path before execution.

The automatic-redirect safeguard is evaluated before saved rules: known enabled redirect policies are denied while `blockAutomaticHttpRedirects` is true. Unknown policies remain unknown and follow ordinary policy evaluation. This does not implement per-hop validation.

Before registering the transformer, the agent installs a tiny JDK-only guard JAR through `Instrumentation.appendToBootstrapClassLoaderSearch`. Instrumented classes call this shared bootstrap bridge, which synchronously dispatches to the agent's policy engine. Fabric's Knot loader deliberately cannot resolve implementation classes from the agent JAR: using direct `GuardHooks` references here caused `NoClassDefFoundError` during startup. ASM, Gson and the policy engine remain outside the bootstrap JAR. Packaging tests verify that the bridge stays nested and dependencies stay relocated.

The GUI bridge also has a JDK-only path through `System.getProperties()`. This lets the agent and Fabric classes exchange a decision even when Knot creates separate copies of core classes in different class loaders.

HTTP metadata crosses that bridge as a `Map<String, String>`, not a class-loader-specific DTO. Matching mod and agent builds are required. Raw request targets remain internal to policy matching; HTTP event and UI targets are sanitized. New GUI rules store `sha256:` exact-target fingerprints, while legacy wildcard patterns remain supported. A target fingerprint does not bind HTTP methods, headers or bodies.

Asynchronous attribution normally recovers context from a lambda/callback class and its `ProtectionDomain`, including calls running in an executor. If a library completely replaces the callback, the source may be reported as unknown; the project does not invent attribution certainty.

## Instrumented call sites

No More RAT instruments **call sites in mod code**, not JDK bootstrap classes. This avoids Mixin restrictions on JDK modules and does not weaken TLS verification.

| Area | Bytecode owner | Methods |
|---|---|---|
| URL/HTTP | `java/net/URL` | `openConnection`, `openStream` |
| URLConnection | `java/net/URLConnection`, `java/net/HttpURLConnection` | `connect`, `getInputStream`, `getOutputStream`, `getResponseCode`, `getResponseMessage`, `getHeaderFields`, `getContent`, `getErrorStream` |
| JDK HTTP | `java/net/http/HttpClient` | `send`, `sendAsync` |
| HTTP clients | `okhttp3/Call`, `org/apache/hc/client5/http/*` | `execute`, `enqueue` |
| TCP | `java/net/Socket`, `java/nio/channels/SocketChannel`, `AsynchronousSocketChannel` | `connect`, `open` |
| TCP server | `java/net/ServerSocket` | `bind`, constructor |
| UDP | `java/net/DatagramSocket`, `java/nio/channels/DatagramChannel` | `connect`, `send`, `bind`, `open` |
| Netty from mod code | `io/netty/channel/*` | `connect`, `bind`, `writeAndFlush` |
| Processes | `java/lang/Runtime`, `java/lang/ProcessBuilder` | `exec`, `start` |
| Files | `java/nio/file/Files` | `write`, `writeString`, `copy`, `move`, `delete`, `deleteIfExists`, `createFile`, `createDirectory`, `createDirectories`, `newOutputStream`, `setAttribute` |
| File channels | `java/nio/channels/FileChannel` | `open` |
| Reflection/dynamic code | `Method`, `Constructor`, `AccessibleObject`, `Field`, `MethodHandles$Lookup`, `URLClassLoader`, `ClassLoader` | `invoke`, `newInstance`, `setAccessible`, `defineClass`, URLClassLoader constructor |

`FileOutputStream` and `RandomAccessFile` are not claimed as intercepted. Safely transforming constructor calls requires handling uninitialized JVM references. An audit test records this limitation instead of claiming coverage that does not exist. The listed NIO entry points currently provide file-write protection.

## Rules

Identity (`modId`, version, SHA-256), operation, target, and process permission are independent dimensions. Rule order matters; the GUI inserts a new rule at the beginning. In strict mode, a broad allow rule without an artifact hash or operation does not match a high-risk operation.

## Recovery

If a bad rule prevents startup, close the client and move `config/no-more-rat/rules.json` to a safe backup location. The agent creates a fail-closed default configuration. Removing one rule from the JSON file does not disable the agent.
