package dev.nmrfirewall.agent;

import java.nio.file.Path;

/** Keeps untrusted, instrumented mod code away from the firewall's own local state. */
final class ProtectedPath {
    private ProtectedPath() { }

    static boolean contains(Path protectedDirectory, String target) {
        if (protectedDirectory == null || target == null || target.isBlank()) return false;
        try {
            Path protectedRoot = protectedDirectory.toAbsolutePath().normalize();
            Path candidate = Path.of(target).toAbsolutePath().normalize();
            return candidate.startsWith(protectedRoot);
        } catch (RuntimeException invalidPath) {
            return false;
        }
    }
}
