package untrusted.smoke;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class AgentSmoke {
    private AgentSmoke() { }

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("nmr.agent.active")) throw new AssertionError("agent did not start");
        expectBlocked("process", () -> new ProcessBuilder("nmr-command-that-must-not-run").start());
        expectBlocked("HttpClient GET", AgentSmoke::httpClientGet);
        expectBlocked("HttpClient POST", AgentSmoke::httpClientPost);
        expectBlocked("HttpURLConnection GET", AgentSmoke::urlConnectionGet);
        expectBlocked("HttpURLConnection POST", AgentSmoke::urlConnectionPost);
        expectBlocked("HttpURLConnection response code", AgentSmoke::urlConnectionResponseCode);
        expectBlocked("TCP connect", () -> new java.net.Socket().connect(new InetSocketAddress("127.0.0.1", 9)));
        expectBlocked("NIO file write", AgentSmoke::fileWrite);
        expectBlocked("reflective invocation", AgentSmoke::reflectiveInvocation);
        System.out.println("NMR_SMOKE_OK: all controlled operations blocked before execution");
    }

    private static void httpClientGet() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9/nmr-get")).GET().build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static void httpClientPost() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9/nmr-post"))
                .POST(HttpRequest.BodyPublishers.ofString("test", StandardCharsets.UTF_8)).build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static void urlConnectionGet() throws Exception {
        HttpURLConnection connection = new ProbeConnection("http://127.0.0.1:9/nmr-get");
        connection.setRequestMethod("GET");
        connection.getInputStream();
    }

    private static void urlConnectionPost() throws Exception {
        HttpURLConnection connection = new ProbeConnection("http://127.0.0.1:9/nmr-post");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.getOutputStream();
    }

    private static void urlConnectionResponseCode() throws Exception {
        HttpURLConnection connection = new ProbeConnection("http://127.0.0.1:9/nmr-response");
        connection.setRequestMethod("GET");
        connection.getResponseCode();
    }

    private static void fileWrite() throws Exception {
        Path target = Path.of(System.getProperty("java.io.tmpdir"), "nmr-must-not-exist-" + UUID.randomUUID());
        try {
            Files.writeString(target, "must not be written", StandardCharsets.UTF_8);
            throw new AssertionError("NIO file write was not blocked");
        } finally {
            if (Files.exists(target)) throw new AssertionError("blocked NIO write created a file: " + target);
        }
    }

    private static void reflectiveInvocation() throws Exception {
        String.class.getMethod("length").invoke("must not run");
    }

    private static void expectBlocked(String label, ThrowingOperation operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError(label + " was not blocked");
        } catch (SecurityException expected) {
            if (!expected.getClass().getName().equals("dev.nmrfirewall.agent.BlockedOperationException")) {
                throw new AssertionError("Expected a policy denial, not a linkage/bootstrap error", expected);
            }
            System.out.println("NMR_BLOCKED: " + label);
        }
    }

    // Avoid an earlier URL.openConnection guard masking the individual stream/response guards.
    // No network is used: reaching any operation itself is a test failure.
    private static final class ProbeConnection extends HttpURLConnection {
        ProbeConnection(String url) throws Exception { super(URI.create(url).toURL()); }
        @Override public void connect() { throw new AssertionError("connect executed"); }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public java.io.InputStream getInputStream() { throw new AssertionError("GET executed"); }
        @Override public java.io.OutputStream getOutputStream() { throw new AssertionError("POST executed"); }
        @Override public int getResponseCode() { throw new AssertionError("getResponseCode executed"); }
    }

    @FunctionalInterface private interface ThrowingOperation { void run() throws Exception; }
}
