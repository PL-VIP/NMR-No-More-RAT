package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import org.junit.jupiter.api.Test;

class SecurityTransformerTest {
    @Test void excludesMixinExtrasStartupInfrastructure() throws Exception {
        String resource = ProcessFixture.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ProcessFixture.class.getClassLoader().getResourceAsStream(resource)) {
            assertNull(new SecurityTransformer().transform(null, ProcessFixture.class.getClassLoader(),
                    "com/llamalad7/mixinextras/utils/MixinExtrasLogger", null,
                    ProcessFixture.class.getProtectionDomain(), input.readAllBytes()));
        }
    }

    @Test void instrumentsProcessStartCallSite() throws Exception {
        String resource = ProcessFixture.class.getName().replace('.', '/') + ".class";
        byte[] original;
        try (InputStream input = ProcessFixture.class.getClassLoader().getResourceAsStream(resource)) {
            original = input.readAllBytes();
        }
        byte[] transformed = new SecurityTransformer().transform(null, ProcessFixture.class.getClassLoader(),
                "untrusted/example/ProcessFixture", null,
                ProcessFixture.class.getProtectionDomain(), original);
        assertNotNull(transformed, "ProcessBuilder.start call site must receive a guard hook");
    }

    @Test void ignoresLauncherCodeOutsideTheGameInstance() throws Exception {
        String previous = System.getProperty("nmr.agent.testAll");
        System.clearProperty("nmr.agent.testAll");
        try {
            byte[] transformed = transformWithLocation(Path.of(System.getProperty("user.dir"), "launcher", "bootstrap.jar"));
            assertNull(transformed, "launcher/bootstrap code outside mods must never be instrumented");
        } finally {
            restoreTestAll(previous);
        }
    }

    @Test void instrumentsCodeLoadedFromTheInstanceModsDirectory() throws Exception {
        String previous = System.getProperty("nmr.agent.testAll");
        System.clearProperty("nmr.agent.testAll");
        try {
            byte[] transformed = transformWithLocation(Path.of(System.getProperty("user.dir"), "mods", "example.jar"));
            assertNotNull(transformed, "classes loaded from the instance mods directory must be instrumented");
        } finally {
            restoreTestAll(previous);
        }
    }

    @Test void instrumentsCodeLoadedFromANestedModrinthProfileWithSpaces() throws Exception {
        String previous = System.getProperty("nmr.agent.testAll");
        System.clearProperty("nmr.agent.testAll");
        try {
            Path profileJar = Path.of("C:", "Users", "player", "AppData", "Roaming", "ModrinthApp",
                    "profiles", "1.21.4 test", "mods", "asddassda-1.21.4-fabric.jar");
            assertNotNull(transformWithLocation(profileJar),
                    "Modrinth may relaunch with a user.dir outside the profile; the mods path itself is authoritative");
        } finally {
            restoreTestAll(previous);
        }
    }

    @Test void instrumentsUntrustedCodeWithoutAProtectionDomain() throws Exception {
        String previous = System.getProperty("nmr.agent.testAll");
        System.clearProperty("nmr.agent.testAll");
        try {
            assertNotNull(transformWithDomain(null),
                    "custom class loaders must not bypass enforcement by omitting CodeSource");
        } finally {
            restoreTestAll(previous);
        }
    }

    private static byte[] transformWithLocation(Path location) throws Exception {
        CodeSource source = new CodeSource(location.toUri().toURL(), (Certificate[]) null);
        return transformWithDomain(new ProtectionDomain(source, null));
    }

    private static byte[] transformWithDomain(ProtectionDomain domain) throws Exception {
        String resource = ProcessFixture.class.getName().replace('.', '/') + ".class";
        byte[] original;
        try (InputStream input = ProcessFixture.class.getClassLoader().getResourceAsStream(resource)) {
            original = input.readAllBytes();
        }
        return new SecurityTransformer().transform(null, ProcessFixture.class.getClassLoader(),
                "untrusted/example/ProcessFixture", null, domain, original);
    }

    private static void restoreTestAll(String previous) {
        if (previous == null) System.clearProperty("nmr.agent.testAll");
        else System.setProperty("nmr.agent.testAll", previous);
    }

    static final class ProcessFixture {
        Process execute() throws Exception { return new ProcessBuilder("definitely-not-run").start(); }
    }
}
