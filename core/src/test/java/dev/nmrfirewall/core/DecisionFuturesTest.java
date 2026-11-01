package dev.nmrfirewall.core;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class DecisionFuturesTest {
    @Test void timeoutCancelsThroughBothBridgeMappings() {
        CompletableFuture<Decision> ui = new CompletableFuture<>();
        var bridge = DecisionFutures.map(ui, Decision::name);
        var agent = DecisionFutures.map(bridge, Decision::valueOf);
        agent.cancel(false);
        assertTrue(ui.isCancelled());
        assertFalse(ui.complete(Decision.ALLOW));
    }
    @Test void approvalRemainsUsable() {
        CompletableFuture<Decision> ui = new CompletableFuture<>();
        var result = DecisionFutures.map(ui, Decision::name);
        ui.complete(Decision.DENY);
        assertEquals("DENY", result.join());
    }
}
