package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Real restart command/state/cancel plus countdown rendering with ordinary HUD and toasts disabled. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class NotificationHarness {
 private static final String ADDRESS=System.getenv("RIVET_NOTIFICATION_ADDRESS");
 private static final java.util.List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(1,0);
 private static int phase=-1,ticks,frame,checks;private static long deadline,initial;private static boolean done,noticeChecked;private static int baselineUnread;private static long noticeStarted;
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Notification test",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Notification timeout phase="+phase);
   if(mc.player==null||!ServerMenuClient.supports("hud"))return;ticks++;
   if(phase==0){if(ticks<60)return;
    if(!noticeChecked){baselineUnread=ServerMenuClient.state.get("unread").getAsInt();noticeStarted=System.nanoTime();mc.player.connection.sendCommand("notificationFixture");phase=5;return;}mc.player.connection.sendCommand("rivet restart 120 notification-test");phase=1;ticks=0;}
   else if(phase==1){if(RivetHud.restartSeconds()<0)return;initial=RivetHud.restartSeconds();check(initial>100&&initial<=120,"Server deadline not received");HudSettings.INSTANCE.enabled=false;if(ServerMenuClient.enabled(0))ServerMenuClient.toggle(0);phase=2;ticks=0;}
   else if(phase==2){if(ticks<5)return;ticks=0;if(frame>0){check(HudRenderer.renderedRestartSeconds>=0,"Countdown hidden with menu/HUD disabled");var bounds=HudRenderer.noticeBounds;check(bounds.y()>=0&&bounds.bottom()<=mc.getWindow().getGuiScaledHeight(),"Countdown outside viewport");}if(frame<FRAMES.size()){FRAMES.get(frame++).apply();mc.setScreen(new HudSettingsScreen(null));return;}check(RivetHud.restartSeconds()<initial,"Countdown does not tick");ServerMenuClient.toggle(2);phase=3;}
   else if(phase==3){if(ticks<5)return;check(RivetHud.restartSeconds()==-1&&HudRenderer.renderedRestartSeconds==-1,"Restart preference ignored");ServerMenuClient.toggle(2);var cancel=new JsonObject();cancel.addProperty("action","restart");cancel.addProperty("seconds",0);cancel.addProperty("text","");ServerMenuClient.request(cancel);phase=4;ticks=0;}
   else if(phase==5){int unread=ServerMenuClient.state.get("unread").getAsInt();if(unread==baselineUnread)return;check(unread==baselineUnread+1,"Another recipient's notification leaked");System.out.println("RIVET_NOTIFICATION_DELIVERY_MS="+(System.nanoTime()-noticeStarted)/1e6);var read=new JsonObject();read.addProperty("action","community");read.addProperty("section","notifications");read.addProperty("op","read");read.addProperty("id","");ServerMenuClient.request(read);phase=6;}
   else if(phase==6){if(ServerMenuClient.state.get("unread").getAsInt()!=0)return;noticeChecked=true;phase=0;ticks=0;}
   else if(phase==4){if(RivetHud.restartSeconds()!=-1)return;if(ticks<5)return;check(HudRenderer.renderedRestartSeconds==-1,"Cancelled countdown remains visible");System.out.println("RIVET_NOTIFICATIONS_OK checks="+checks+" frames="+frame+" command=true menuCancel=true preferences=true seconds=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_NOTIFICATIONS_FAILED phase="+phase);failure.printStackTrace();mc.stop();}
 }
}
