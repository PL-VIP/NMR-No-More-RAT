package dev.nmrfirewall.fabric;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MetadataAndLanguageTest {
    @Test void metadataContainsRepositoryAndReadableSquareIcon() throws Exception {
        JsonObject metadata = read("/fabric.mod.json");
        assertEquals("https://github.com/PL-VIP/NMR-No-More-RAT", metadata.getAsJsonObject("contact").get("sources").getAsString());
        assertTrue(metadata.get("description").getAsString().contains("Java agent"));
        try (var stream = getClass().getResourceAsStream("/" + metadata.get("icon").getAsString())) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(image.getWidth(), image.getHeight());
        }
    }

    @Test void englishAndPolishHaveMatchingKeysAndFormatArguments() throws Exception {
        JsonObject english = read("/assets/no_more_rat/lang/en_us.json");
        JsonObject polish = read("/assets/no_more_rat/lang/pl_pl.json");
        assertEquals(english.keySet(), polish.keySet());
        for (String key : english.keySet()) {
            String en = english.get(key).getAsString();
            String pl = polish.get(key).getAsString();
            assertFalse(pl.isBlank(), key);
            assertEquals(en.split("%s", -1).length, pl.split("%s", -1).length, key);
        }
    }

    private JsonObject read(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
