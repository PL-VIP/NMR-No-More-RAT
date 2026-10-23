package dev.nmrfirewall.core;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

public final class PolicyConfig {
    public int schemaVersion = 1;
    public boolean strictMode = true;
    public Decision unknownSourceDecision = Decision.DENY;
    public Decision defaultDecision = Decision.ASK;
    public int promptTimeoutSeconds = 15;
    public int maxLogEntries = 2000;
    public boolean logAllowedOperations = true;
    public boolean blockAutomaticHttpRedirects = true;
    public Map<OperationType, Decision> operationDecisions = new EnumMap<>(OperationType.class);
    public List<PolicyRule> rules = new ArrayList<>();

    public enum Preset { RECOMMENDED, ASK_ALL, BLOCK_ALL }

    /** Empty overrides preserve the behavior of existing schema-1 configurations. */
    public Decision decisionFor(OperationType operation) {
        Decision override = operationDecisions.get(operation);
        if (override != null) return override;
        if (strictMode && (operation == OperationType.PROCESS_START || operation == OperationType.DYNAMIC_CODE
                || operation == OperationType.SECRET_ACCESS)) return Decision.DENY;
        return defaultDecision;
    }

    public void applyPreset(Preset preset) {
        strictMode = true;
        blockAutomaticHttpRedirects = true;
        defaultDecision = preset == Preset.BLOCK_ALL ? Decision.DENY : Decision.ASK;
        unknownSourceDecision = preset == Preset.ASK_ALL ? Decision.ASK : Decision.DENY;
        operationDecisions.clear();
        for (OperationType operation : OperationType.values()) {
            boolean highRisk = operation == OperationType.PROCESS_START || operation == OperationType.DYNAMIC_CODE
                    || operation == OperationType.SECRET_ACCESS || operation == OperationType.TCP_LISTEN;
            Decision decision = switch (preset) {
                case ASK_ALL -> Decision.ASK;
                case BLOCK_ALL -> Decision.DENY;
                case RECOMMENDED -> highRisk ? Decision.DENY : Decision.ASK;
            };
            operationDecisions.put(operation, decision);
        }
    }

    public void copyPreferencesFrom(PolicyConfig other) {
        strictMode = other.strictMode;
        unknownSourceDecision = other.unknownSourceDecision;
        defaultDecision = other.defaultDecision;
        promptTimeoutSeconds = other.promptTimeoutSeconds;
        maxLogEntries = other.maxLogEntries;
        logAllowedOperations = other.logAllowedOperations;
        blockAutomaticHttpRedirects = other.blockAutomaticHttpRedirects;
        operationDecisions = new EnumMap<>(OperationType.class);
        operationDecisions.putAll(other.operationDecisions);
    }
}
