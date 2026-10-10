package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in two-process check: seed on the local test server, then reconnect and verify real replay. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ChatHistoryWireHarness {
 private static long started;private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String mode=System.getenv("RIVET_CHAT_HISTORY_WIRE");if(mode==null||done)return;var mc=Minecraft.getInstance();
  if(mc.player==null||!ServerMenuClient.supports("chat-history")||!ServerMenuClient.state.has("chatFormat"))return;
  if(started==0){started=System.currentTimeMillis();if(mode.equals("seed")){mc.getConnection().sendChat("!RIVET_HISTORY_GLOBAL_PROBE");mc.getConnection().sendCommand("rivet chat local RIVET_HISTORY_LOCAL_PROBE");}}
  if(System.currentTimeMillis()-started<8000)return;
  try{
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
   for(String probe:java.util.List.of("RIVET_HISTORY_GLOBAL_PROBE","RIVET_HISTORY_LOCAL_PROBE"))if(rows.stream().filter(r->r.content().getString().contains(probe)).count()!=1)throw new IllegalStateException("Expected one message: "+probe);
   if(mode.equals("check")&&rows.stream().filter(r->r.content().getString().contains("RIVET_HISTORY_")).anyMatch(r->r.signature()!=null))throw new IllegalStateException("History must not impersonate signed live chat");
   var field=net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("trimmedMessages");field.setAccessible(true);
   @SuppressWarnings("unchecked") var visible=(java.util.List<net.minecraft.client.GuiMessage.Line>)field.get(mc.gui.getChat());
   var rendered=new StringBuilder();for(var line:visible)line.content().accept((index,style,code)->{rendered.appendCodePoint(code);return true;});
   for(String probe:java.util.List.of("RIVET_HISTORY_GLOBAL_PROBE","RIVET_HISTORY_LOCAL_PROBE"))if(!rendered.toString().contains(probe))throw new IllegalStateException("History was stored but not refreshed for display: "+probe);
   System.out.println("RIVET_CHAT_HISTORY_WIRE_OK "+mode);
  }catch(Throwable ex){System.out.println("RIVET_CHAT_HISTORY_WIRE_FAILED "+mode);ex.printStackTrace();}
  done=true;mc.stop();
 }
}
