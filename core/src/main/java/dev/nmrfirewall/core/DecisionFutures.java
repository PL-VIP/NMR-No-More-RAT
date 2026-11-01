package dev.nmrfirewall.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Preserve cancellation across the JDK-only agent/UI class-loader boundary. */
public final class DecisionFutures {
    private DecisionFutures() { }
    public static <T, R> CompletableFuture<R> map(CompletableFuture<T> source, Function<T, R> mapper) {
        CompletableFuture<R> mapped = source.thenApply(mapper);
        mapped.whenComplete((value, error) -> {
            if (mapped.isCancelled()) source.cancel(false);
        });
        return mapped;
    }
}
