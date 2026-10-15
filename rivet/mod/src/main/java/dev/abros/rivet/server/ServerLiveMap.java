package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.*;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Game-thread selection and final visibility check; DB policies run only on API workers. */
final class ServerLiveMap {
 static final dev.abros.rivet.core.api.LiveMapAccess ACCESS=new dev.abros.rivet.core.api.LiveMapAccess(){
  public JsonObject read(UUID viewer,String resource,Map<String,String> query)throws Exception{return ServerLiveMap.read(viewer,resource,query);}
  public Set<String> capabilities(){var result=new HashSet<String>();if(ServerDatabase.settings().flag("map.enabled")){if(ServerMapPlayers.allowed()){result.add("players.presence.read");result.add("map.positions.read");}if(ServerWaystones.available())result.add("map.waystones.read");}return Set.copyOf(result);}
 };
 record Prepared(ServerPlayer viewer,JsonObject input,long revision){}
 static JsonObject read(UUID viewer,String resource,Map<String,String> query)throws Exception{
  String cursor=query.getOrDefault("cursor",""),dimension=query.getOrDefault("dimension","");
  if(!cursor.isEmpty()&&!UUID.fromString(cursor).toString().equals(cursor))throw new ApiException(INVALID_ARGUMENT,"Invalid cursor");
  if(!dimension.isEmpty()&&(dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))throw new ApiException(INVALID_ARGUMENT,"Invalid dimension");
  var server=ServerDomainAccess.current();
  if(resource.equals("map/waystones"))return ServerDomainAccess.await(server.submit(()->{
   var player=viewer(server,viewer);if(!ServerWaystones.available())throw new ApiException(MODULE_DISABLED,"Waystones unavailable");
   var stones=stones(player).stream().filter(s->s.id().toString().compareTo(cursor)>0&&(dimension.isEmpty()||s.dimension().equals(dimension))).sorted(Comparator.comparing(s->s.id().toString())).limit(65).toList();
   var items=new JsonArray();for(var stone:stones.stream().limit(64).toList()){var j=new JsonObject();j.addProperty("uuid",stone.id().toString());j.addProperty("name",stone.name());j.addProperty("dimension",stone.dimension());j.addProperty("x",stone.x());j.addProperty("y",stone.y());j.addProperty("z",stone.z());items.add(j);}return page(items,stones.size()>64?stones.get(63).id().toString():"");
  }));
  var prepared=ServerDomainAccess.await(server.submit(()->prepare(server,viewer,cursor)));
  var policies=ServerFeatures.communityStore().apiPositionPolicies(new CommunityStore.Actor(viewer.toString(),"",false,false),prepared.input());
  return ServerDomainAccess.await(server.submit(()->finish(server,viewer,prepared,policies,resource,cursor,dimension)));
 }
 static Prepared prepare(MinecraftServer server,UUID viewer,String cursor){var player=viewer(server,viewer);if(!ServerMapPlayers.allowed())throw new ApiException(MODULE_DISABLED,"Positions disabled");var in=new JsonObject();in.addProperty("op","mapPositionPeers");in.addProperty("cursor",cursor);ServerMapPlayers.prepareApi(player,in);if(!Json.str(in,"positionNext").isEmpty())throw new ApiException(BUSY,"Position capacity exceeded");return new Prepared(player,in,ServerDatabase.get().communityRevision());}
 static JsonObject finish(MinecraftServer server,UUID viewer,Prepared prepared,JsonObject policies,String resource,String cursor,String dimension){
   if(viewer(server,viewer)!=prepared.viewer())throw new ApiException(FORBIDDEN,"Player session changed");
   if(ServerDatabase.get().communityRevision()!=prepared.revision())throw new ApiException(CONFLICT,"Visibility changed; retry");
   prepared.input().addProperty("positionCompact",false);var value=ServerMapPlayers.fill(prepared.viewer(),prepared.input(),policies).getFirst();
   if(value.has("positionStale"))throw new ApiException(CONFLICT,"Visibility changed; retry");
   var items=new JsonArray();for(var e:value.getAsJsonArray("peers")){var row=e.getAsJsonObject();if(Json.str(row,"uuid").compareTo(cursor)<=0||!dimension.isEmpty()&&!Json.str(row,"dimension").equals(dimension))continue;if(resource.equals("players/online")){var presence=new JsonObject();presence.add("uuid",row.get("uuid"));presence.add("name",row.get("name"));items.add(presence);}else items.add(row);}
   var limited=new JsonArray();for(int i=0;i<Math.min(64,items.size());i++)limited.add(items.get(i));return page(limited,items.size()>64?Json.str(limited.get(63).getAsJsonObject(),"uuid"):"");
 }
 private static java.util.List<dev.abros.rivet.network.WaystonesWire.Stone> stones(ServerPlayer player){try{return ServerWaystones.apiPoints(player);}catch(Exception failure){throw new ApiException(UNAVAILABLE,"Waystones read failed");}}
 private static ServerPlayer viewer(MinecraftServer server,UUID id){if(ServerDomainAccess.current()!=server)throw new ApiException(UNAVAILABLE,"Server changed");if(!ServerDatabase.settings().flag("map.enabled"))throw new ApiException(MODULE_DISABLED,"Map disabled");var player=server.getPlayerList().getPlayer(id);if(player==null||!AuthServer.authenticated(player))throw new ApiException(FORBIDDEN,"Online authenticated player required");return player;}
 private static JsonObject page(JsonArray items,String cursor){var result=new JsonObject();result.add("items",items);result.addProperty("nextCursor",cursor);return result;}
 private ServerLiveMap(){}
}
