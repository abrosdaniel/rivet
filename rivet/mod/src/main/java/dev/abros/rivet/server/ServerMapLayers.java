package dev.abros.rivet.server;

import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.api.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** No DB access or consumer callbacks on ticks; private projections never leave the intended connection. */
final class ServerMapLayers {
 private record Connection(Object identity,MapLayerGameSync sync){}
 private static final Map<UUID,Connection> connections=new HashMap<>();
 private static final Map<UUID,Long> retries=new HashMap<>();
 static void reset(){connections.clear();retries.clear();}
 static void resync(net.minecraft.server.level.ServerPlayer player){long now=System.currentTimeMillis();if(now-retries.getOrDefault(player.getUUID(),0L)<3000)return;retries.put(player.getUUID(),now);connections.remove(player.getUUID());}
 static void tick(MinecraftServer server,MapLayerRegistry registry){
  var live=new HashSet<UUID>();long now=System.currentTimeMillis();
  for(var player:server.getPlayerList().getPlayers()){
   var id=player.getUUID();live.add(id);
   if(!ServerIntegration.supports(player,MapLayerGameSync.FEATURE)||!player.connection.hasChannel(Protocol.FeatureState.TYPE)) {connections.remove(id);continue;}
   if(!registry.available()||!AuthServer.authenticated(player)){
    if(connections.remove(id)!=null){var clear=new com.google.gson.JsonObject();clear.addProperty("kind",MapLayerGameSync.FEATURE);clear.addProperty("clear",true);PacketDistributor.sendToPlayer(player,new Protocol.FeatureState(clear.toString()));}continue;
   }
   var state=connections.get(id);if(state==null||state.identity()!=player.connection){state=new Connection(player.connection,new MapLayerGameSync());connections.put(id,state);}
   var packet=state.sync().next(registry,id,now);if(packet!=null)PacketDistributor.sendToPlayer(player,new Protocol.FeatureState(Json.GSON.toJson(packet)));
  }
  connections.keySet().retainAll(live);retries.keySet().retainAll(live);
 }
 private ServerMapLayers(){}
}
