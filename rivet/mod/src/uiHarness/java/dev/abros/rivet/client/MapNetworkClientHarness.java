package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapNetworkClientHarness {
 private static final String DIRECTORY=System.getenv("RIVET_MAP_NETWORK"),ROLE=System.getenv("RIVET_MAP_ROLE");private static boolean connected,installed,done,ack,skinSent;private static int stage=-1,ticks,stable;private static long since;private static final RequestSession request=new RequestSession();private static JsonObject phase;
 private static void write(String file,boolean ok,String error)throws Exception{var j=new JsonObject();j.addProperty("stage",stage);j.addProperty("ok",ok);j.addProperty("error",error);Json.write(Path.of(DIRECTORY,file),j);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(DIRECTORY==null||ROLE==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25598"),new ServerData("Map network","127.0.0.1:25598",ServerData.Type.OTHER),false,null);}
  if(!MapPositions.available()||++ticks%4!=0)return;
  if(!installed){installed=true;var original=Protocol.featureState;Protocol.featureState=j->{original.accept(j);if(request.receive(j))try{if(j.has("error"))throw new IllegalStateException(j.toString());write("actor.json",true,"");}catch(Exception ex){try{write("viewer.json",false,ex.toString());}catch(Exception ignored){}System.out.println("RIVET_MAP_NETWORK_SAVE_FAILED "+ex);}};mc.setScreen(new WorldMapScreen(null));}
  if(Files.exists(Path.of(DIRECTORY,"done"))){done=true;System.out.println("RIVET_MAP_NETWORK_CLIENT_OK role="+ROLE);mc.stop();return;}if(Files.exists(Path.of(DIRECTORY,"failed")))throw new IllegalStateException(Files.readString(Path.of(DIRECTORY,"failed")));
  if(ROLE.equals("actor")&&!skinSent&&SkinClient.available()&&!SkinClient.busy){try(var image=new com.mojang.blaze3d.platform.NativeImage(64,64,false)){for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setPixelRGBA(x,y,0xff55cc99);SkinClient.upload("Network test",false,image.asByteArray());skinSent=true;}}
  if(!Files.exists(Path.of(DIRECTORY,"phase.json")))return;var next=Json.read(Path.of(DIRECTORY,"phase.json"));if(next.get("stage").getAsInt()!=stage){phase=next;stage=phase.get("stage").getAsInt();since=System.currentTimeMillis();stable=0;ack=false;if(ROLE.equals("actor")){var q=new JsonObject();q.addProperty("action","community");q.addProperty("section","home");q.addProperty("op","mapPositionSave");var policy=new JsonObject();policy.add("mode",phase.get("mode"));policy.add("audience",phase.get("audience"));q.add("settings",policy);ServerMenuClient.request(request.begin(q,true,System.currentTimeMillis()));}return;}
  if(!ROLE.equals("viewer")||ack)return;var actor=Path.of(DIRECTORY,"actor.json");if(!Files.exists(actor)||Json.read(actor).get("stage").getAsInt()!=stage)return;
  String peer=Json.str(phase,"peer");var point=MapPositions.points().stream().filter(p->p.id().equals(peer)).findFirst();boolean visible=phase.get("visible").getAsBoolean();boolean matches=visible?point.isPresent()&&point.get().location().x()==phase.get("x").getAsInt()&&point.get().location().dimension().equals(Json.str(phase,"dimension")):point.isEmpty();
  if(stage==14&&DirectionCue.hasTarget())matches=false;if(stage==13&&!SkinClient.skin(UUID.fromString(peer)).texture().getNamespace().equals("rivet"))matches=false;
  if(matches&&System.currentTimeMillis()-since>2500)stable++;else stable=0;
  if(stable>=6){if(stage==13){MapPositions.navigate(point.orElseThrow());if(!DirectionCue.hasTarget())throw new IllegalStateException("Route missing");var skin=SkinClient.skin(UUID.fromString(peer));if(skin==null||skin.texture()==null)throw new IllegalStateException("Player head texture missing");System.out.println("RIVET_MAP_NETWORK_SKIN "+skin.texture());}for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().toString().equals(peer)&&MapRadar.included(entity))throw new IllegalStateException("Local radar bypassed privacy");write("viewer.json",true,"");ack=true;System.out.println("RIVET_MAP_NETWORK_CHECK "+stage+" "+Json.str(phase,"label"));}
  if(System.currentTimeMillis()-since>35000)throw new IllegalStateException("Position mismatch stage="+stage+" snapshot="+point);
 }catch(Throwable ex){done=true;System.out.println("RIVET_MAP_NETWORK_CLIENT_FAILED role="+ROLE+" stage="+stage);ex.printStackTrace();try{write("viewer.json",false,ex.toString());}catch(Exception ignored){}mc.stop();}}
}
