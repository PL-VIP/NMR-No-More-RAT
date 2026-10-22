package dev.nmrfirewall.agent;

import java.lang.instrument.Instrumentation;

public final class NoMoreRatAgent {
    private NoMoreRatAgent() { }

    public static void premain(String arguments, Instrumentation instrumentation) {
        start(arguments, instrumentation);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        start(arguments, instrumentation);
    }

    private static synchronized void start(String arguments, Instrumentation instrumentation) {
        if (Boolean.getBoolean("nmr.agent.active")) return;
        try {
            BootstrapInstaller.install(instrumentation);
        } catch (Exception failure) {
            throw new IllegalStateException("No More RAT could not install the shared guard", failure);
        }
        AgentRuntime.initialize(arguments);
        instrumentation.addTransformer(new SecurityTransformer(), true);
        System.setProperty("nmr.agent.active", "true");
        System.err.println("[No More RAT] Security agent active; call-site enforcement enabled.");
    }
}
