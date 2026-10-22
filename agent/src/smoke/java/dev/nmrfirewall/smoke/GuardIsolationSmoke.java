package dev.nmrfirewall.smoke;

import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.EnvType;

/** Uses Fabric's real, restricted Knot loader, without starting Minecraft or loading user mods. */
public final class GuardIsolationSmoke {
    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean("nmr.agent.testAll")) throw new AssertionError("Must exercise production scope");
        Class<?> api = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClassLoaderInterface");
        var create = api.getDeclaredMethod("create", boolean.class, boolean.class, EnvType.class,
                Class.forName("net.fabricmc.loader.impl.game.GameProvider"));
        create.setAccessible(true);
        Object target = create.invoke(null, false, false, EnvType.CLIENT, null);
        var parentPaths = api.getDeclaredMethod("setValidParentClassPath", java.util.Collection.class);
        parentPaths.setAccessible(true);
        parentPaths.invoke(target, List.of());
        var addSource = api.getDeclaredMethod("addCodeSource", Path.class);
        addSource.setAccessible(true);
        addSource.invoke(target, Path.of(args[0]));
        var getLoader = api.getDeclaredMethod("getClassLoader");
        getLoader.setAccessible(true);
        ClassLoader knot = (ClassLoader) getLoader.invoke(target);
        try {
            knot.loadClass("dev.nmrfirewall.agent.GuardHooks");
            throw new AssertionError("Agent implementation must remain isolated from Knot");
        } catch (ClassNotFoundException expected) {
            System.out.println("NMR_KNOT_ISOLATION_OK: agent implementation is not exposed to the game");
        }
        Class<?> bridge = knot.loadClass("dev.nmrfirewall.bootstrap.GuardBridge");
        if (bridge.getClassLoader() != null) throw new AssertionError("Guard must resolve through bootstrap");
        Class<?> fixture = knot.loadClass("untrusted.smoke.AgentSmoke");
        if (fixture.getClassLoader() != knot) throw new AssertionError("Fixture must run inside Knot");
        fixture.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        System.out.println("NMR_KNOT_GUARDS_OK: real Fabric loader resolves guards and enforces denials");
    }
}
