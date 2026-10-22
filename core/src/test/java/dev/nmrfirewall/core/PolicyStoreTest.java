package dev.nmrfirewall.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class PolicyStoreTest {
    @Test void exportsAndImportsPolicy(@TempDir Path directory) throws Exception {
        PolicyConfig config = new PolicyConfig();
        config.strictMode = false;
        Path file = directory.resolve("rules.json");
        PolicyStore store = new PolicyStore();
        store.save(file, config);
        assertEquals(false, store.load(file).strictMode);
    }
}
