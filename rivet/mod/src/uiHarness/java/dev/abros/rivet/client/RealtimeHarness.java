package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real packet deltas, row replacement and connection-scoped baseline recovery. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RealtimeHarness {
 private static String address,request,oldFirst,mapRequest;private static JsonObject reply,mapReply;private static int phase=-1,deltas,checks,joins;private static long deadline,next;private static boolean done;
 private static final class View extends Screen implements CommunityScreen.Receiver {
  View(){super(Component.literal("Realtime test"));}
  public void receiveCommunity(JsonObject packet){if(Json.opt(packet,"request","").equals(request))reply=packet;if(Json.opt(packet,"request","").equals(mapRequest))mapReply=packet;}
 }
 private static void check(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException(message);}
 private static void connect(){ConnectScreen.startConnecting(new TitleScreen(),Minecraft.getInstance(),ServerAddress.parseString(address),new ServerData("Realtime test",address,ServerData.Type.OTHER),false,null);}
 private static void read(){reply=null;request=java.util.UUID.randomUUID().toString();var q=new JsonObject();q.addProperty("action","community");q.addProperty("op","list");q.addProperty("section","notifications");q.addProperty("request",request);q.addProperty("page",0);Minecraft.getInstance().setScreen(new View());mapReply=null;mapRequest=java.util.UUID.randomUUID().toString();var map=new JsonObject();map.addProperty("action","community");map.addProperty("section","home");map.addProperty("op","toolsPlaces");map.addProperty("request",mapRequest);ServerMenuClient.requestBackground(map);ServerMenuClient.request(q);next=System.currentTimeMillis()+1000;}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  address=System.getenv("RIVET_REALTIME_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;var handler=Protocol.featureState;Protocol.featureState=packet->{if(packet.has("pageDelta"))deltas++;handler.accept(packet);};connect();phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Realtime timeout phase="+phase);
   if(phase==8){if(System.currentTimeMillis()<next)return;connect();phase=9;return;}
   if(mc.player==null||!ServerMenuClient.supports(MenuPageDelta.FEATURE))return;
   if(phase==0){mc.player.connection.sendCommand("realtimeFixture seed");next=System.currentTimeMillis()+2000;phase=1;return;}
   if(System.currentTimeMillis()<next)return;
   if(phase==1){read();phase=2;return;}
   if(phase==2){if(reply==null||mapReply==null)return;check(!mapReply.has("error"),"Concurrent map read rejected: "+mapReply);check(!reply.has("error"),"Initial snapshot failed: "+reply);check(reply.getAsJsonArray("entries").size()>=5,"Missing fixture notifications");oldFirst=Json.str(reply.getAsJsonArray("entries").get(0).getAsJsonObject(),"id");read();phase=3;return;}
   if(phase==3){if(reply==null)return;check(deltas>0,"Unchanged page was sent without delta");check(!reply.has("pageDelta"),"Delta reached UI without decoding");mc.player.connection.sendCommand("realtimeFixture change");next=System.currentTimeMillis()+2000;phase=4;return;}
   if(phase==4){read();phase=5;return;}
   if(phase==5){if(reply==null||mapReply==null)return;check(!mapReply.has("error"),"Updated concurrent map read rejected: "+mapReply);check(!reply.has("error"),"Changed snapshot failed");check(!oldFirst.equals(Json.str(reply.getAsJsonArray("entries").get(0).getAsJsonObject(),"id")),"Changed row did not reach UI");check(!reply.toString().contains("OTHER_RECIPIENT_SECRET"),"Private notification leaked");check(deltas>=2,"Changed page did not use delta");mc.level.disconnect();mc.disconnect();next=System.currentTimeMillis()+1200;phase=8;return;}
   if(phase==9){deltas=0;read();phase=10;return;}
   if(phase==10){if(reply==null)return;check(!reply.has("error")&&reply.has("entries"),"Rejoin snapshot missing");check(deltas==0,"New connection reused an old baseline");if(++joins<3){mc.level.disconnect();mc.disconnect();next=System.currentTimeMillis()+1000;phase=8;return;}System.out.println("RIVET_REALTIME_OK checks="+checks+" reconnects="+joins+" full=true delta=true privacy=true concurrentMap=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_REALTIME_FAILED phase="+phase);failure.printStackTrace();mc.stop();}
 }
}
