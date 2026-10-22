package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import dev.nmrfirewall.core.OperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class AgentEnforcementTest {
    @Test void denialThrowsBeforeControlledOperation(@TempDir Path directory) {
        AgentRuntime.initialize("config=" + directory);
        assertThrows(BlockedOperationException.class,
                () -> GuardHooks.before(OperationType.PROCESS_START.name(), "java.lang.ProcessBuilder#start"));
    }
}
