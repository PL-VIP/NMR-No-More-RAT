package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpInspectionTest {
    @Test void metadataDoesNotRetainCredentialsOrConsumeTheBody() {
        AtomicInteger reads = new AtomicInteger();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.invalid:8443/plugin.jar?key=fake-secret"))
                .header("Authorization", "Bearer fake-header-secret")
                .header("Cookie", "session=fake-cookie")
                .header("Content-Type", "application/json; boundary=fake-private")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    reads.incrementAndGet(); throw new AssertionError("Must not read body before consent");
                })).build();
        var result = HttpInspection.request(request, "enabled");
        assertEquals(0, reads.get());
        assertEquals("example.invalid", result.parameters().get("host"));
        assertEquals("8443", result.parameters().get("port"));
        assertEquals("unknown", result.parameters().get("bodyBytes"));
        assertEquals("true", result.parameters().get("sensitiveHeaders"));
        assertEquals("true", result.parameters().get("codeOrArchive"));
        assertEquals("application/json", result.parameters().get("contentType"));
        assertFalse(result.parameters().toString().contains("fake-"));
        assertEquals(RiskLevel.HIGH, new SecurityRequest(OperationType.HTTP, null, result.target(), result.parameters()).riskLevel());
    }

    @Test void customPublishersAreNeverInvokedEvenForContentLength() {
        var publisher = new HttpRequest.BodyPublisher() {
            public long contentLength() { throw new AssertionError("Custom code must not run during inspection"); }
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) { throw new AssertionError("Must not subscribe"); }
        };
        var request = HttpRequest.newBuilder(URI.create("https://example.invalid")).POST(publisher).build();
        assertEquals("unknown", HttpInspection.request(request, "disabled").parameters().get("bodyBytes"));
    }

    @Test void immutableTextReportsByteLengthWithoutRetainingBody() {
        var request = HttpRequest.newBuilder(URI.create("https://example.invalid"))
                .POST(HttpRequest.BodyPublishers.ofString("żółw")).build();
        var inspected = HttpInspection.request(request, "disabled");
        assertEquals("7", inspected.parameters().get("bodyBytes"));
        assertEquals("not_inspected", inspected.parameters().get("bodyInspection"));
        assertFalse(inspected.parameters().toString().contains("żółw"));
    }

    @Test void aggregatePublishersCannotInvokeCustomLengthAccessorsDuringInspection() {
        var custom = new HttpRequest.BodyPublisher() {
            public long contentLength() { throw new AssertionError("Must not invoke nested custom publisher"); }
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) { throw new AssertionError("Must not subscribe"); }
        };
        var combined = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString("synthetic"), custom);
        var request = HttpRequest.newBuilder(URI.create("https://example.invalid")).POST(combined).build();
        assertEquals("unknown", HttpInspection.request(request, "disabled").parameters().get("bodyBytes"));
    }

    @Test void urlConnectionInspectionReadsOnlyRequestMetadataWithoutConnecting() throws Exception {
        var connection = (java.net.HttpURLConnection) URI.create("http://127.0.0.1:9/test.zip?key=fake").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer fake-private");
        var inspected = HttpInspection.connection(connection);
        assertEquals("POST", inspected.parameters().get("method"));
        assertEquals("disabled", inspected.parameters().get("redirects"));
        assertEquals("partial_names", inspected.parameters().get("headersInspection"));
        assertEquals("unknown", inspected.parameters().get("sensitiveHeaders"));
        assertEquals("unknown", inspected.parameters().get("bodyBytes"));
        assertFalse(inspected.parameters().toString().contains("fake"));
        // These setters fail once connected. No response getter or body operation is called.
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Test", "still-unconnected");
        connection.setRequestProperty("X-API-Key", "fake-key");
        assertEquals("true", HttpInspection.connection(connection).parameters().get("sensitiveHeaders"));
    }

    @Test void redirectPreferenceSurvivesPersistenceAndDefaultsOnForOldConfigs(@TempDir Path directory) throws Exception {
        var file = directory.resolve("rules.json");
        java.nio.file.Files.writeString(file, "{\"schemaVersion\":1}");
        PolicyStore store = new PolicyStore();
        assertTrue(store.load(file).blockAutomaticHttpRedirects);
        PolicyConfig config = new PolicyConfig();
        config.blockAutomaticHttpRedirects = false;
        PolicyConfig copied = new PolicyConfig();
        copied.copyPreferencesFrom(config);
        store.save(file, copied);
        assertFalse(store.load(file).blockAutomaticHttpRedirects);
        copied.applyPreset(PolicyConfig.Preset.ASK_ALL);
        assertTrue(copied.blockAutomaticHttpRedirects);
    }

    @Test void eventsHideAllPrivateUrlParts() {
        String url = "https://fake-user:fake-pass@example.invalid/private-token?unusual_key=fake-value#fragment";
        SecurityEvent event = new SecurityEvent(null, null, OperationType.HTTP, null, url, Map.of(), null, null, Decision.DENY);
        assertEquals("https://example.invalid/<path hidden>?<query hidden>", event.target());
        assertFalse(event.target().contains("fake"));
    }

    @Test void redirectProtectionTakesPrecedenceOverSavedAllowAndCanBeConfigured() {
        PolicyConfig config = new PolicyConfig();
        config.rules.add(new PolicyRule(null, true, Decision.ALLOW, OperationType.HTTP, null, null, null, null, false));
        var request = new SecurityRequest(OperationType.HTTP, null, "https://example.invalid", Map.of("redirects", "enabled"));
        PolicyEngine engine = new PolicyEngine(config);
        assertEquals("automatic-http-redirects", engine.evaluate(request).ruleId());
        assertEquals(Decision.DENY, engine.evaluate(request).decision());
        config.blockAutomaticHttpRedirects = false;
        assertEquals(Decision.ALLOW, engine.evaluate(request).decision());
    }

    @Test void exactTargetRuleSurvivesSaveWithoutStoringSecretsOrExpandingWildcards(@TempDir Path directory) throws Exception {
        String target = "https://example.invalid/*?secret=fake-value";
        PolicyConfig config = new PolicyConfig();
        config.rules.add(new PolicyRule(null, true, Decision.ALLOW, OperationType.HTTP, null, null, null, PolicyRule.exactTarget(target), false));
        Path file = directory.resolve("rules.json");
        new PolicyStore().save(file, config);
        assertFalse(java.nio.file.Files.readString(file).contains("fake-value"));
        PolicyRule rule = new PolicyStore().load(file).rules.getFirst();
        assertTrue(rule.matches(new SecurityRequest(OperationType.HTTP, null, target, Map.of()), true));
        assertFalse(rule.matches(new SecurityRequest(OperationType.HTTP, null, target.replace("*", "anything"), Map.of()), true));
    }
}
