package dev.nmrfirewall.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import dev.nmrfirewall.core.OperationType;
import org.junit.jupiter.api.Test;

class CoverageAuditTest {
    @Test void classifiesSupportedNetworkPaths() {
        assertEquals(OperationType.HTTP, type("java/net/URL", "openConnection"));
        assertEquals(OperationType.HTTP, type("java/net/HttpURLConnection", "getResponseCode"));
        assertEquals(OperationType.HTTP, type("java/net/HttpURLConnection", "getErrorStream"));
        assertEquals(OperationType.HTTP, type("java/net/http/HttpClient", "send"));
        assertEquals(OperationType.HTTP, type("okhttp3/Call", "execute"));
        assertEquals(OperationType.TCP_CONNECT, type("java/net/Socket", "connect"));
        assertEquals(OperationType.TCP_CONNECT, type("java/nio/channels/AsynchronousSocketChannel", "connect"));
        assertEquals(OperationType.TCP_CONNECT, type("io/netty/channel/Channel", "connect"));
        assertEquals(OperationType.UDP, type("java/net/DatagramSocket", "send"));
    }

    @Test void classifiesSupportedWriteProcessAndCodePaths() {
        assertEquals(OperationType.FILE_WRITE, type("java/nio/file/Files", "write"));
        assertEquals(OperationType.FILE_WRITE, type("java/nio/file/Files", "copy"));
        assertEquals(OperationType.FILE_WRITE, type("java/nio/file/Files", "deleteIfExists"));
        assertEquals(OperationType.FILE_WRITE, type("java/nio/file/Files", "createDirectories"));
        assertEquals(OperationType.FILE_WRITE, type("java/nio/file/Files", "newOutputStream"));
        assertEquals(OperationType.FILE_WRITE, type("java/nio/channels/FileChannel", "open"));
        assertEquals(OperationType.PROCESS_START, type("java/lang/Runtime", "exec"));
        assertEquals(OperationType.PROCESS_START, type("java/lang/ProcessBuilder", "start"));
        assertEquals(OperationType.DYNAMIC_CODE, type("java/lang/invoke/MethodHandles$Lookup", "defineClass"));
        assertEquals(OperationType.DYNAMIC_CODE, type("java/lang/reflect/Method", "invoke"));
        assertEquals(OperationType.DYNAMIC_CODE, type("java/lang/reflect/Constructor", "newInstance"));
    }

    @Test void explicitlyRevealsKnownUninstrumentedAlternatives() {
        assertNull(type("sun/misc/Unsafe", "defineClass"), "Unsafe/JNI require stronger isolation");
        assertNull(type("java/io/FileOutputStream", "<init>"), "constructor interception is intentionally not claimed");
    }

    private static OperationType type(String owner, String method) {
        return SecurityTransformer.classify(owner, method, "()V");
    }
}
