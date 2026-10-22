package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityRequestRiskTest {
    @Test void classifiesUserFacingRiskWithoutChangingThePolicyDecision() {
        SecurityRequest process = new SecurityRequest(OperationType.PROCESS_START, SourceIdentity.unknown("test"), "cmd", Map.of());
        SecurityRequest network = new SecurityRequest(OperationType.HTTP, SourceIdentity.unknown("test"), "https://example.invalid", Map.of());
        assertEquals(RiskLevel.CRITICAL, process.riskLevel());
        assertEquals(RiskLevel.MEDIUM, network.riskLevel());
    }
}
