package dev.nmrfirewall.core;

import java.util.Map;

public record SecurityRequest(OperationType operation, SourceIdentity source, String target,
                              Map<String, String> parameters) {
    public SecurityRequest {
        if (operation == null) throw new IllegalArgumentException("operation is required");
        source = source == null ? SourceIdentity.unknown("unknown") : source;
        target = target == null ? "unknown" : target;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public boolean highRisk() {
        return operation == OperationType.PROCESS_START || operation == OperationType.DYNAMIC_CODE
                || operation == OperationType.SECRET_ACCESS;
    }

    public RiskLevel riskLevel() {
        return switch (operation) {
            case PROCESS_START, DYNAMIC_CODE, SECRET_ACCESS -> RiskLevel.CRITICAL;
            case TCP_LISTEN, FILE_WRITE -> RiskLevel.HIGH;
            case HTTP, TCP_CONNECT, UDP -> RiskLevel.MEDIUM;
        };
    }

    public String riskReason() {
        return switch (operation) {
            case HTTP -> "Can send data to an external server.";
            case TCP_CONNECT, UDP -> "Can communicate with an external endpoint.";
            case TCP_LISTEN -> "Can expose a local network service.";
            case FILE_WRITE -> "Can change local files.";
            case PROCESS_START -> "Can launch another program outside Minecraft.";
            case DYNAMIC_CODE -> "Can alter code execution at runtime.";
            case SECRET_ACCESS -> "Can access potentially sensitive data.";
        };
    }
}
