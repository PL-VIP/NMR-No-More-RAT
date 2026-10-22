package dev.nmrfirewall.bootstrap;

/** JDK-only boundary shared by the bootstrap, launcher and game class loaders. */
public final class GuardBridge {
    private static volatile Dispatcher dispatcher;

    private GuardBridge() { }

    public interface Dispatcher {
        void check(String operation, Object target, String api);
    }

    public static synchronized void install(Dispatcher handler) {
        if (handler == null || dispatcher != null) throw new IllegalStateException("Guard already initialized or missing");
        dispatcher = handler;
    }

    public static void before(String operation, String api) {
        beforeObject(null, operation, api);
    }

    public static void beforeObject(Object target, String operation, String api) {
        Dispatcher handler = dispatcher;
        if (handler == null) throw new SecurityException("No More RAT guard is not initialized");
        handler.check(operation, target, api);
    }
}
