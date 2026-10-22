# Test matrix

## Automated checks

| Mechanism | Test | Expected result |
|---|---|---|
| HTTP/domain rule | `PolicyEngineTest.exactArtifactAndOperationCanBeAllowed` | the exact artifact and domain are allowed |
| Changed JAR | `changedJarHashInvalidatesTrust` | a different SHA-256 invalidates the rule |
| Process permission | `processAllowNeedsExplicitProcessPermission` | a general allow does not permit process creation |
| Secret redaction | `SecretRedactorTest` | tokens and passwords do not appear in the result |
| Import/export | `PolicyStoreTest` | configuration values survive serialization |
| Protection controls | `ConfigControlsTest` | category overrides and presets apply with documented precedence; legacy settings remain compatible; saving preferences preserves rules; invalid settings cannot overwrite the configuration |
| Permission risk explanation | `SecurityRequestRiskTest` | user-facing risk levels classify supported operations without changing policy decisions |
| Firewall data path | `ProtectedPathTest` | paths inside `config/no-more-rat` are recognized as protected from supported untrusted file writes |
| Branding and translations | `MetadataAndLanguageTest` | metadata links to the project, the square icon is readable, and English/Polish keys and placeholder counts match |
| Instrumentation scope | `SecurityTransformerTest` | nested Modrinth profile paths and source-less custom-loader classes receive guards; launcher code outside mod paths is ignored |
| Denial before execution | `TransformedDenialIntegrationTest` | the call throws `BlockedOperationException` before `start()` |
| Real `-javaagent` startup | `agent:smokeAgent` | premain activates, transforms the fixture, and blocks process creation |
| Agent classpath isolation | `agent:verifyAgentIsolation`, `agent:smokeFabricClasspath` | the packaged agent exposes no original ASM/Gson classes and passes Fabric 0.19.5's duplicate-ASM check |
| Real Fabric class-loader boundary | `agent:smokeKnotIsolation` | Knot rejects direct access to agent implementation classes, resolves the bootstrap guard, and blocks operations from a fixture JAR in `mods` with production instrumentation scope |
| HTTP before transmission | `agent:smokeAgent`, `agent:smokeKnotIsolation` | `HttpClient` GET/POST is denied; instrumented `HttpURLConnection` stream and response calls use a probe that fails if the original method executes, so an earlier `URL.openConnection` check cannot mask missing guards |
| HTTP/TCP/UDP/NIO/reflection entry points | `CoverageAuditTest` | classification matches the architecture table |
| Local suspicious sample (opt-in) | `SampleJarStaticRegressionTest` | without loading its classes, transformed bytecode contains HTTP, file-write, process, and reflection guards |
| Alternative paths | `explicitlyRevealsKnownUninstrumentedAlternatives` | Unsafe and IO constructors remain explicitly documented gaps |
| Unknown source/timeout | `AgentEnforcementTest` | failure to obtain a decision is denied |

## Verified by the build

- Java sources compile with release 21;
- the `TitleScreen.init` Mixin and GUI signatures compile against Yarn 1.21.4+build.8;
- Loom remaps the final mod;
- the agent is a shaded JAR with `Premain-Class` and embedded ASM, Gson, and core classes;
- the final Fabric JAR contains the English and Polish language files and embedded agent.

## Local UI verification (2026-10-22)

A clean development client (Minecraft 1.21.4, Fabric Loader 0.19.5, Java 21) started with the security agent active. The dashboard, profile settings, operation controls, and operation pagination were visually inspected in a small window. No policy values were changed during this UI check. The client shut down normally. This was not a run of untrusted mods and does not demonstrate complete sandboxing.

## Runtime coverage limits

CI does not yet run a complete Minecraft client against real HTTPS/TCP/UDP services or a local TLS server. A successful compilation is not presented as a full client runtime test. JNI and Unsafe are outside the stated boundary, so there is no bypass-prevention test for them; the audit test instead confirms that the classifier does not claim to intercept those paths. Completed-download SHA-256 testing is absent because that feature is not implemented. The local sample regression is opt-in because malware samples must never be committed to the repository; run it with `-Dnmr.sampleJar=<path>`.

For a manual test, run `fabric:runClient`, confirm that the UI says **Security agent: ACTIVE**, and use a separate test mod that invokes each controlled API. Never use real tokens or private data in security tests.
