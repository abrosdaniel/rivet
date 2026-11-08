package dev.abros.rivet.core;

import dev.abros.rivet.core.map.MapConnectionPolicy;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MapConnectionPolicyTest {
    @Test void snapshotsAreIndependentAndOldConnectionsCannotRestorePermissions()throws Exception{
        var policy=new MapConnectionPolicy<Object>();var first=new Object();var second=new Object();
        var packet=Json.parse("{\"features\":[\"world-map\"],\"map\":{\"enabled\":true,\"positions\":true,\"nearbyRadius\":128}}");
        UUID world=UUID.randomUUID();packet.getAsJsonObject("map").addProperty("world",world.toString());
        policy.connect(first);assertTrue(policy.accept(first,packet));
        packet.getAsJsonObject("map").addProperty("enabled",false);
        assertTrue(policy.current(first).enabled());assertEquals(world,policy.current(first).world());
        policy.connect(first);assertTrue(policy.current(first).enabled());
        policy.connect(second);assertFalse(policy.current(second).enabled());
        assertFalse(policy.accept(first,packet));assertFalse(policy.current(first).enabled());
        packet.getAsJsonObject("map").addProperty("enabled",true);assertTrue(policy.accept(second,packet));
        packet.getAsJsonObject("map").addProperty("radar",false);assertTrue(policy.accept(second,packet));assertFalse(policy.current(second).radar());
        packet.getAsJsonObject("map").addProperty("world","bad");assertFalse(policy.accept(second,packet));assertFalse(policy.current(second).enabled());
        policy.connect(null);assertNull(policy.current(null).world());
    }
    @Test void missingCapabilityAndMalformedFlagsFailClosed()throws Exception{
        var policy=new MapConnectionPolicy<Object>();var connection=new Object();policy.connect(connection);
        var packet=Json.parse("{\"features\":[],\"map\":{\"enabled\":true}}");assertFalse(policy.accept(connection,packet));
        packet.getAsJsonArray("features").add("world-map");packet.getAsJsonObject("map").addProperty("world",UUID.randomUUID().toString());
        assertTrue(policy.accept(connection,packet));assertTrue(policy.current(connection).caves());assertFalse(policy.current(connection).positions());
        packet.getAsJsonObject("map").addProperty("enabled","true");assertFalse(policy.accept(connection,packet));assertFalse(policy.current(connection).enabled());
        packet.getAsJsonObject("map").addProperty("enabled",true);packet.getAsJsonObject("map").addProperty("nearbyRadius",4097);assertFalse(policy.accept(connection,packet));
        packet.add("map",new com.google.gson.JsonArray());assertFalse(policy.accept(connection,packet));
        assertFalse(policy.accept(connection,new com.google.gson.JsonObject()));
    }
}
