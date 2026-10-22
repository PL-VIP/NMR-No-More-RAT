package dev.nmrfirewall.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nmrfirewall.core.SourceIdentity;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

final class SourceResolver {
    private final Map<Path, Artifact> cache = new ConcurrentHashMap<>();

    Resolved resolve() {
        StackWalker.StackFrame frame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream
                .filter(f -> !f.getClassName().startsWith("dev.nmrfirewall."))
                .filter(f -> !f.getClassName().startsWith("java.") && !f.getClassName().startsWith("jdk."))
                .findFirst().orElse(null));
        if (frame == null) return new Resolved(SourceIdentity.unknown("unknown"), java.util.List.of());
        Class<?> caller = frame.getDeclaringClass();
        try {
            URI location = caller.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            Artifact artifact = cache.computeIfAbsent(path, this::inspect);
            SourceIdentity identity = new SourceIdentity(artifact.modId, artifact.name, artifact.version,
                    path.getFileName().toString(), artifact.sha256, caller.getName(), artifact.confidence);
            return new Resolved(identity, safeStack());
        } catch (Exception ignored) {
            return new Resolved(SourceIdentity.unknown(caller.getName()), safeStack());
        }
    }

    private Artifact inspect(Path path) {
        try {
            String sha = Files.isRegularFile(path) ? sha256(path) : "development-directory";
            if (Files.isRegularFile(path)) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    var entry = jar.getJarEntry("fabric.mod.json");
                    if (entry != null) {
                        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            String id = string(json, "id", "unknown");
                            String name = string(json, "name", id);
                            String version = string(json, "version", "unknown");
                            return new Artifact(id, name, version, sha, SourceIdentity.Confidence.HIGH);
                        }
                    }
                }
            }
            return new Artifact("unknown", path.getFileName().toString(), "unknown", sha,
                    SourceIdentity.Confidence.LOW);
        } catch (Exception e) {
            return new Artifact("unknown", path.getFileName().toString(), "unknown", "unknown",
                    SourceIdentity.Confidence.UNKNOWN);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0; ) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static java.util.List<String> safeStack() {
        return StackWalker.getInstance().walk(s -> s.limit(40).map(Object::toString).toList());
    }

    record Resolved(SourceIdentity identity, java.util.List<String> stack) { }
    private record Artifact(String modId, String name, String version, String sha256,
                            SourceIdentity.Confidence confidence) { }
}
