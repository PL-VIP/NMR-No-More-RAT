package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;

public final class GuardHooks {
    private GuardHooks() { }
    public static void before(String operation, String target) {
        AgentRuntime.enforce(OperationType.valueOf(operation), target);
    }
    public static void beforeObject(Object target, String operation, String api) {
        AgentRuntime.enforce(OperationType.valueOf(operation), target, api);
    }
}
