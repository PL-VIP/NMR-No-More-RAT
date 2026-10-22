package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

class PolicyEngineTest {
    private static SourceIdentity source(String hash) {
        return new SourceIdentity("example", "Example", "1.0", "example.jar", hash,
                "example.Main", SourceIdentity.Confidence.HIGH);
    }

    @Test void exactArtifactAndOperationCanBeAllowed() {
        PolicyConfig config = new PolicyConfig();
        config.rules = List.of(new PolicyRule("allow-api", true, Decision.ALLOW, OperationType.HTTP,
                "example", "1.0", "abc", "*example.com*", false));
        PolicyResult result = new PolicyEngine(config).evaluate(new SecurityRequest(OperationType.HTTP,
                source("abc"), "https://api.example.com/v1", Map.of()));
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals("allow-api", result.ruleId());
    }

    @Test void changedJarHashInvalidatesTrust() {
        PolicyConfig config = new PolicyConfig();
        config.defaultDecision = Decision.DENY;
        config.rules = List.of(new PolicyRule("trusted", true, Decision.ALLOW, OperationType.HTTP,
                "example", "1.0", "old-hash", "*", false));
        PolicyResult result = new PolicyEngine(config).evaluate(new SecurityRequest(OperationType.HTTP,
                source("new-hash"), "https://example.com", Map.of()));
        assertEquals(Decision.DENY, result.decision());
    }

    @Test void processAllowNeedsExplicitProcessPermission() {
        PolicyRule rule = new PolicyRule("too-broad", true, Decision.ALLOW, OperationType.PROCESS_START,
                "example", "1.0", "abc", "*", false);
        assertFalse(rule.matches(new SecurityRequest(OperationType.PROCESS_START, source("abc"), "cmd", Map.of()), true));
    }
}
