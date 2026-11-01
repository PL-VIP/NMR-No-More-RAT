package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigWritePromptTest {
    @TempDir Path directory;
    private SecurityRequest request(Path path, String api) {
        return new SecurityRequest(OperationType.FILE_WRITE,
                new SourceIdentity("demo", "Demo", "1", "demo.jar", "a".repeat(64), "Demo", SourceIdentity.Confidence.HIGH),
                path.toString(), Map.of("api", "java.nio.file.Files#" + api));
    }
    private Path firewall() throws Exception { return Files.createDirectories(directory.resolve("config/no-more-rat")); }

    @Test void ordinaryConfigReadsAreNotClassifiedAsWrites() {
        assertNull(SecurityTransformer.classify("java/nio/file/Files", "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;"));
        assertNull(SecurityTransformer.classify("java/nio/file/Files", "newInputStream", "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/InputStream;"));
    }

    @Test void fileWritesCanAskBeforeMinecraftHasInitializedItsUi() throws Exception {
        Path firewall = firewall();
        Path config = directory.resolve("config/demo");
        assertTrue(ConfigWritePrompt.eligible(request(config, "createDirectories"), firewall));
        assertTrue(ConfigWritePrompt.eligible(request(config.resolve("settings.json"), "writeString"), firewall));
        assertTrue(ConfigWritePrompt.eligible(request(directory.resolve("outside/boot.log"), "write"), firewall));
        assertFalse(Files.exists(config));
    }
    @Test void onlyFirewallDataStaysOutOfScope() throws Exception {
        Path firewall = firewall();
        assertFalse(ConfigWritePrompt.eligible(request(firewall.resolve("rules.json"), "writeString"), firewall));
        assertFalse(ConfigWritePrompt.eligible(request(firewall.resolve("events.jsonl"), "write"), firewall));
    }
    @Test void unknownSourcesAndOtherOperationsDoNotGetThisPrompt() throws Exception {
        Path firewall = firewall();
        SecurityRequest known = request(directory.resolve("config/demo.json"), "write");
        assertFalse(ConfigWritePrompt.eligible(new SecurityRequest(OperationType.FILE_WRITE, SourceIdentity.unknown("Demo"), known.target(), known.parameters()), firewall));
        assertFalse(ConfigWritePrompt.eligible(new SecurityRequest(OperationType.HTTP, known.source(), known.target(), known.parameters()), firewall));
        assertFalse(ConfigWritePrompt.eligible(known, directory.resolve("custom-agent-data")));
    }
}
