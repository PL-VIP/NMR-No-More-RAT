package dev.nmrfirewall.core;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PromptHandler {
    CompletableFuture<Decision> request(SecurityRequest request, PolicyResult preliminary);
}
