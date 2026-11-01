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
| Agent classpath isolation | `agent:verifyAgentIsolation`, `agent:smokeFabricClasspath` | the packaged agent exposes no original ASM/Gson classes and passes the configured Fabric Loader's duplicate-ASM check |
| Real Fabric class-loader boundary | `agent:smokeKnotIsolation` | Knot rejects direct access to agent implementation classes, resolves the bootstrap guard, and blocks operations from a fixture JAR in `mods` with production instrumentation scope |
| HTTP before transmission | `agent:smokeAgent`, `agent:smokeKnotIsolation` | `HttpClient` GET/POST is denied; instrumented `HttpURLConnection` stream and response calls use a probe that fails if the original method executes, so an earlier `URL.openConnection` check cannot mask missing guards |
| HTTP/TCP/UDP/NIO/reflection entry points | `CoverageAuditTest` | classification matches the architecture table |
| Local suspicious sample (opt-in) | `SampleJarStaticRegressionTest` | without loading its classes, transformed bytecode contains HTTP, file-write, process, and reflection guards |
| Alternative paths | `explicitlyRevealsKnownUninstrumentedAlternatives` | Unsafe and IO constructors remain explicitly documented gaps |
| Unknown source/timeout | `AgentEnforcementTest` | failure to obtain a decision is denied |
| HTTP metadata/privacy | `HttpInspectionTest` | inspection does not consume streams or invoke custom publishers; metadata omits credential values; URL display hides private components; exact-target fingerprints survive persistence without wildcard expansion |
| Redirect policy precedence | `HttpInspectionTest` | enabled automatic redirects are denied before a saved allow rule unless protection is disabled |
| Real loopback HTTP consent | `agent:smokeHttpConsent` | a real Java agent guards synchronous send and both asynchronous overloads; pending/denied/timed-out requests reach neither the server nor the body supplier; allow-once sends exactly once; enabled redirects are blocked before sending; logs omit synthetic secrets |
| Permission cancellation | `DecisionFuturesTest` | cancellation reaches the original UI future across both mapping stages; a late allow cannot complete the cancelled request |
| Per-version distribution | `fabric:verifyReleaseJar` | the packaged JAR has exact target metadata, compatible class-file versions, translations, Mixin and embedded agent |

## Verified by the build

- shared core/agent sources compile with release 17; client sources target the Java version in `versions.json`;
- GUI signatures compile against each target's actual Minecraft dependency;
- Loom remaps legacy builds and packages unobfuscated 26.x builds;
- the agent is a shaded JAR with `Premain-Class` and embedded ASM, Gson, and core classes;
- the final Fabric JAR contains the English and Polish language files and embedded agent.

## Local UI verification (2026-10-22)

A clean development client (Minecraft 1.21.4, Fabric Loader 0.19.5, Java 21) started with the security agent active. The dashboard, profile settings, operation controls, and operation pagination were visually inspected in a small window. No policy values were changed during this UI check. The client shut down normally. This was not a run of untrusted mods and does not demonstrate complete sandboxing.

## Runtime coverage limits

On 2026-10-31, Computer Use verified startup, the ACTIVE agent dashboard and the synthetic HTTP permission preview on Minecraft 1.20.1 (Java 17), 1.21.11 (Java 21) and 26.3 (Java 25). Text and decision controls were visible in a small window. Each client closed normally afterward. See [multi-version verification](MULTIVERSION.md) for the scope of these checks.

For 1.2.0, the packaged agent also passed `AgentSmoke` on Java 17.0.20.1 and Java 25.0.4 on 2026-10-30. The fixture was separately compiled with `--release 17` and `--release 25`. Both runs blocked the controlled HTTP, TCP, file-write, process and reflection operations before execution. These are synthetic local probes, not a claim of complete sandboxing.

On 2026-10-23 the updated development client started with Fabric Loader 0.19.5 and the agent active, reaching resource/audio initialization without a startup crash. Visual verification of the new HTTP prompt was blocked by the desktop automation tool failing to initialize (`failed to write kernel assets`, missing path). The preview is available for manual inspection; this run is not reported as a completed UI test.

CI does not yet run a complete Minecraft client against real HTTPS/TCP/UDP services or a local TLS server. A successful compilation is not presented as a full client runtime test. JNI and Unsafe are outside the stated boundary, so there is no bypass-prevention test for them; the audit test instead confirms that the classifier does not claim to intercept those paths. Completed-download SHA-256 testing is absent because that feature is not implemented. The local sample regression is opt-in because malware samples must never be committed to the repository; run it with `-Dnmr.sampleJar=<path>`.

For a manual test, run `fabric:runClient`, confirm that the UI says **Security agent: ACTIVE**, and use a separate test mod that invokes each controlled API. Never use real tokens or private data in security tests.
