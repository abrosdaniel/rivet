package dev.abros.rivet.server;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in two-client integration test. Control files coordinate phases, never supply map responses. */
@EventBusSubscriber(modid="rivet",value=Dist.DEDICATED_SERVER)
public final class MapNetworkServerHarness {
 private static final String DIRECTORY=System.getenv("RIVET_MAP_NETWORK");private static int stage=-1,ticks;private static long deadline;private static boolean done;private static CommunityStore store;private static CommunityStore.Actor actor,viewer;private static String group="";
 private static JsonObject command(String op){var q=new JsonObject();q.addProperty("action","community");q.addProperty("section","groups");q.addProperty("op",op);if(!group.isEmpty())q.addProperty("id",group);return q;}
 private static void move(ServerPlayer p,net.minecraft.resources.ResourceKey<Level> dim,double x){p.setGameMode(GameType.CREATIVE);p.teleportTo(p.server.getLevel(dim),x,200,0,Set.of(),0,0);p.getAbilities().flying=true;p.onUpdateAbilities();}
 private static void membership(boolean join)throws Exception{if(join){var q=command("invite");q.addProperty("target",viewer.id());store.request(actor,q);q=command("invitation");q.addProperty("accept",true);store.request(viewer,q);}else store.request(viewer,command("leave"));}
 private static void phase(ServerPlayer a,ServerPlayer v)throws Exception{
  boolean visible=true;String mode="full",audience="all";String label="full distant";
  a.setInvisible(false);move(v,Level.OVERWORLD,0);move(a,Level.OVERWORLD,1024);
  switch(stage){case 1->{move(a,Level.OVERWORLD,1040);label="movement";}case 2->{move(a,Level.NETHER,40);label="full other dimension";}case 3->{move(a,Level.NETHER,40);mode="nearby";visible=false;label="nearby other dimension";}case 4->{move(a,Level.OVERWORLD,127);mode="nearby";label="nearby inside";}case 5->{move(a,Level.OVERWORLD,129);mode="nearby";visible=false;label="nearby outside";}case 6->{mode="hidden";visible=false;label="hidden";}case 7->{audience="none";visible=false;label="nobody";}case 8->{audience="groups";visible=false;label="groups outsider";}case 9->{audience="groups";membership(true);label="groups member";}case 10->{audience="groups";membership(false);visible=false;label="groups leave";}case 11->{a.setInvisible(true);visible=false;label="invisible";}case 12->{a.setGameMode(GameType.SPECTATOR);visible=false;label="spectator";}case 13->{label="route and skin";}case 14->{mode="hidden";visible=false;label="revoke route";}default->{}}
  ServerMapPlayers.changed(a.server);var q=new JsonObject();q.addProperty("stage",stage);q.addProperty("label",label);q.addProperty("peer",a.getUUID().toString());q.addProperty("visible",visible);q.addProperty("mode",mode);q.addProperty("audience",audience);q.addProperty("dimension",a.level().dimension().location().toString());q.addProperty("x",a.getBlockX());q.addProperty("y",200);q.addProperty("z",0);Json.write(Path.of(DIRECTORY,"phase.json"),q);deadline=System.currentTimeMillis()+45000;System.out.println("RIVET_MAP_NETWORK_PHASE "+stage+" "+label);
 }
 private static void cleanup()throws Exception{if(store==null)return;store.deleteRecord("map-position",actor.id());store.deleteRecord("map-position",viewer.id());if(!group.isEmpty())store.request(actor,command("delete"));}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(DIRECTORY==null||done||++ticks%10!=0)return;try{var server=event.getServer();var a=server.getPlayerList().getPlayerByName("RivetPeerTest");var v=server.getPlayerList().getPlayerByName("RivetViewTest");if(a==null||v==null)return;if(!ServerIntegration.supports(a,"map-positions")||!ServerIntegration.supports(v,"map-positions"))return;
  if(stage<0){store=new CommunityStore(ServerDatabase.get(),CommunityStore.defaults());actor=new CommunityStore.Actor(a.getUUID().toString(),a.getGameProfile().getName(),false,false);viewer=new CommunityStore.Actor(v.getUUID().toString(),v.getGameProfile().getName(),false,false);var q=command("create");q.addProperty("title","Network map test "+UUID.randomUUID().toString().substring(0,8));q.addProperty("description","Temporary integration test");q.addProperty("type","Команда");group=Json.str(store.request(actor,q).getAsJsonObject("detail"),"id");stage=0;phase(a,v);return;}
  var ack=Path.of(DIRECTORY,"viewer.json");if(Files.exists(ack)){var result=Json.read(ack);if(result.get("stage").getAsInt()==stage){if(!result.get("ok").getAsBoolean())throw new IllegalStateException(Json.str(result,"error"));if(++stage==15){cleanup();done=true;Files.writeString(Path.of(DIRECTORY,"done"),"OK");System.out.println("RIVET_MAP_NETWORK_SERVER_OK phases=15");return;}phase(a,v);return;}}
  if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Client timeout stage="+stage);
 }catch(Throwable ex){done=true;System.out.println("RIVET_MAP_NETWORK_SERVER_FAILED stage="+stage);ex.printStackTrace();try{cleanup();Files.writeString(Path.of(DIRECTORY,"failed"),ex.toString());}catch(Exception ignored){}}}
}
