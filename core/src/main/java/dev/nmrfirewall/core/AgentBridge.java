package dev.nmrfirewall.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentBridge {
    private static final AtomicReference<PromptHandler> HANDLER = new AtomicReference<>();

    private AgentBridge() { }
    public static void register(PromptHandler handler) { HANDLER.set(Objects.requireNonNull(handler)); }
    public static void unregister(PromptHandler handler) { HANDLER.compareAndSet(handler, null); }
    public static PromptHandler handler() { return HANDLER.get(); }
}
