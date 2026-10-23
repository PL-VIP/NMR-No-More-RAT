package dev.nmrfirewall.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PolicyEngine {
    private final AtomicReference<PolicyConfig> config;

    public PolicyEngine(PolicyConfig initial) {
        this.config = new AtomicReference<>(initial == null ? new PolicyConfig() : initial);
    }

    public PolicyResult evaluate(SecurityRequest request) {
        PolicyConfig current = config.get();
        if (current.blockAutomaticHttpRedirects && request.operation() == OperationType.HTTP
                && "enabled".equals(request.parameters().get("redirects"))) {
            return new PolicyResult(Decision.DENY, "automatic-http-redirects");
        }
        List<PolicyRule> rules = List.copyOf(current.rules);
        for (PolicyRule rule : rules) {
            if (rule.matches(request, current.strictMode)) return new PolicyResult(rule.decision(), rule.id());
        }
        if (request.source().confidence() == SourceIdentity.Confidence.UNKNOWN) {
            return new PolicyResult(current.unknownSourceDecision, "unknown-source-default");
        }
        if (current.operationDecisions.containsKey(request.operation())) {
            return new PolicyResult(current.decisionFor(request.operation()), "operation-" + request.operation().name());
        }
        if (current.strictMode && request.highRisk()) return new PolicyResult(Decision.DENY, "strict-high-risk-default");
        return new PolicyResult(current.defaultDecision, "default");
    }

    public PolicyConfig config() { return config.get(); }
    public void replace(PolicyConfig replacement) { config.set(replacement); }
}
