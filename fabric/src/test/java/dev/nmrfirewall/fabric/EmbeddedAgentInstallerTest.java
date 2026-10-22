package dev.nmrfirewall.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedAgentInstallerTest {
    @Test
    void quotesJavaAgentArgumentWhenProfilePathContainsSpaces() {
        Path agent = Path.of("C:\\Users\\test\\profiles\\1.21.4 test\\config\\no-more-rat-agent.jar");

        assertEquals("\"-javaagent:" + agent.toAbsolutePath() + "\"",
                EmbeddedAgentInstaller.jvmArgument(agent));
    }

    @Test
    void leavesJavaAgentArgumentUnquotedWhenPathHasNoSpaces() {
        Path agent = Path.of("C:\\Minecraft\\config\\no-more-rat-agent.jar");

        assertEquals("-javaagent:" + agent.toAbsolutePath(),
                EmbeddedAgentInstaller.jvmArgument(agent));
    }
}
