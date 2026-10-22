package dev.nmrfirewall.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

final class EmbeddedAgentInstaller {
    private EmbeddedAgentInstaller() { }

    static String jvmArgument(Path agent) {
        String value = "-javaagent:" + agent.toAbsolutePath();
        return value.indexOf(' ') >= 0 ? "\"" + value + "\"" : value;
    }

    static Path extract() throws IOException {
        byte[] bundled;
        try (var input = EmbeddedAgentInstaller.class.getResourceAsStream("/nmr-agent/no-more-rat-agent.jar")) {
            if (input == null) throw new IOException("The embedded agent is missing from the mod JAR");
            bundled = input.readAllBytes();
        }
        Path directory = NoMoreRatClient.configDirectory().resolve("agent");
        Path target = directory.resolve("no-more-rat-agent.jar");
        Files.createDirectories(directory);
        if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), bundled)) return target.toAbsolutePath();
        Path temporary = directory.resolve("no-more-rat-agent.jar.tmp");
        Files.write(temporary, bundled);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath();
    }
}
