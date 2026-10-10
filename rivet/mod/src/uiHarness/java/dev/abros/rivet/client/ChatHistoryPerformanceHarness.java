package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in local replay benchmark; synthetic messages never reach the server or disk history. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ChatHistoryPerformanceHarness {
 private static final String ADDRESS=System.getenv("RIVET_CHAT_PERF_ADDRESS"),OUTPUT=System.getenv("RIVET_CHAT_PERF_OUTPUT");
 private static int stage,ticks;private static long deadline;private static final com.google.gson.JsonArray results=new com.google.gson.JsonArray();
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||stage==4)return;var mc=Minecraft.getInstance();
  try{
   if(stage==0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Chat benchmark",ADDRESS,ServerData.Type.OTHER),false,null);stage=1;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Chat benchmark timeout");
   if(mc.player==null||!ServerMenuClient.supports("chat-history")||!SocialSettings.chatEnabled()||++ticks<100)return;
   ticks=0;var chat=(dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat();var saved=new ArrayList<>(chat.rivet$messages());int previous=SocialSettings.INSTANCE.history;
   try{
    SocialSettings.INSTANCE.history=1000;chat.rivet$messages().clear();ClientChat.reset();chat.rivet$refresh();
    long[] samples=new long[1000];long begin=System.nanoTime();
    for(int i=0;i<1000;i++){
     var packet=new JsonObject();packet.addProperty("kind","chatHistory");packet.addProperty("id",1000000L+i);
     var message=new JsonObject();message.addProperty("text","Player "+i+": Representative chat history message with enough text to wrap across multiple lines. ".repeat(2));packet.add("message",message);
     long start=System.nanoTime();if(!ClientChat.receive(packet))throw new IllegalStateException("History rejected");samples[i]=System.nanoTime()-start;if((i+1)%4==0)ClientChat.flushHistory();
    }
    long receive=System.nanoTime()-begin;long refreshStart=System.nanoTime();chat.rivet$refresh();long refresh=System.nanoTime()-refreshStart;
    if(chat.rivet$messages().size()!=1000)throw new IllegalStateException("History limit or replay lost messages");
    Arrays.sort(samples);var result=new JsonObject();result.addProperty("receiveMillis",receive/1e6);result.addProperty("finalRefreshMillis",refresh/1e6);result.addProperty("packetP95Millis",samples[950]/1e6);results.add(result);
    System.out.println("RIVET_CHAT_PERF_SAMPLE "+result);
   }finally{SocialSettings.INSTANCE.history=previous;chat.rivet$messages().clear();chat.rivet$messages().addAll(saved);ClientChat.reset();chat.rivet$refresh();}
   if(++stage==4){var result=new JsonObject();result.addProperty("messages",1000);result.addProperty("synthetic",true);result.addProperty("batchSize",4);result.add("samples",results);Json.write(Path.of(OUTPUT),result);System.out.println("RIVET_CHAT_PERF_OK samples=3 messages=1000");mc.stop();}
  }catch(Throwable failure){stage=4;System.out.println("RIVET_CHAT_PERF_FAILED");failure.printStackTrace();mc.stop();}
 }
}
