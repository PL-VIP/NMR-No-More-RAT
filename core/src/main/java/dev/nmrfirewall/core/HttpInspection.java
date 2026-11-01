package dev.nmrfirewall.core;

import java.net.URI;
import java.net.HttpURLConnection;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Metadata only: never subscribes to a body, reads a stream, resolves DNS or opens a connection. */
public final class HttpInspection {
    // Match exact leaf implementations obtained from safe JDK factories. A JDK aggregate
    // publisher can delegate contentLength() to custom publishers, so its module is not enough.
    private static final java.util.Set<Class<?>> LENGTH_SAFE_PUBLISHERS = java.util.Set.copyOf(List.of(
            HttpRequest.BodyPublishers.noBody().getClass(),
            HttpRequest.BodyPublishers.ofByteArray(new byte[0]).getClass(),
            HttpRequest.BodyPublishers.ofString("").getClass()));
    private HttpInspection() { }

    public record Result(String target, Map<String, String> parameters) { }

    public static Result request(HttpRequest request, String redirects) {
        // A custom request/publisher may execute arbitrary code in its accessors.
        if (request.getClass().getModule() != HttpRequest.class.getModule()) return unknown("custom HttpRequest");
        Map<String, String> values = base(request.uri(), request.method(), redirects);
        headers(values, request.headers().map());
        request.bodyPublisher().ifPresentOrElse(body -> {
            if (LENGTH_SAFE_PUBLISHERS.contains(body.getClass())) {
                long size = body.contentLength();
                values.put("bodyBytes", size < 0 ? "unknown" : Long.toString(size));
                values.put("bodyInspection", size == 0 ? "empty" : "not_inspected");
            }
        }, () -> { values.put("bodyBytes", "0"); values.put("bodyInspection", "empty"); });
        return new Result(request.uri().toString(), Map.copyOf(values));
    }

    public static Result connection(HttpURLConnection connection) {
        if (connection.getClass().getModule() != HttpURLConnection.class.getModule()) return unknown("custom HttpURLConnection");
        try {
            URI uri = connection.getURL().toURI();
            Map<String, String> values = base(uri, connection.getRequestMethod(),
                    connection.getInstanceFollowRedirects() ? "enabled" : "disabled");
            try {
                headers(values, connection.getRequestProperties());
                // JDK URLConnection hides Authorization and some other request headers.
                values.put("headersInspection", "partial_names");
                if (!"true".equals(values.get("sensitiveHeaders"))) values.put("sensitiveHeaders", "unknown");
            }
            catch (IllegalStateException alreadyConnected) { values.put("headersInspection", "unavailable"); }
            // Never ask for response headers/content length: that can initiate the connection.
            return new Result(uri.toString(), Map.copyOf(values));
        } catch (java.net.URISyntaxException malformed) { return unknown("invalid HTTP URL"); }
    }

    public static Result url(String target) {
        try { return new Result(target, Map.copyOf(base(URI.create(target), "unknown", "unknown"))); }
        catch (IllegalArgumentException invalid) { return unknown("invalid HTTP URL"); }
    }

    public static Result unknown(String api) {
        return new Result(api, Map.of("bodyBytes", "unknown", "bodyInspection", "not_inspected",
                "headersInspection", "unavailable", "redirects", "unknown"));
    }

    private static Map<String, String> base(URI uri, String method, String redirects) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("method", safeMethod(method));
        values.put("host", bounded(uri.getHost() == null ? "unknown" : uri.getHost(), 253));
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        values.put("scheme", scheme.equals("http") || scheme.equals("https") ? scheme : "unknown");
        int port = uri.getPort() >= 0 ? uri.getPort() : scheme.equals("https") ? 443 : scheme.equals("http") ? 80 : -1;
        values.put("port", port < 0 ? "unknown" : Integer.toString(port));
        values.put("redirects", List.of("enabled", "disabled").contains(redirects) ? redirects : "unknown");
        values.put("bodyBytes", "unknown");
        values.put("bodyInspection", "not_inspected");
        values.put("headersInspection", "unavailable");
        values.put("urlPrivateParts", Boolean.toString(uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null));
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        values.put("codeOrArchive", Boolean.toString(path.matches(".*\\.(jar|zip|exe|dll|so|dylib|bat|cmd|ps1|sh)$")));
        return values;
    }

    private static void headers(Map<String, String> values, Map<String, List<String>> headers) {
        boolean sensitive = false;
        String type = "unknown";
        for (String name : headers.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            // Only names are inspected for credential indicators. Values are never retained.
            sensitive |= lower.equals("authorization") || lower.equals("proxy-authorization") || lower.equals("cookie")
                    || lower.contains("token") || lower.contains("api-key") || lower.contains("apikey") || lower.contains("secret");
            if (lower.equals("content-type") && !headers.get(name).isEmpty()) {
                String raw = headers.get(name).get(0);
                String media = raw.substring(0, Math.min(raw.length(), 128)).split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                type = switch (media) {
                    case "application/json", "application/x-www-form-urlencoded", "multipart/form-data", "text/plain",
                         "application/octet-stream", "application/zip", "application/java-archive" -> media;
                    default -> "other";
                };
            }
        }
        values.put("headersInspection", "names_only");
        values.put("sensitiveHeaders", Boolean.toString(sensitive));
        values.put("headerCount", Integer.toString(headers.size()));
        values.put("contentType", type);
    }

    private static String safeMethod(String method) {
        return method != null && method.matches("[A-Z]{1,16}") ? method : "unknown";
    }

    private static String bounded(String value, int length) { return value.substring(0, Math.min(length, value.length())); }

    /** Keep the endpoint visible, but never expose credentials embedded in paths, queries or fragments. */
    public static String displayTarget(String raw) {
        if (raw == null) return "HTTP target unavailable";
        try {
            URI uri = URI.create(raw);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                return "HTTP target unavailable";
            return bounded(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + displayPath(uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?<query hidden>"), 400);
        } catch (IllegalArgumentException malformed) { return "HTTP target unavailable"; }
    }

    /** Discord webhook URLs contain a credential as their final path segment. */
    private static String displayPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.equals("/")) return "/";
        if (rawPath.matches("(?i)^/api/webhooks/[0-9]+/[^/]+$")) {
            return rawPath.replaceFirst("(?i)^(/api/webhooks/[0-9]+)/[^/]+$", "$1/<webhook token hidden>");
        }
        return "/<path hidden>";
    }
}
