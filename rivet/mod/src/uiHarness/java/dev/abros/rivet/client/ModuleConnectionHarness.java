package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real-server chat fixture followed by connection-owned resource cleanup. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ModuleConnectionHarness {
 private static int stage,ticks;private static long deadline;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_MODULE_CHAT_SMOKE")==null)return;
  var mc=Minecraft.getInstance();if(deadline==0)deadline=System.currentTimeMillis()+120000;
  try{
   if(stage==0&&RivetChatIdentityHarness.complete()){
    if(mc.level!=null)mc.level.disconnect();mc.disconnect();stage=1;
   }else if(stage==1&&++ticks>=3){
    check(SocialClient.players.isEmpty()&&SocialClient.chatGroups.isEmpty(),"Player metadata survived disconnect");
    check(!DirectionCue.hasTarget()&&DirectionCue.mapRows().isEmpty(),"Navigation survived disconnect");
    var history=ClientChat.class.getDeclaredField("lastSystem");history.setAccessible(true);
    check(history.get(null).equals(""),"Chat repeat history survived disconnect");
    check(!SkinClient.busy&&SkinClient.library.isEmpty(),"Skin request survived disconnect");
    System.out.println("RIVET_MODULE_CONNECTION_OK chat + player identity + coordinate navigation + disconnect cleanup");stage=2;mc.stop();
   }
   if(stage<2&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Chat fixture timed out");
  }catch(Throwable failure){System.out.println("RIVET_MODULE_CONNECTION_FAILED");failure.printStackTrace();stage=2;mc.stop();}
 }
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
