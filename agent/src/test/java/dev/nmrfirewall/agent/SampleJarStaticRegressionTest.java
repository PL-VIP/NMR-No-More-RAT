package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.nmrfirewall.core.OperationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.EnumSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class SampleJarStaticRegressionTest {
    @Test void attachedSampleReceivesGuardsWithoutExecutingIt() throws Exception {
        String configured = System.getProperty("nmr.sampleJar", "");
        assumeTrue(!configured.isBlank(), "set -Dnmr.sampleJar to run the local sample regression");
        Path sample = Path.of(configured).toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(sample), "configured sample JAR does not exist");

        String previous = System.getProperty("nmr.agent.testAll");
        System.clearProperty("nmr.agent.testAll");
        try {
            Path installedLocation = Path.of("profiles", "1.21.4 test", "mods", sample.getFileName().toString());
            CodeSource source = new CodeSource(installedLocation.toUri().toURL(), (Certificate[]) null);
            ProtectionDomain domain = new ProtectionDomain(source, null);
            Set<OperationType> guarded = EnumSet.noneOf(OperationType.class);
            int[] transformedClasses = {0};

            try (JarFile jar = new JarFile(sample.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                    byte[] original;
                    try (var input = jar.getInputStream(entry)) {
                        original = input.readAllBytes();
                    }
                    String className = entry.getName().substring(0, entry.getName().length() - 6);
                    byte[] transformed = new SecurityTransformer().transform(null, getClass().getClassLoader(),
                            className, null, domain, original);
                    if (transformed != null) {
                        transformedClasses[0]++;
                        collectGuardedOperations(transformed, guarded);
                    }
                }
            }

            assertTrue(transformedClasses[0] > 0, "sample classes must not escape transformation");
            assertTrue(guarded.contains(OperationType.HTTP), "sample HTTP calls must receive a pre-operation guard");
            assertTrue(guarded.contains(OperationType.FILE_WRITE), "sample file writes must receive a pre-operation guard");
            assertTrue(guarded.contains(OperationType.PROCESS_START), "sample process starts must receive a pre-operation guard");
            assertTrue(guarded.contains(OperationType.DYNAMIC_CODE), "sample reflection must receive a pre-operation guard");
        } finally {
            if (previous == null) System.clearProperty("nmr.agent.testAll");
            else System.setProperty("nmr.agent.testAll", previous);
        }
    }

    private static void collectGuardedOperations(byte[] bytecode, Set<OperationType> guarded) {
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private OperationType pending;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String text) {
                            try { pending = OperationType.valueOf(text); }
                            catch (IllegalArgumentException ignored) { }
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                                boolean isInterface) {
                        if (owner.equals("dev/nmrfirewall/bootstrap/GuardBridge") && pending != null) {
                            guarded.add(pending);
                            pending = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
