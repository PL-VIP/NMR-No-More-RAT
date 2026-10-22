package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {
    @Test void masksCommonCredentialForms() {
        String raw = "Authorization: Bearer abc.def.ghi access_token=supersecret password=hunter2";
        String redacted = SecretRedactor.redact(raw);
        assertFalse(redacted.contains("supersecret"));
        assertFalse(redacted.contains("hunter2"));
        assertTrue(redacted.contains("<REDACTED>"));
    }
}
