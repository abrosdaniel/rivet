package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.map.MapConnectionPolicy;
import net.minecraft.client.Minecraft;

/** Map network state survives rebuilding or replacing menu views. Game thread only. */
final class ClientMapPolicy {
    private static final MapConnectionPolicy<Object> policy=new MapConnectionPolicy<>();
    static void receive(JsonObject packet){var connection=Minecraft.getInstance().getConnection();policy.connect(connection);policy.accept(connection,packet);}
    static MapConnectionPolicy.State current(){var connection=Minecraft.getInstance().getConnection();policy.connect(connection);return policy.current(connection);}
    private ClientMapPolicy(){}
}
