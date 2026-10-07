package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.Protocol;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Shared server-owned identities, presence and memberships. Clients cannot supply recipient UUIDs. */
final class ServerSocial {
 private record Presence(net.minecraft.world.phys.Vec3 position,float yaw,long active){}
 private record Membership(long until,Map<String,String> groups){}
 private record Metadata(long until,JsonObject value,String context){}
 private static final Map<UUID,Metadata> metadata=new HashMap<>();
 static JsonObject metadata(ServerPlayer p){long now=System.currentTimeMillis();var saved=metadata.get(p.getUUID());if(saved!=null&&saved.until>now&&saved.context.equals(p.level().dimension().location().toString()+":"+LuckPermsAdapter.revision()))return saved.value;var row=ServerIntegration.luckPermsEnabled()?LuckPermsAdapter.profile(p):new JsonObject();metadata.put(p.getUUID(),new Metadata(now+2000,row,p.level().dimension().location().toString()+":"+LuckPermsAdapter.revision()));return row;}
 private static final Map<UUID,Presence> presence=new HashMap<>();
 private static final Map<UUID,Membership> groups=new HashMap<>();
 private static final Set<UUID> loading=new HashSet<>();
 private static final Map<String,Long> rates=new HashMap<>();
 private static long sample;private static Object generation=new Object();
 static void install(){
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{UUID id=e.getEntity().getUUID();presence.remove(id);metadata.remove(id);groups.remove(id);loading.remove(id);rates.keySet().removeIf(k->k.startsWith(id.toString()));});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{long now=System.currentTimeMillis();if(now<sample)return;sample=now+1000;for(var p:e.getServer().getPlayerList().getPlayers()){var old=presence.get(p.getUUID());boolean moved=old==null||old.position.distanceToSqr(p.position())>.01||old.yaw!=p.getYRot();presence.put(p.getUUID(),new Presence(p.position(),p.getYRot(),moved?now:old.active));}});
 }
 static void start(){stop();sample=0;}
 static void stop(){generation=new Object();presence.clear();metadata.clear();groups.clear();loading.clear();rates.clear();}
 static Object generation(){return generation;}
 static void active(ServerPlayer p){presence.put(p.getUUID(),new Presence(p.position(),p.getYRot(),System.currentTimeMillis()));}
 static void suggestGroups(ServerPlayer p,com.mojang.brigadier.suggestion.SuggestionsBuilder builder){memberships(p);var own=groups.get(p.getUUID());if(own!=null)own.groups.keySet().forEach(builder::suggest);}
 private static void memberships(ServerPlayer p){long now=System.currentTimeMillis();UUID id=p.getUUID();if(groups.containsKey(id)&&groups.get(id).until>now||!loading.add(id))return;var store=ServerFeatures.communityStore();if(store==null){loading.remove(id);return;}Object epoch=generation;
  try{ServerFeatures.storage(()->{Map<String,String> result=new LinkedHashMap<>();try{for(var row:store.playerGroups(id.toString()))result.put(Json.str(row,"id"),Json.str(row,"title"));}catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Cannot load chat memberships",error);}var data=Map.copyOf(result);p.server.execute(()->{if(epoch!=generation)return;loading.remove(id);if(p.server.getPlayerList().getPlayer(id)==p)groups.put(id,new Membership(System.currentTimeMillis()+15000,data));});});}catch(java.util.concurrent.RejectedExecutionException full){loading.remove(id);}
 }
 private static boolean rate(ServerPlayer p,String op,long interval){String key=p.getUUID()+":"+op;long now=System.currentTimeMillis();if(now-rates.getOrDefault(key,0L)<interval)return false;rates.put(key,now);return true;}
 static boolean handle(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext context){if(!Json.opt(j,"action","").equals("social"))return false;context.enqueueWork(()->{
  if(!(context.player() instanceof ServerPlayer p)||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"social-display")||!rate(p,"social",250))return;
  int page=j.has("page")?Math.max(0,Math.min(100,j.get("page").getAsInt())):0;var players=p.server.getPlayerList().getPlayers().stream().filter(AuthServer::authenticated).sorted(Comparator.comparing(v->v.getUUID().toString())).toList();var array=new JsonArray();for(int i=page*32;i<Math.min(players.size(),page*32+32);i++){var target=players.get(i);memberships(target);var row=new JsonObject();row.addProperty("id",target.getUUID().toString());row.addProperty("name",target.getGameProfile().getName());var lp=metadata(target);for(String k:List.of("prefix","suffix","primaryGroup")){String v=Json.opt(lp,k,"");row.addProperty(k,v.substring(0,Math.min(80,v.length())));}var state=presence.get(target.getUUID());row.addProperty("status",state==null?"":System.currentTimeMillis()-state.active>=300000?"Отошёл":"");var membership=groups.get(target.getUUID());row.addProperty("group",membership==null?"":membership.groups.values().stream().findFirst().orElse(""));array.add(row);}
  var out=new JsonObject();out.addProperty("kind","social");out.addProperty("request",Json.opt(j,"request",""));out.addProperty("page",page);out.addProperty("more",(page+1)*32<players.size());out.add("players",array);memberships(p);var own=groups.get(p.getUUID());var chatGroups=new JsonArray();if(ServerDatabase.settings().flag("chat.enabled")&&ServerDatabase.settings().flag("chat.group")&&own!=null)own.groups.values().stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(chatGroups::add);out.add("chatGroups",chatGroups);out.addProperty("chatGroupsReady",own!=null);PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(out.toString()));
 });return true;}

}
