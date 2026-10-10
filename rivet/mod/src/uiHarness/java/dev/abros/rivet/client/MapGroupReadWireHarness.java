package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.function.Consumer;

/** Real read-only traffic: opening a group card must not make map reads invalidate themselves. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapGroupReadWireHarness {
 private static final String ADDRESS=System.getenv("RIVET_GROUP_READ_WIRE");
 private static boolean connected,done;private static long deadline,started;private static int phase=-1,replies,changes;private static Consumer<JsonObject> previous;private static String group="";private static java.util.Set<java.util.UUID> territories;private static java.lang.reflect.Field expiry;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
   if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+90000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Group read regression",ADDRESS,ServerData.Type.OTHER),false,null);}
   if(!connected)return;long now=System.currentTimeMillis();if(now>deadline)throw new IllegalStateException("Wire regression timeout");
   if(!WorldMapClient.ready()||!MapGroupClient.available())return;
   if(previous==null){
    expiry=MapGroupClient.class.getDeclaredField("expires");expiry.setAccessible(true);if(expiry.getLong(null)<=now)return;
    territories=MapGroupClient.territories().stream().map(t->t.id()).collect(java.util.stream.Collectors.toSet());
    group=MapGroupClient.groups().stream().map(MapGroupClient.Group::id).findFirst().orElse("");
    previous=Protocol.featureState;Protocol.featureState=j->{if(Json.opt(j,"kind","").equals("changed")&&(!j.has("resync")||!j.get("resync").getAsBoolean())&&java.util.Set.of("","groups").contains(Json.opt(j,"section","")))changes++;if(j.has("territories"))replies++;previous.accept(j);};started=now;
    System.out.println("RIVET_GROUP_READ_WIRE_BEGIN card="+!group.isEmpty()+" territories="+territories.size());
   }
   if(changes>0)throw new IllegalStateException("Read-only group browsing produced "+changes+" map invalidations after "+replies+" territory replies");
   if(expiry.getLong(null)<=now||!MapGroupClient.territories().stream().map(t->t.id()).collect(java.util.stream.Collectors.toSet()).containsAll(territories))throw new IllegalStateException("Territory snapshot disappeared during read-only browsing");
   int next=(int)((now-started)/6000);
   if(next>=4){if(replies<2)throw new IllegalStateException("Not enough real territory replies");finish();System.out.println("RIVET_GROUP_READ_WIRE_OK replies="+replies+" invalidations="+changes+" phases=4");return;}
   if(phase!=next){phase=next;mc.setScreen(phase%2==0?new CommunityScreen(null,"groups",group):new WorldMapScreen(null));}
  }catch(Throwable failure){System.out.println("RIVET_GROUP_READ_WIRE_FAILED");failure.printStackTrace();finish();}
 }
 private static void finish(){done=true;if(previous!=null)Protocol.featureState=previous;Minecraft.getInstance().stop();}
}
