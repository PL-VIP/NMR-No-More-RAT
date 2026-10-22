package dev.nmrfirewall.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

final class BootstrapInstaller {
    // Instrumentation requires this JAR to remain available for the JVM lifetime.
    private static JarFile bridgeJar;

    private BootstrapInstaller() { }

    static synchronized void install(Instrumentation instrumentation) throws IOException, ClassNotFoundException {
        if (bridgeJar != null) return;
        try (var input = BootstrapInstaller.class.getResourceAsStream("/nmr-bootstrap/bridge.bin")) {
            if (input == null) throw new IOException("Missing embedded bootstrap guard");
            var file = Files.createTempFile("nmr-bootstrap-", ".jar");
            file.toFile().deleteOnExit();
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            bridgeJar = new JarFile(file.toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bridgeJar);
        }
        Class<?> bridge = Class.forName("dev.nmrfirewall.bootstrap.GuardBridge", true, null);
        if (bridge.getClassLoader() != null) throw new IllegalStateException("Guard must use bootstrap loader");
    }
}
