package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.MapPositionPolicy;
import dev.abros.rivet.core.map.MapPositionPage;
import dev.abros.rivet.network.Protocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Game-thread live positions, with a generation fence around asynchronous permission reads. */
final class ServerMapPlayers {
 private static long revision;
 static boolean allowed(){return ServerDatabase.settings().flag("map.enabled")&&ServerDatabase.settings().flag("map.positions");}
 static void changed(MinecraftServer server){revision++;var j=new JsonObject();j.addProperty("kind","mapPositionsChanged");j.addProperty("revision",revision);for(var player:server.getPlayerList().getPlayers())if(AuthServer.authenticated(player)&&ServerIntegration.supports(player,"map-positions"))PacketDistributor.sendToPlayer(player,new Protocol.FeatureState(Json.GSON.toJson(j)));}
 static void prepare(ServerPlayer viewer,JsonObject in){long started=PerformanceMetrics.start();try{prepareSnapshot(viewer,in);}finally{PerformanceMetrics.end("map.positions.prepare",started);}}
 private static void prepareSnapshot(ServerPlayer viewer,JsonObject in){
  in.remove("positionCandidates");in.remove("positionEpoch");in.remove("positionNext");
  boolean compact=ServerIntegration.supports(viewer,MapPositionPage.FEATURE);in.addProperty("positionCompact",compact);int limit=compact?MapPositionPage.SNAPSHOT_LIMIT:MapPositionPage.LEGACY_LIMIT;
  String cursor=compact?"":Json.opt(in,"cursor","");if(!cursor.isEmpty())UUID.fromString(cursor);
  var online=viewer.server.getPlayerList().getPlayers().stream().filter(p->p!=viewer&&AuthServer.authenticated(p)&&!p.isSpectator()&&!p.isInvisibleTo(viewer)).map(p->p.getUUID().toString()).filter(id->id.compareTo(cursor)>0).sorted().limit(limit+1).toList();
  var ids=new JsonArray();online.stream().limit(limit).forEach(ids::add);in.add("positionCandidates",ids);in.addProperty("positionEpoch",revision);in.addProperty("positionNext",online.size()>limit?online.get(limit-1):"");
 }
 static java.util.List<JsonObject> fill(ServerPlayer viewer,JsonObject in,JsonObject out){long started=PerformanceMetrics.start();try{return fillSnapshot(viewer,in,out);}finally{PerformanceMetrics.end("map.positions.fill",started);}}
 private static java.util.List<JsonObject> fillSnapshot(ServerPlayer viewer,JsonObject in,JsonObject out){
  var rows=new JsonArray();out.add("peers",rows);out.addProperty("positionRevision",revision);
  if(!allowed()||in.get("positionEpoch").getAsLong()!=revision){out.remove("positionPolicies");out.addProperty("positionStale",true);return java.util.List.of(out);}
  boolean compact=in.get("positionCompact").getAsBoolean();String next=Json.str(in,"positionNext");
  int radius=ServerDatabase.settings().number("map.nearbyRadius");
  for(var element:out.getAsJsonArray("positionPolicies")){var policy=element.getAsJsonObject();var peer=viewer.server.getPlayerList().getPlayer(UUID.fromString(Json.str(policy,"uuid")));if(peer==null||!AuthServer.authenticated(peer)||peer.isSpectator()||peer.isInvisibleTo(viewer))continue;String dimension=peer.level().dimension().location().toString();if(!MapPositionPolicy.read(policy).distanceAllows(dimension,peer.getX(),peer.getY(),peer.getZ(),viewer.level().dimension().location().toString(),viewer.getX(),viewer.getY(),viewer.getZ(),radius))continue;
   var row=new JsonObject();row.addProperty("uuid",peer.getUUID().toString());row.addProperty("name",peer.getGameProfile().getName());row.addProperty("dimension",dimension);row.addProperty("x",peer.getBlockX());row.addProperty("y",Math.clamp(peer.getBlockY(),-2048,2048));row.addProperty("z",peer.getBlockZ());rows.add(row);
  }out.remove("positionPolicies");if(compact){out.remove("peers");if(!next.isEmpty()){dev.abros.rivet.core.LocalizedText.key("rivet.core.position_snapshot_size_exceeded_086a346f").put(out,"error");return java.util.List.of(out);}return MapPositionPage.split(out,rows);}out.addProperty("nextCursor",next);return java.util.List.of(out);
 }
 private ServerMapPlayers(){}
}
