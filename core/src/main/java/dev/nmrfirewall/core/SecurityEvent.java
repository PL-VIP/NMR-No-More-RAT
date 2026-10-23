package dev.nmrfirewall.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SecurityEvent(
        UUID id,
        Instant timestamp,
        OperationType operation,
        SourceIdentity source,
        String target,
        Map<String, String> parameters,
        List<String> stack,
        String matchedRule,
        Decision decision) {

    public SecurityEvent {
        id = id == null ? UUID.randomUUID() : id;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        source = source == null ? SourceIdentity.unknown("unknown") : source;
        target = operation == OperationType.HTTP ? HttpInspection.displayTarget(target)
                : SecretRedactor.redact(target == null ? "unknown" : target);
        parameters = parameters == null ? Map.of() : parameters.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        e -> SecretRedactor.redact(e.getValue())));
        stack = stack == null ? List.of() : List.copyOf(stack);
        matchedRule = matchedRule == null ? "default" : matchedRule;
        decision = decision == null ? Decision.DENY : decision;
    }
}
