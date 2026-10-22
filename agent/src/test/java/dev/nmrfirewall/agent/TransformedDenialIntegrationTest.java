package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class TransformedDenialIntegrationTest {
    @Test void transformedCallIsDeniedBeforeProcessBuilderRuns() throws Exception {
        AgentRuntime.initialize("config=" + java.nio.file.Files.createTempDirectory("nmr-test-policy"));
        String name = Fixture.class.getName();
        byte[] original;
        try (InputStream input = Fixture.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            original = input.readAllBytes();
        }
        byte[] transformed = new SecurityTransformer().transform(null, Fixture.class.getClassLoader(),
                "untrusted/fixture/ProcessCaller", null, Fixture.class.getProtectionDomain(), original);
        ClassLoader loader = new ClassLoader(Fixture.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (className.equals(name)) {
                    Class<?> defined = findLoadedClass(className);
                    if (defined == null) defined = defineClass(className, transformed, 0, transformed.length);
                    if (resolve) resolveClass(defined);
                    return defined;
                }
                return super.loadClass(className, resolve);
            }
        };
        Class<?> fixture = loader.loadClass(name);
        var constructor = fixture.getDeclaredConstructor(); constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        var method = fixture.getDeclaredMethod("execute"); method.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(instance));
        assertInstanceOf(BlockedOperationException.class, thrown.getCause());
    }

    public static final class Fixture {
        public Process execute() throws Exception { return new ProcessBuilder("nmr-must-never-run").start(); }
    }
}
