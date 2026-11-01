package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.nio.file.Path;

/**
 * Fabric loads a mod's client entrypoint before its own screens exist.  Denying a
 * normal write below config/<that-mod-id> at this point turns an ASK decision into
 * an unavoidable startup crash.  Keep this compatibility scope deliberately tiny:
 * a verified artifact may change only its own configuration subtree, never the
 * firewall data, another mod's data, or an arbitrary local path.
 */
final class ModConfigCompatibility {
    private ModConfigCompatibility() { }

    static boolean allows(SecurityRequest request, Path firewallDirectory, PolicyConfig config) {
        if (!config.allowModConfigWrites || request.operation() != OperationType.FILE_WRITE
                || request.source().confidence() != SourceIdentity.Confidence.HIGH
                || !request.source().sha256().matches("[a-fA-F0-9]{64}")) return false;
        try {
            Path firewall = firewallDirectory.toAbsolutePath().normalize();
            Path configRoot = firewall.getParent();
            if (configRoot == null || !firewall.getFileName().toString().equals("no-more-rat")
                    || !configRoot.getFileName().toString().equals("config")) return false;
            String modId = request.source().modId();
            if (!modId.matches("[a-z0-9_.-]{1,64}")) return false;
            Path target = Path.of(request.target()).toAbsolutePath().normalize();
            Path ownConfig = configRoot.resolve(modId).normalize();
            return target.startsWith(ownConfig) && !target.startsWith(firewall);
        } catch (Exception invalid) {
            return false;
        }
    }
}
