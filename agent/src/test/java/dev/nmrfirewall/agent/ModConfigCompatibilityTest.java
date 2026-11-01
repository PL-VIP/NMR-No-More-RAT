package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigCompatibilityTest {
    @TempDir Path directory;

    private Path firewall() throws Exception {
        return Files.createDirectories(directory.resolve("config/no-more-rat"));
    }

    private SecurityRequest request(Path target, SourceIdentity.Confidence confidence) {
        var source = new SourceIdentity("demo", "Demo", "1", "demo.jar", "a".repeat(64), "Demo", confidence);
        return new SecurityRequest(OperationType.FILE_WRITE, source, target.toString(), java.util.Map.of());
    }

    @Test void allowsOnlyVerifiedModOwnConfigSubtreeWhenEnabled() throws Exception {
        Path firewall = firewall();
        PolicyConfig config = new PolicyConfig();
        assertTrue(ModConfigCompatibility.allows(request(directory.resolve("config/demo/settings.json"), SourceIdentity.Confidence.HIGH), firewall, config));
        assertTrue(ModConfigCompatibility.allows(request(directory.resolve("config/demo/nested"), SourceIdentity.Confidence.HIGH), firewall, config));
    }

    @Test void rejectsOtherPathsUnknownArtifactsAndDisabledSetting() throws Exception {
        Path firewall = firewall();
        PolicyConfig config = new PolicyConfig();
        assertFalse(ModConfigCompatibility.allows(request(directory.resolve("config/other/settings.json"), SourceIdentity.Confidence.HIGH), firewall, config));
        assertFalse(ModConfigCompatibility.allows(request(directory.resolve("outside/settings.json"), SourceIdentity.Confidence.HIGH), firewall, config));
        assertFalse(ModConfigCompatibility.allows(request(firewall.resolve("rules.json"), SourceIdentity.Confidence.HIGH), firewall, config));
        assertFalse(ModConfigCompatibility.allows(request(directory.resolve("config/demo/settings.json"), SourceIdentity.Confidence.UNKNOWN), firewall, config));
        config.allowModConfigWrites = false;
        assertFalse(ModConfigCompatibility.allows(request(directory.resolve("config/demo/settings.json"), SourceIdentity.Confidence.HIGH), firewall, config));
    }
}
