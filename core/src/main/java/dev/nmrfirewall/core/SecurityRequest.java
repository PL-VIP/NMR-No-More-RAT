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
}
