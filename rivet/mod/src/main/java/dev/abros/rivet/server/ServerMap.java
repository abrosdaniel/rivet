package dev.abros.rivet.server;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.map.MapRepository;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.UUID;

/** World identity belongs to the save, not the server address. */
public final class ServerMap {
    private static volatile UUID world;
    static void start(MinecraftServer server)throws java.io.IOException { world=MapRepository.worldId(server.getWorldPath(LevelResource.ROOT)); }
    static void stop() { world=null; }
    public static boolean radarAllowed(){return ServerDatabase.settings().flag("map.radar");}
    public static boolean cavesAllowed(){return ServerDatabase.settings().flag("map.caves");}
    public static UUID worldId() { return world; }
    static JsonObject policy() {
        var j=new JsonObject();j.addProperty("enabled",ServerDatabase.settings().flag("map.enabled"));
        j.addProperty("positions",ServerDatabase.settings().flag("map.positions"));j.addProperty("nearbyRadius",ServerDatabase.settings().number("map.nearbyRadius"));
        j.addProperty("radar",ServerDatabase.settings().flag("map.radar"));
        j.addProperty("caves",ServerDatabase.settings().flag("map.caves"));
        if(world!=null)j.addProperty("world",world.toString());
        return j;
    }
}
