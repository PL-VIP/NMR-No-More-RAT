package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigControlsTest {
    private static final SourceIdentity MOD = new SourceIdentity("test", "Test", "1", "test.jar", "abc",
            "test.Main", SourceIdentity.Confidence.HIGH);

    private static SecurityRequest request(OperationType operation) {
        return new SecurityRequest(operation, MOD, "https://example.invalid", Map.of());
    }

    @Test void explicitAskOverridesAutomaticHighRiskDenial() {
        PolicyConfig config = new PolicyConfig();
        config.operationDecisions.put(OperationType.PROCESS_START, Decision.ASK);
        config.operationDecisions.put(OperationType.DYNAMIC_CODE, Decision.ASK);
        PolicyEngine engine = new PolicyEngine(config);
        assertEquals(Decision.ASK, engine.evaluate(request(OperationType.PROCESS_START)).decision());
        assertEquals(Decision.ASK, engine.evaluate(request(OperationType.DYNAMIC_CODE)).decision());
    }

    @Test void networkCanBeBlockedWhileFileChangesAreAllowed() {
        PolicyConfig config = new PolicyConfig();
        config.operationDecisions.put(OperationType.HTTP, Decision.DENY);
        config.operationDecisions.put(OperationType.FILE_WRITE, Decision.ALLOW);
        PolicyEngine engine = new PolicyEngine(config);
        assertEquals(Decision.DENY, engine.evaluate(request(OperationType.HTTP)).decision());
        assertEquals(Decision.ALLOW, engine.evaluate(request(OperationType.FILE_WRITE)).decision());
        assertEquals(Decision.ASK, engine.evaluate(request(OperationType.UDP)).decision());
    }

    @Test void unknownSourcePolicyStillPrecedesOperationAllow() {
        PolicyConfig config = new PolicyConfig();
        config.operationDecisions.put(OperationType.HTTP, Decision.ALLOW);
        var unknown = new SecurityRequest(OperationType.HTTP, SourceIdentity.unknown("unknown"), "url", Map.of());
        assertEquals(Decision.DENY, new PolicyEngine(config).evaluate(unknown).decision());
    }

    @Test void askPresetIncludesProcessesAndUnknownSources() {
        PolicyConfig config = new PolicyConfig();
        config.applyPreset(PolicyConfig.Preset.ASK_ALL);
        PolicyEngine engine = new PolicyEngine(config);
        for (OperationType operation : OperationType.values()) {
            assertEquals(Decision.ASK, engine.evaluate(request(operation)).decision());
        }
        var unknown = new SecurityRequest(OperationType.HTTP, null, "url", Map.of());
        assertEquals(Decision.ASK, engine.evaluate(unknown).decision());
    }

    @Test void recommendedPresetBlocksProcessesButAsksForNetwork() {
        PolicyConfig config = new PolicyConfig();
        config.applyPreset(PolicyConfig.Preset.RECOMMENDED);
        PolicyEngine engine = new PolicyEngine(config);
        assertEquals(Decision.DENY, engine.evaluate(request(OperationType.PROCESS_START)).decision());
        assertEquals(Decision.DENY, engine.evaluate(request(OperationType.TCP_LISTEN)).decision());
        assertEquals(Decision.ASK, engine.evaluate(request(OperationType.HTTP)).decision());
    }

    @Test void blockPresetKeepsSpecificExceptions() {
        PolicyConfig config = new PolicyConfig();
        var rule = new PolicyRule("trusted", true, Decision.ALLOW, OperationType.HTTP,
                "test", "1", "abc", "*example.invalid*", false);
        config.rules.add(rule);
        config.applyPreset(PolicyConfig.Preset.BLOCK_ALL);
        PolicyEngine engine = new PolicyEngine(config);
        assertEquals(Decision.ALLOW, engine.evaluate(request(OperationType.HTTP)).decision());
        assertEquals(Decision.DENY, engine.evaluate(request(OperationType.FILE_WRITE)).decision());
        assertEquals(rule, config.rules.getFirst());
    }

    @Test void settingsSavePreservesRulesAddedWhileScreenWasOpen(@TempDir Path directory) throws Exception {
        PolicyStore store = new PolicyStore();
        Path path = directory.resolve("rules.json");
        PolicyConfig draft = store.load(path);
        draft.operationDecisions.put(OperationType.FILE_WRITE, Decision.ASK);
        draft.logAllowedOperations = false;
        PolicyConfig live = new PolicyConfig();
        live.rules.add(new PolicyRule("new-rule", true, Decision.DENY, OperationType.HTTP,
                "test", null, null, null, false));
        store.save(path, live);
        store.savePreferences(path, draft);
        PolicyConfig loaded = store.load(path);
        assertEquals("new-rule", loaded.rules.getFirst().id());
        assertEquals(Decision.ASK, loaded.operationDecisions.get(OperationType.FILE_WRITE));
        assertFalse(loaded.logAllowedOperations);
    }

    @Test void legacyConfigurationPreservesItsDecisions(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rules.json");
        Files.writeString(path, """
                {"schemaVersion":1,"strictMode":true,"unknownSourceDecision":"DENY",
                 "defaultDecision":"ALLOW","promptTimeoutSeconds":15,"maxLogEntries":2000,"rules":[]}
                """);
        PolicyConfig config = new PolicyStore().load(path);
        assertTrue(config.operationDecisions.isEmpty());
        assertEquals(Decision.ALLOW, new PolicyEngine(config).evaluate(request(OperationType.HTTP)).decision());
        assertEquals(Decision.DENY, new PolicyEngine(config).evaluate(request(OperationType.PROCESS_START)).decision());
    }

    @Test void invalidSettingsCannotReplaceSavedConfiguration(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rules.json");
        PolicyStore store = new PolicyStore();
        store.save(path, new PolicyConfig());
        String original = Files.readString(path);
        PolicyConfig invalid = new PolicyConfig();
        invalid.promptTimeoutSeconds = 0;
        assertThrows(IOException.class, () -> store.save(path, invalid));
        assertEquals(original, Files.readString(path));
        invalid.promptTimeoutSeconds = 15;
        invalid.operationDecisions.put(OperationType.HTTP, null);
        assertThrows(IOException.class, () -> store.save(path, invalid));
    }
}
