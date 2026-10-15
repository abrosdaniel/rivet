package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.StatusFeed;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns the API incarnation. World/game state is read exclusively on the server thread. */
public final class ServerApi implements RivetApiProvider {
    private static volatile StatusFeed feed;
    private static volatile dev.abros.rivet.core.api.MapLayerRegistry layers;
    static dev.abros.rivet.core.api.MapLayerRegistry layers(){var value=layers;if(value==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Map layers stopped");return value;}
    private static volatile dev.abros.rivet.core.api.JavaDomains domains;

    public ServerApi() {}

    static RivetApi runtime(){var current=feed;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"API stopped");return current;}
    static void install() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            var mapLayers=layers;if(mapLayers!=null){mapLayers.enabled(ServerDatabase.settings().flag("map.enabled"));ServerMapLayers.tick(event.getServer(),mapLayers);}
            var current = feed;
            if (current != null) current.publish(status(event.getServer()));
        });
    }

    static void start(MinecraftServer server) {
        if (feed != null) throw new IllegalStateException("Rivet API already started");
        ServerApiKeys.start(server);ServerDomainAccess.start(server);
        feed = new StatusFeed(256, Clock.systemUTC(), status(server));
        layers=new dev.abros.rivet.core.api.MapLayerRegistry(ServerDatabase.settings().flag("map.enabled"));
        domains = new dev.abros.rivet.core.api.JavaDomains(ServerDatabase.get(),ServerFeatures.communityStore(),ServerDatabase.settings()).access(ServerDomainAccess::allowed).pack(ServerPack::apiManifest).live(ServerLiveMap.ACCESS).local(ServerDomainAccess::local).layers(layers);
    }

    static void stop() {
        ServerMapLayers.reset();
        var oldLayers=layers;layers=null;if(oldLayers!=null)oldLayers.close();
        var oldDomains=domains;domains=null;if(oldDomains!=null)oldDomains.close();
        ServerApiKeys.stop();ServerDomainAccess.stop();
        var previous = feed;
        feed = null;
        if (previous != null) previous.close();
    }

    private static ServerStatus status(MinecraftServer server) {
        boolean management = ServerDatabase.settings().modules().enabled("server");
        var capabilities=new java.util.HashSet<>(Set.of("server.status","server.status.events","domains","domains.watch"));if(ServerTerrain.available())capabilities.add("map.terrain");if(layers!=null&&layers.available())capabilities.add("map.layers");
        return new ServerStatus(server.getPlayerCount(), server.getMaxPlayers(),
            management && ServerManagement.maintenance,
            management ? Math.max(0, ServerManagement.restartAt) : 0,
            Set.copyOf(capabilities));
    }

    @Override public Optional<RivetApi> connect(String modId) {
        if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}") || !ModList.get().isLoaded(modId))
            throw new ApiException(ApiException.Code.INVALID_ARGUMENT, "Consumer mod is not installed");
        var current=feed;var services=domains;var mapLayers=layers;
        if(current==null||services==null)return Optional.empty();
        return Optional.of(new RivetApi(){
            public StatusSnapshot serverStatus(){return current.serverStatus();}
            public StatusEvents serverStatusEvents(ApiCursor after,int limit){return current.serverStatusEvents(after,limit);}
            public IntegrationApi integrations(){current.serverStatus();return ServerProviders.owner(modId);}
            public MapLayersApi mapLayers(){current.serverStatus();return mapLayers.owner(modId);}
            public TerrainApi terrain(){current.serverStatus();return ServerTerrain.api();}
            public DomainApi domains(java.util.UUID player){return services.connect(modId,player);}
        });
    }
}
