package dev.abros.rivet.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Published configuration, independent of the evolving current template. */
class EcosystemUpgradeTest {
    @TempDir Path root;

    @Test void acceptsPublishedConfigurationAndPreservesOwnerChoices() throws Exception {
        String original;
        try (var input = getClass().getResourceAsStream("/fixtures/ecosystem-2.7.0/server.toml")) {
            assertNotNull(input);
            original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        original = original.replace("localRadius = 100", "localRadius = 42 # owner choice")
                .replace("password = \"\"", "password = \"fixture-only-password\"");
        Path file = root.resolve("config/rivet-server.toml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, original);
        var upgraded = ServerSettings.load(root);
        assertEquals("optional",upgraded.text("auth.totp"));
        assertTrue(Files.readString(file).contains("totp = \"optional\""));
        assertEquals(42, upgraded.number("chat.localRadius"));
        assertEquals("fixture-only-password", upgraded.text("database.password"));
        assertTrue(Files.readString(file).contains("# owner choice"));
        for(String id:RetiredFeatures.IDS){
            assertTrue(upgraded.flag(id+".enabled"),"Published value must remain readable: "+id);
            assertFalse(upgraded.modules().enabled(id),"Retired feature must not start: "+id);
            assertTrue(Files.readString(file).contains("["+id+"]"),"Owner's section must stay intact");
            assertFalse(ServerSettings.template().contains("["+id+"]"),"New files must not advertise a retired feature");
        }
        String first = Files.readString(file);
        ServerSettings.load(root);
        assertEquals(first, Files.readString(file), "Upgrade must be idempotent");
        Files.writeString(file, first + "\n[unrelated]\nunknown = true\n");
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(root));
        assertEquals(first + "\n[unrelated]\nunknown = true\n", Files.readString(file));
    }
}
