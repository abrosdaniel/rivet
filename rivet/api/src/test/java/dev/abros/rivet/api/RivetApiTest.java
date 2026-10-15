package dev.abros.rivet.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RivetApiTest {
    @Test void optionalDiscoveryDoesNotNeedMinecraftOrRuntime() {
        assertTrue(RivetApi.connect("example_mod").isEmpty());
    }
    @Test void invalidConsumerFailsBeforeDiscovery() {
        for (String value : new String[] {"", "../../x", "MixedCase", "x", "a".repeat(65)})
            assertEquals(ApiException.Code.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> RivetApi.connect(value)).code());
    }
}
