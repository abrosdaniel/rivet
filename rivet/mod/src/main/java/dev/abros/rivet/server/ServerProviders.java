package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.IntegrationRegistry;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.server.compat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.*;
import java.util.*;

/** Platform boundary: Minecraft actors never cross the dependency-free Java API. */
public final class ServerProviders {
 private static volatile IntegrationRegistry registry;
 private static IntegrationApi reader;
 public static void start(MinecraftServer server){
  stop();WaystonesAdapter.CAPABILITY.reset();registry=new IntegrationRegistry();reader=registry.owner("rivet");
  var identities=new ServerIdentityDirectory(server);
  owner("rivet").register(IntegrationApi.IDENTITY,"rivet",new IntegrationApi.Identity(){
   public Optional<IntegrationApi.Player> byName(String name){thread(server);return identities.byName(name).map(p->new IntegrationApi.Player(p.getId(),p.getName()));}
   public Optional<IntegrationApi.Player> byId(UUID id){thread(server);return identities.byId(id).map(p->new IntegrationApi.Player(p.getId(),p.getName()));}
   public Optional<UUID> verifiedAlias(UUID id){thread(server);return identities.verifiedAlias(id);}
  });
  owner("waystones").register(IntegrationApi.WAYSTONES,"waystones",new IntegrationApi.Waystones(){
   public boolean available(){return WaystonesAdapter.CAPABILITY.available();}
   public List<DomainData.Waystone> points(UUID id)throws Exception {var player=actor(server,id);if(!ServerDatabase.settings().flag("map.enabled"))throw new ApiException(ApiException.Code.MODULE_DISABLED,"Map disabled");return WaystonesAdapter.apiPoints(player).stream().map(s->new DomainData.Waystone(s.id(),s.name(),s.dimension(),s.x(),s.y(),s.z())).toList();}
  });
  owner("plasmovoice").register(IntegrationApi.VOICE,"plasmovoice",new IntegrationApi.Voice(){
   public boolean available(){return PlasmoVoiceAdapter.available();}
   public void checkMute(UUID id)throws Exception{thread(server);PlasmoVoiceAdapter.checkMute(id);}
   public void mute(UUID id,int minutes,String reason)throws Exception{thread(server);PlasmoVoiceAdapter.mute(id,minutes,reason);}
  });
  owner("voicechat").register(IntegrationApi.VOICE,"voicechat",new IntegrationApi.Voice(){
   public boolean available(){return SimpleVoiceChatAdapter.available();}
   public Optional<Boolean> muted(UUID id){thread(server);return Optional.of(dev.abros.rivet.compat.voice.SimpleVoiceBridge.muted(id));}
   public void checkMute(UUID id){thread(server);SimpleVoiceChatAdapter.checkMute(id);}
   public void mute(UUID id,int minutes,String reason)throws Exception{thread(server);SimpleVoiceChatAdapter.mute(id,minutes);}
  });
  owner("luckperms").register(IntegrationApi.PERMISSIONS,"luckperms",new IntegrationApi.Permissions(){
   public long revision(){return LuckPermsAdapter.revision();}
   public IntegrationApi.Data profile(UUID id,boolean online){return data(online?LuckPermsAdapter.profile(live(server,id)):LuckPermsAdapter.profile(id));}
   public boolean allowed(UUID id,String right){thread(server);return LuckPermsAdapter.allowed(live(server,id),right);}
   public boolean offlineAllowed(UUID id,String right){if(server.isSameThread())throw new IllegalStateException("Offline permissions require a worker");return LuckPermsAdapter.offlineAllowed(id,right);}
   public List<IntegrationApi.Data> roles(){thread(server);var result=new ArrayList<IntegrationApi.Data>();LuckPermsAdapter.roles().forEach(e->result.add(data(e.getAsJsonObject())));return List.copyOf(result);}
  });
 }
 public static void registerAction(MinecraftServer server,CompatibilityAdapter adapter,IntegrationApi.Service<IntegrationApi.Actions> type){
  owner(adapter.id().replace('-','_')).register(type,adapter.id().replace('-','_'),(id,request)->{
   var player=actor(server,id);var input=json(request);
   var operation=(dev.abros.rivet.core.OptionalIntegration.Operation<JsonObject>)()->adapter.execute(player,input,find(IntegrationApi.IDENTITY,"rivet").orElseThrow());
   boolean diagnostic=type.equals(IntegrationApi.DIAGNOSTICS)&&Set.of("snapshot","alerts").contains(Json.opt(input,"op","snapshot"));
   return data(diagnostic?operation.run():adapter.lifecycle().required(operation));
  });
 }
 public static IntegrationApi owner(String id){var current=registry;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Integrations stopped");return current.owner(id);}
 public static <T> Optional<T> find(IntegrationApi.Service<T> service,String id){var current=reader;return current==null?Optional.empty():current.find(service,id);}
 public static void stop(){reader=null;var old=registry;registry=null;if(old!=null)old.close();}
 private static void thread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Server thread required");}
 private static ServerPlayer live(MinecraftServer server,UUID id){thread(server);var player=server.getPlayerList().getPlayer(id);if(player==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Player offline");return player;}
 private static ServerPlayer actor(MinecraftServer server,UUID id){var player=live(server,id);if(!AuthServer.authenticated(player))throw new ApiException(ApiException.Code.FORBIDDEN,"Sign in first");return player;}
 public static IntegrationApi.Data data(JsonObject value){var fields=new LinkedHashMap<String,Object>();value.entrySet().forEach(e->fields.put(e.getKey(),plain(e.getValue())));return new IntegrationApi.Data(fields);}
 private static Object plain(JsonElement value){if(value.isJsonObject()){var result=new LinkedHashMap<String,Object>();value.getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),plain(e.getValue())));return result;}if(value.isJsonArray()){var result=new ArrayList<Object>();value.getAsJsonArray().forEach(e->result.add(plain(e)));return result;}if(value.isJsonNull())return null;var p=value.getAsJsonPrimitive();return p.isBoolean()?p.getAsBoolean():p.isNumber()?p.getAsBigDecimal():p.getAsString();}
 public static JsonObject json(IntegrationApi.Data data){return Json.GSON.toJsonTree(data.fields()).getAsJsonObject();}
 private ServerProviders(){}
}
