package dev.nmrfirewall.core;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PolicyRule(
        String id,
        boolean enabled,
        Decision decision,
        OperationType operation,
        String modId,
        String version,
        String artifactSha256,
        String targetPattern,
        boolean processPermission) {

    public PolicyRule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        decision = Objects.requireNonNull(decision, "decision");
        modId = normalize(modId);
        version = normalize(version);
        artifactSha256 = normalize(artifactSha256);
        targetPattern = normalize(targetPattern);
    }

    public boolean matches(SecurityRequest request, boolean strictMode) {
        if (!enabled || (operation != null && operation != request.operation())) return false;
        SourceIdentity source = request.source();
        if (modId != null && !modId.equalsIgnoreCase(source.modId())) return false;
        if (version != null && !version.equals(source.version())) return false;
        if (artifactSha256 != null && !artifactSha256.equalsIgnoreCase(source.sha256())) return false;
        if (targetPattern != null && !(targetPattern.startsWith("sha256:")
                ? targetPattern.equals(exactTarget(request.target())) : wildcardMatch(targetPattern, request.target()))) return false;
        if (request.operation() == OperationType.PROCESS_START && decision == Decision.ALLOW && !processPermission) return false;
        if (strictMode && decision == Decision.ALLOW && request.highRisk()
                && (artifactSha256 == null || operation == null)) return false;
        return true;
    }

    private static boolean wildcardMatch(String pattern, String value) {
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        String regex = java.util.regex.Pattern.quote(normalizedPattern).replace("*", "\\E.*\\Q");
        return value != null && value.toLowerCase(Locale.ROOT).matches(regex);
    }

    /** Store an exact target without persisting URL credentials or treating literal '*' as a wildcard. */
    public static String exactTarget(String target) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Objects.requireNonNull(target).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || "*".equals(value) ? null : value.trim();
    }
}
