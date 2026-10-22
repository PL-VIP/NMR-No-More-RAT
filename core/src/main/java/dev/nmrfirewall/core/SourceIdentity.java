package dev.nmrfirewall.core;

import java.util.Objects;

public record SourceIdentity(
        String modId,
        String name,
        String version,
        String jarName,
        String sha256,
        String callerClass,
        Confidence confidence) {

    public enum Confidence { HIGH, MEDIUM, LOW, UNKNOWN }

    public SourceIdentity {
        modId = safe(modId, "unknown");
        name = safe(name, "Unknown source");
        version = safe(version, "unknown");
        jarName = safe(jarName, "unknown");
        sha256 = safe(sha256, "unknown");
        callerClass = safe(callerClass, "unknown");
        confidence = Objects.requireNonNullElse(confidence, Confidence.UNKNOWN);
    }

    public static SourceIdentity unknown(String callerClass) {
        return new SourceIdentity("unknown", "Unknown source", "unknown", "unknown",
                "unknown", callerClass, Confidence.UNKNOWN);
    }

    public String artifactKey() { return modId + ":" + version + ":" + sha256; }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
