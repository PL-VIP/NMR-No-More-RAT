package dev.nmrfirewall.smoke;

import com.sun.net.httpserver.HttpServer;
import dev.nmrfirewall.core.*;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import untrusted.smoke.HttpConsentFixture;

/** Real agent + transformed call sites + local HTTP endpoint. No external traffic or real secrets. */
public final class HttpConsentSmoke {
    public static void main(String[] args) throws Exception {
        PolicyConfig config = new PolicyConfig();
        config.unknownSourceDecision = Decision.ASK;
        config.promptTimeoutSeconds = 3;
        Path file = Path.of(System.getProperty("nmr.configDir"), "rules.json");
        long previous = Files.getLastModifiedTime(file).toMillis();
        new PolicyStore().save(file, config);
        Files.setLastModifiedTime(file, FileTime.fromMillis(Math.max(System.currentTimeMillis(), previous + 2000)));
        AtomicInteger received = new AtomicInteger(), supplied = new AtomicInteger();
        BlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
        PromptHandler handler = (request, preliminary) -> {
            CompletableFuture<Decision> answer = new CompletableFuture<>();
            pending.add(new Pending(request, answer));
            return answer;
        };
        AgentBridge.register(handler);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try (ExecutorService worker = Executors.newSingleThreadExecutor(); HttpClient client = HttpClient.newHttpClient()) {
            URI target = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/consent?key=fake-query-secret");
            HttpRequest request = HttpRequest.newBuilder(target).header("Authorization", "Bearer fake-header-secret")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                        supplied.incrementAndGet(); return new ByteArrayInputStream("dummy body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    })).build();
            for (int mode = 0; mode < 3; mode++) {
                final int selectedMode = mode;
                int beforeRequests = received.get(), beforeReads = supplied.get();
                Future<Integer> denied = worker.submit(() -> HttpConsentFixture.send(client, request, selectedMode));
                Pending decision = take(pending);
                if (!"127.0.0.1".equals(decision.request.parameters().get("host"))) throw new AssertionError("metadata missing");
                if (!"true".equals(decision.request.parameters().get("sensitiveHeaders"))) throw new AssertionError("header indicator missing");
                if (denied.isDone() || received.get() != beforeRequests || supplied.get() != beforeReads)
                    throw new AssertionError("data moved before permission");
                decision.answer.complete(Decision.DENY);
                expectDenied(denied);
                if (received.get() != beforeRequests || supplied.get() != beforeReads) throw new AssertionError("denial sent data");

                Future<Integer> allowed = worker.submit(() -> HttpConsentFixture.send(client, request, selectedMode));
                take(pending).answer.complete(Decision.ALLOW);
                if (allowed.get(5, TimeUnit.SECONDS) != 204 || received.get() != beforeRequests + 1 || supplied.get() != beforeReads + 1)
                    throw new AssertionError("one-time allow did not execute exactly once");
            }
            int beforeTimeout = received.get();
            Future<Integer> expired = worker.submit(() -> HttpConsentFixture.send(client, request, 0));
            Pending unanswered = take(pending);
            expectDenied(expired);
            if (unanswered.answer.complete(Decision.ALLOW) || received.get() != beforeTimeout) throw new AssertionError("late approval sent data");

            try (HttpClient redirecting = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
                expectDenied(worker.submit(() -> HttpConsentFixture.send(redirecting, request, 0)));
                if (received.get() != beforeTimeout || !pending.isEmpty()) throw new AssertionError("redirect request was not denied up front");
            }
            String journal = Files.readString(file.resolveSibling("events.jsonl"));
            if (journal.contains("fake-query-secret") || journal.contains("fake-header-secret") || journal.contains("dummy body"))
                throw new AssertionError("private content persisted in log");
            System.out.println("NMR_HTTP_CONSENT_OK: sync/async deny, allow, timeout, redirect block; zero pre-consent requests/body reads; redacted logs");
        } finally { server.stop(0); AgentBridge.unregister(handler); }
    }

    private static Pending take(BlockingQueue<Pending> queue) throws Exception {
        Pending result = queue.poll(5, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("prompt not delivered");
        return result;
    }
    private static void expectDenied(Future<Integer> future) throws Exception {
        try { future.get(6, TimeUnit.SECONDS); throw new AssertionError("expected DENY"); }
        catch (ExecutionException expected) {
            if (!(expected.getCause() instanceof SecurityException)) throw expected;
        }
    }
    private record Pending(SecurityRequest request, CompletableFuture<Decision> answer) { }
}
