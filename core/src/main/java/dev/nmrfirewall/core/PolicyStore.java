package dev.nmrfirewall.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PolicyStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public PolicyConfig load(Path path) throws IOException {
        if (!Files.exists(path)) return new PolicyConfig();
        try {
            PolicyConfig value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), PolicyConfig.class);
            validate(value);
            return value;
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid policy configuration", malformed);
        }
    }

    public void save(Path path, PolicyConfig config) throws IOException {
        validate(config);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Re-read rules so saving an open settings screen does not discard newly remembered decisions. */
    public void savePreferences(Path path, PolicyConfig preferences) throws IOException {
        PolicyConfig latest = load(path);
        latest.copyPreferencesFrom(preferences);
        save(path, latest);
    }

    private static void validate(PolicyConfig config) throws IOException {
        if (config == null || config.schemaVersion != 1) throw new IOException("Unsupported or empty policy schema");
        if (config.defaultDecision == null || config.unknownSourceDecision == null
                || config.rules == null || config.rules.stream().anyMatch(java.util.Objects::isNull)
                || config.operationDecisions == null
                || config.operationDecisions.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)) {
            throw new IOException("Policy decisions and rules must not be null or unknown");
        }
        if (config.promptTimeoutSeconds < 1 || config.promptTimeoutSeconds > 300) {
            throw new IOException("Decision timeout must be between 1 and 300 seconds");
        }
        if (config.maxLogEntries < 100 || config.maxLogEntries > 100000) {
            throw new IOException("Event capacity must be between 100 and 100000");
        }
    }
}
