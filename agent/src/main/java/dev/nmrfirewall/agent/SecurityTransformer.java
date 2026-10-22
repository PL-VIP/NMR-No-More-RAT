package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

final class SecurityTransformer implements ClassFileTransformer {
    private static final String HOOKS = "dev/nmrfirewall/bootstrap/GuardBridge";
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/objectweb/asm/",
            "dev/nmrfirewall/", "net/minecraft/", "com/mojang/", "net/fabricmc/",
            "com/modrinth/theseus/", "com/llamalad7/mixinextras/",
            "org/spongepowered/", "io/netty/", "org/slf4j/", "com/google/gson/");

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || excluded(className)) return null;
        if (!shouldInstrument(protectionDomain)) return null;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new LoaderAwareClassWriter(reader, loader);
            final boolean[] changed = {false};
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new AdviceAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                                    boolean isInterface) {
                            OperationType type = classify(owner, methodName, methodDescriptor);
                            if (type != null) {
                                changed[0] = true;
                                String api = owner.replace('/', '.') + "#" + methodName;
                                if (owner.equals("java/net/http/HttpClient")
                                        && (methodName.equals("send") || methodName.equals("sendAsync"))) {
                                    captureArgument(type, api, methodDescriptor, 0);
                                } else if (owner.equals("java/nio/file/Files")) {
                                    captureArgument(type, api, methodDescriptor,
                                            methodName.equals("copy") || methodName.equals("move") ? 1 : 0);
                                } else if (canCaptureTop(opcode, owner, methodName, methodDescriptor)) {
                                    super.visitInsn(Opcodes.DUP);
                                    super.visitLdcInsn(type.name());
                                    super.visitLdcInsn(api);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "beforeObject",
                                            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false);
                                } else {
                                    super.visitLdcInsn(type.name());
                                    super.visitLdcInsn(api);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "before",
                                            "(Ljava/lang/String;Ljava/lang/String;)V", false);
                                }
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        }

                        private void captureArgument(OperationType type, String api, String descriptor,
                                                     int argumentIndex) {
                            Type[] arguments = Type.getArgumentTypes(descriptor);
                            if (argumentIndex < 0 || argumentIndex >= arguments.length
                                    || arguments[argumentIndex].getSort() < Type.ARRAY) {
                                super.visitLdcInsn(type.name());
                                super.visitLdcInsn(api);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "before",
                                        "(Ljava/lang/String;Ljava/lang/String;)V", false);
                                return;
                            }
                            int trailing = arguments.length - argumentIndex - 1;
                            int[] locals = new int[trailing];
                            for (int i = trailing - 1; i >= 0; i--) {
                                Type argument = arguments[argumentIndex + i + 1];
                                locals[i] = newLocal(argument);
                                super.visitVarInsn(argument.getOpcode(Opcodes.ISTORE), locals[i]);
                            }
                            super.visitInsn(Opcodes.DUP);
                            super.visitLdcInsn(type.name());
                            super.visitLdcInsn(api);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "beforeObject",
                                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false);
                            for (int i = 0; i < trailing; i++) {
                                Type argument = arguments[argumentIndex + i + 1];
                                super.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), locals[i]);
                            }
                        }
                    };
                }
            };
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return changed[0] ? writer.toByteArray() : null;
        } catch (Throwable failure) {
            System.err.println("[No More RAT] Could not instrument " + className + ": " + failure);
            return null;
        }
    }

    private static boolean excluded(String name) {
        return EXCLUDED_PREFIXES.stream().anyMatch(name::startsWith)
                || name.contains("$$Lambda$") && name.startsWith("java/");
    }

    private static boolean shouldInstrument(ProtectionDomain domain) {
        if (Boolean.getBoolean("nmr.agent.testAll")) return true;
        try {
            // Classes created by an untrusted custom loader may not have a CodeSource.
            // Instrument them unless their package is explicitly trusted above.
            if (domain == null || domain.getCodeSource() == null
                    || domain.getCodeSource().getLocation() == null) return true;
            Path source = Path.of(domain.getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            for (Path segment : source) {
                String name = segment.toString().toLowerCase(Locale.ROOT);
                if (name.equals("mods") || name.equals(".fabric")) return true;
            }
            return false;
        } catch (Exception ignored) {
            // An unresolvable origin is not a reason to trust executable code.
            return true;
        }
    }

    static OperationType classify(String owner, String name, String descriptor) {
        if ((owner.equals("java/net/URL") && (name.equals("openConnection") || name.equals("openStream")))
                || (owner.equals("java/net/URLConnection") && ioMethod(name))
                || (owner.equals("java/net/HttpURLConnection") && ioMethod(name))
                || (owner.equals("java/net/http/HttpClient") && (name.equals("send") || name.equals("sendAsync")))) {
            return OperationType.HTTP;
        }
        if ((owner.equals("okhttp3/Call") && (name.equals("execute") || name.equals("enqueue")))
                || (owner.startsWith("org/apache/hc/client5/http/") && name.equals("execute"))
                || (owner.startsWith("org/apache/http/client/") && name.equals("execute"))) {
            return OperationType.HTTP;
        }
        if ((owner.equals("java/net/Socket") && name.equals("connect"))
                || (owner.equals("java/nio/channels/SocketChannel") && (name.equals("connect") || name.equals("open")))
                || (owner.equals("java/nio/channels/AsynchronousSocketChannel") && (name.equals("connect") || name.equals("open")))) {
            return OperationType.TCP_CONNECT;
        }
        if (owner.startsWith("io/netty/channel/") && (name.equals("connect") || name.equals("bind")
                || name.equals("writeAndFlush"))) return OperationType.TCP_CONNECT;
        if ((owner.equals("java/net/ServerSocket") && (name.equals("bind") || name.equals("<init>")))) {
            return OperationType.TCP_LISTEN;
        }
        if ((owner.equals("java/net/DatagramSocket") && (name.equals("connect") || name.equals("send") || name.equals("bind")))
                || (owner.equals("java/nio/channels/DatagramChannel") && (name.equals("connect") || name.equals("send") || name.equals("open")))) {
            return OperationType.UDP;
        }
        if ((owner.equals("java/lang/Runtime") && name.equals("exec"))
                || (owner.equals("java/lang/ProcessBuilder") && name.equals("start"))) {
            return OperationType.PROCESS_START;
        }
        if ((owner.equals("java/nio/file/Files") && fileWriteMethod(name))
                || (owner.equals("java/nio/channels/FileChannel") && name.equals("open"))) {
            return OperationType.FILE_WRITE;
        }
        if ((owner.equals("java/lang/reflect/Method") && name.equals("invoke"))
                || (owner.equals("java/lang/reflect/Constructor") && name.equals("newInstance"))
                || (owner.equals("java/lang/reflect/AccessibleObject") && name.equals("setAccessible"))
                || (owner.equals("java/lang/reflect/Field") && name.equals("setAccessible"))
                || (owner.equals("java/lang/reflect/Method") && name.equals("setAccessible"))
                || (owner.equals("java/lang/reflect/Constructor") && name.equals("setAccessible"))
                || (owner.equals("java/lang/invoke/MethodHandles$Lookup") && name.equals("defineClass"))
                || (owner.equals("java/net/URLClassLoader") && name.equals("<init>"))
                || (owner.equals("java/lang/ClassLoader") && name.startsWith("defineClass"))) {
            return OperationType.DYNAMIC_CODE;
        }
        return null;
    }

    private static boolean ioMethod(String name) {
        return name.equals("connect") || name.equals("getInputStream") || name.equals("getOutputStream")
                || name.equals("getResponseCode") || name.equals("getResponseMessage")
                || name.equals("getHeaderFields") || name.equals("getContent") || name.equals("getErrorStream");
    }

    private static boolean fileWriteMethod(String name) {
        return name.equals("write") || name.equals("writeString") || name.equals("copy") || name.equals("move")
                || name.equals("delete") || name.equals("deleteIfExists") || name.equals("createFile")
                || name.equals("createDirectory") || name.equals("createDirectories")
                || name.equals("newOutputStream") || name.equals("setAttribute");
    }

    private static boolean canCaptureTop(int opcode, String owner, String name, String descriptor) {
        if (name.equals("<init>")) return false;
        if (owner.equals("java/net/URL")) return descriptor.startsWith("()");
        if (owner.equals("java/net/URLConnection") || owner.equals("java/net/HttpURLConnection")) {
            return descriptor.startsWith("()");
        }
        if (owner.equals("java/lang/ProcessBuilder") && name.equals("start")) return true;
        if (owner.equals("okhttp3/Call") && (name.equals("execute") || name.equals("enqueue"))) {
            return descriptor.startsWith("()");
        }
        if (owner.equals("java/net/Socket") || owner.equals("java/nio/channels/SocketChannel")
                || owner.equals("java/nio/channels/AsynchronousSocketChannel")) {
            return descriptor.startsWith("(Ljava/net/SocketAddress;)");
        }
        if (owner.equals("java/net/DatagramSocket")) {
            return descriptor.startsWith("(Ljava/net/DatagramPacket;)")
                    || descriptor.startsWith("(Ljava/net/SocketAddress;)");
        }
        if (owner.equals("java/nio/channels/DatagramChannel") && name.equals("send")) {
            return descriptor.startsWith("(Ljava/nio/ByteBuffer;Ljava/net/SocketAddress;)");
        }
        if (owner.equals("java/lang/Runtime") && name.equals("exec")) {
            return descriptor.equals("(Ljava/lang/String;)Ljava/lang/Process;")
                    || descriptor.equals("([Ljava/lang/String;)Ljava/lang/Process;");
        }
        return opcode == Opcodes.INVOKESTATIC && descriptor.startsWith("(Ljava/net/SocketAddress;)");
    }

    private static final class LoaderAwareClassWriter extends ClassWriter {
        private final ClassLoader loader;

        private LoaderAwareClassWriter(ClassReader reader, ClassLoader loader) {
            super(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            this.loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
        }

        @Override
        protected String getCommonSuperClass(String first, String second) {
            if (first.equals(second)) return first;
            try {
                Class<?> left = Class.forName(first.replace('/', '.'), false, loader);
                Class<?> right = Class.forName(second.replace('/', '.'), false, loader);
                if (left.isAssignableFrom(right)) return first;
                if (right.isAssignableFrom(left)) return second;
                if (left.isInterface() || right.isInterface()) return "java/lang/Object";
                do {
                    left = left.getSuperclass();
                } while (left != null && !left.isAssignableFrom(right));
                return left == null ? "java/lang/Object" : left.getName().replace('.', '/');
            } catch (Throwable unavailableDuringClassLoad) {
                return "java/lang/Object";
            }
        }
    }
}
