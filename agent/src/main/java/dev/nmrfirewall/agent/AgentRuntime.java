package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.AgentBridge;
import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.EventJournal;
import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyEngine;
import dev.nmrfirewall.core.PolicyResult;
import dev.nmrfirewall.core.PolicyStore;
import dev.nmrfirewall.core.SecurityEvent;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.HttpInspection;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class AgentRuntime {
    private static final SourceResolver SOURCES = new SourceResolver();
    private static final PolicyStore STORE = new PolicyStore();
    private static volatile Path configFile;
    private static volatile PolicyEngine engine;
    private static volatile EventJournal journal;
    private static volatile long lastConfigModified;
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);
    /** Never persisted: removes duplicate consent prompts without creating a permanent allow rule. */
    private static final Set<SessionPermission> SESSION_PERMISSIONS = ConcurrentHashMap.newKeySet();

    private AgentRuntime() { }

    static synchronized void initialize(String arguments) {
        if (engine != null) return;
        Path directory = parseDirectory(arguments);
        configFile = directory.resolve("rules.json");
        try {
            PolicyConfig config = STORE.load(configFile);
            engine = new PolicyEngine(config);
            if (!java.nio.file.Files.exists(configFile)) STORE.save(configFile, config);
            journal = new EventJournal(directory.resolve("events.jsonl"), config.maxLogEntries);
            refreshModified();
        } catch (Exception failure) {
            engine = new PolicyEngine(new PolicyConfig());
            System.err.println("[No More RAT] Configuration error; fail-closed defaults active: " + failure.getMessage());
        }
        System.setProperty("nmr.configDir", directory.toAbsolutePath().toString());
        dev.nmrfirewall.bootstrap.GuardBridge.install((operation, target, api) ->
                enforce(OperationType.valueOf(operation), target, api));
    }

    static void enforce(OperationType operation, String target) {
        enforce(operation, target, Map.of());
    }

    private static void enforce(OperationType operation, String target, Map<String, String> parameters) {
        if (Boolean.TRUE.equals(INSIDE.get())) return;
        INSIDE.set(true);
        try {
            reloadIfChanged();
            SourceResolver.Resolved resolved = SOURCES.resolve();
            SecurityRequest request = new SecurityRequest(operation, resolved.identity(), target, parameters);
            // The agent and the trusted UI are excluded from instrumentation. Every other supported
            // NIO write into this directory is therefore an untrusted attempt to alter policy/log state.
            PolicyResult result;
            if (operation == OperationType.FILE_WRITE && ProtectedPath.contains(configFile.getParent(), target)) {
                result = new PolicyResult(Decision.DENY, "protected-no-more-rat-data");
            } else {
                result = engine.evaluate(request);
                if (result.decision() == Decision.ASK
                        && ModConfigCompatibility.allows(request, configFile.getParent(), engine.config())) {
                    result = new PolicyResult(Decision.ALLOW, "mod-config-compatibility");
                }
            }
            SessionPermission permission = result.decision() == Decision.ASK
                    && ConfigWritePrompt.eligible(request, configFile.getParent()) ? SessionPermission.from(request) : null;
            if (permission != null && SESSION_PERMISSIONS.contains(permission)) {
                result = new PolicyResult(Decision.ALLOW, "session-file-consent");
            }
            Decision finalDecision = resolveAsk(request, result);
            if (permission != null && result.decision() == Decision.ASK && finalDecision == Decision.ALLOW) {
                SESSION_PERMISSIONS.add(permission);
            }
            SecurityEvent event = new SecurityEvent(UUID.randomUUID(), Instant.now(), operation, resolved.identity(),
                    target, parameters, resolved.stack(), result.ruleId(), finalDecision);
            if (journal != null && (finalDecision != Decision.ALLOW || engine.config().logAllowedOperations)) journal.append(event);
            if (finalDecision != Decision.ALLOW) {
                if ("automatic-http-redirects".equals(result.ruleId())) {
                    var notice = new java.util.HashMap<>(parameters);
                    notice.put("blockReason", "automatic-http-redirects");
                    notifySharedBridge(new SecurityRequest(operation, resolved.identity(), target, notice));
                }
                throw new BlockedOperationException("No More RAT blocked " + operation + " from "
                        + resolved.identity().modId() + " (rule: " + result.ruleId() + ")");
            }
        } catch (BlockedOperationException blocked) {
            throw blocked;
        } catch (Exception failure) {
            throw new BlockedOperationException("No More RAT failed closed: " + failure.getClass().getSimpleName());
        } finally {
            INSIDE.set(false);
        }
    }

    static void enforce(OperationType operation, Object targetObject, String api) {
        TargetDescription description = describe(targetObject, api);
        enforce(operation, description.target(), description.parameters());
    }

    private static TargetDescription describe(Object value, String fallback) {
        if (value instanceof Object[] pair && pair.length == 2
                && pair[0] instanceof java.net.http.HttpClient client && pair[1] instanceof java.net.http.HttpRequest request) {
            String redirects = client.getClass().getModule() == java.net.http.HttpClient.class.getModule()
                    ? (client.followRedirects() == java.net.http.HttpClient.Redirect.NEVER ? "disabled" : "enabled") : "unknown";
            HttpInspection.Result inspected = HttpInspection.request(request, redirects);
            return new TargetDescription(inspected.target(), inspected.parameters());
        }
        if (value == null) return new TargetDescription(fallback, Map.of());
        if (value instanceof java.net.HttpURLConnection connection) {
            HttpInspection.Result inspected = HttpInspection.connection(connection);
            return new TargetDescription(inspected.target(), inspected.parameters());
        }
        if (value instanceof java.net.URL url) {
            HttpInspection.Result inspected = HttpInspection.url(url.toExternalForm());
            return new TargetDescription(inspected.target(), inspected.parameters());
        }
        if (value instanceof java.net.URLConnection connection) {
            return new TargetDescription(connection.getURL().toExternalForm(), Map.of());
        }
        if (value instanceof java.net.http.HttpRequest request) {
            HttpInspection.Result inspected = HttpInspection.request(request, "unknown");
            return new TargetDescription(inspected.target(), inspected.parameters());
        }
        if (value instanceof java.net.SocketAddress address) return new TargetDescription(address.toString(), Map.of());
        if (value instanceof java.nio.file.Path path) {
            return new TargetDescription(path.toAbsolutePath().normalize().toString(), Map.of("api", fallback));
        }
        if (value instanceof java.net.DatagramPacket packet) {
            return new TargetDescription(String.valueOf(packet.getSocketAddress()), Map.of());
        }
        if (value instanceof ProcessBuilder builder) {
            return new TargetDescription(dev.nmrfirewall.core.SecretRedactor.redact(String.join(" ", builder.command())), Map.of());
        }
        if (value instanceof String command) {
            return new TargetDescription(dev.nmrfirewall.core.SecretRedactor.redact(command), Map.of());
        }
        if (value instanceof String[] command) {
            return new TargetDescription(dev.nmrfirewall.core.SecretRedactor.redact(String.join(" ", command)), Map.of());
        }
        if (value.getClass().getName().startsWith("okhttp3.")) {
            try {
                Object request = value.getClass().getMethod("request").invoke(value);
                Object method = request.getClass().getMethod("method").invoke(request);
                Object url = request.getClass().getMethod("url").invoke(request);
                return new TargetDescription(String.valueOf(url), Map.of("method", String.valueOf(method)));
            } catch (ReflectiveOperationException ignored) { }
        }
        return new TargetDescription(fallback, Map.of());
    }

    private static Decision resolveAsk(SecurityRequest request, PolicyResult result) {
        if (result.decision() != Decision.ASK) return result.decision();
        // Minecraft cannot render its own consent screen while its initialization thread waits.
        // This separate desktop dialog grants only this call, never blanket FILE_WRITE access.
        if (ConfigWritePrompt.eligible(request, configFile.getParent())) {
            java.util.concurrent.CompletableFuture<Decision> consent = null;
            try {
                consent = ConfigWritePrompt.ask(request, configFile.getParent(), engine.config().promptTimeoutSeconds);
                return consent.get(engine.config().promptTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (consent != null) consent.cancel(false);
                return Decision.DENY;
            } catch (Exception | LinkageError unavailable) {
                if (consent != null) consent.cancel(false);
                return Decision.DENY;
            }
        }
        if (isLikelyRenderThread()) {
            notifySharedBridge(request);
            return Decision.DENY;
        }
        java.util.concurrent.CompletableFuture<Decision> pending = null;
        try {
            var handler = AgentBridge.handler();
            if (handler != null) {
                pending = handler.request(request, result);
            } else {
                pending = requestThroughSharedBridge(request);
            }
            if (pending == null) return Decision.DENY;
            return pending.get(engine.config().promptTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception timeoutOrFailure) {
            if (pending != null) pending.cancel(false);
            return Decision.DENY;
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.concurrent.CompletableFuture<Decision> requestThroughSharedBridge(SecurityRequest request) {
        Object bridge = trustedPromptBridge();
        if (bridge == null) return null;
        try {
            var source = request.source();
            Object response = bridge.getClass().getMethod("request", String.class, String.class, String.class,
                    String.class, String.class, String.class, String.class, String.class, String.class, Map.class)
                    .invoke(bridge, request.operation().name(), source.modId(), source.name(), source.version(),
                            source.jarName(), source.sha256(), source.callerClass(), source.confidence().name(),
                            request.target(), request.parameters());
            java.util.concurrent.CompletableFuture<?> raw = (java.util.concurrent.CompletableFuture<?>) response;
            return dev.nmrfirewall.core.DecisionFutures.map(raw, value -> Decision.valueOf(String.valueOf(value)));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static void notifySharedBridge(SecurityRequest request) {
        Object bridge = trustedPromptBridge();
        if (bridge == null) return;
        try {
            var source = request.source();
            bridge.getClass().getMethod("notifyBlocked", String.class, String.class, String.class,
                    String.class, String.class, String.class, String.class, String.class, String.class, Map.class)
                    .invoke(bridge, request.operation().name(), source.modId(), source.name(), source.version(),
                            source.jarName(), source.sha256(), source.callerClass(), source.confidence().name(),
                            request.target(), request.parameters());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static boolean isLikelyRenderThread() {
        String name = Thread.currentThread().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("render") || name.equals("client thread");
    }

    private static volatile Object trustedBridge;

    /** Cache only the concrete UI endpoint; a later System-property replacement cannot grant ALLOW. */
    private static Object trustedPromptBridge() {
        Object cached = trustedBridge;
        if (cached != null) return cached;
        Object candidate = System.getProperties().get("nmr.prompt.bridge");
        if (candidate == null || !candidate.getClass().getName().equals("dev.nmrfirewall.fabric.PromptBridgeEndpoint")) return null;
        trustedBridge = candidate;
        return candidate;
    }

    private static void reloadIfChanged() {
        try {
            long modified = java.nio.file.Files.getLastModifiedTime(configFile).toMillis();
            if (modified != lastConfigModified) {
                PolicyConfig updated = STORE.load(configFile);
                engine.replace(updated);
                if (journal != null) journal.setCapacity(updated.maxLogEntries);
                lastConfigModified = modified;
            }
        } catch (Exception ignored) { }
    }

    private static void refreshModified() {
        try { lastConfigModified = java.nio.file.Files.getLastModifiedTime(configFile).toMillis(); }
        catch (Exception ignored) { lastConfigModified = 0; }
    }

    private static Path parseDirectory(String arguments) {
        if (arguments != null && arguments.startsWith("config=")) return Path.of(arguments.substring(7));
        return Path.of("config", "no-more-rat");
    }

    private record TargetDescription(String target, Map<String, String> parameters) { }

    private record SessionPermission(OperationType operation, String artifactSha256, String target, String api) {
        static SessionPermission from(SecurityRequest request) {
            String normalized;
            try { normalized = Path.of(request.target()).toAbsolutePath().normalize().toString(); }
            catch (Exception malformed) { normalized = request.target(); }
            return new SessionPermission(request.operation(), request.source().sha256(), normalized,
                    request.parameters().getOrDefault("api", ""));
        }
    }
}
