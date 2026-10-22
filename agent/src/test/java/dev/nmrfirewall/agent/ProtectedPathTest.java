package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtectedPathTest {
    @Test void recognizesOnlyPathsInsideFirewallDataDirectory() {
        Path data = Path.of("C:", "game", "config", "no-more-rat");
        assertTrue(ProtectedPath.contains(data, data.resolve("rules.json").toString()));
        assertTrue(ProtectedPath.contains(data, data.resolve("agent/no-more-rat-agent.jar").toString()));
        assertFalse(ProtectedPath.contains(data, data.resolveSibling("no-more-rat-copy").resolve("rules.json").toString()));
    }
}
