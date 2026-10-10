package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.abros.rivet.core.map.MinimapProjection;
/** Real frame interpolation and cached terrain during a controlled walk/run/stop. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MinimapMotionHarness {
 private static int stage=-1,ticks,frames,subframes,lastTick=-1;private static double lastX,fastZoom,baseZoom;private static boolean done;private static long deadline;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_MINIMAP_MOTION_TEST_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{if(stage==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+90000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Motion test",address,ServerData.Type.OTHER),false,null);stage=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Motion test timed out");if(!WorldMapClient.ready())return;
   if(stage==0){MapSettings.INSTANCE.reset();baseZoom=MapSettings.INSTANCE.zoom;fastZoom=baseZoom;mc.options.guiScale().set(2);mc.resizeDisplay();mc.player.connection.sendCommand("gamemode creative");mc.player.getAbilities().flying=true;mc.player.setPos(14,90,-35);mc.setScreen(new HudInteractionScreen(false,null));stage=1;ticks=0;return;}
   ticks++;if(stage==1||stage==2){mc.player.getAbilities().flying=true;mc.player.setPos(mc.player.getX()+(stage==1?.2:.35),90,mc.player.getZ());}
   if(ticks%60==0){if(stage==2){fastZoom=projection().zoom();if(fastZoom>=baseZoom*.975||fastZoom<baseZoom/1.3)throw new IllegalStateException("Speed zoom outside bounds: "+fastZoom);}if(stage==3){if(projection().zoom()<=fastZoom||Math.abs(projection().zoom()-baseZoom)>.05)throw new IllegalStateException("Zoom did not recover");if(frames<60||subframes<5)throw new IllegalStateException("No interpolated frames: "+frames+" / "+subframes);System.out.println("RIVET_MINIMAP_MOTION_OK frames="+frames+" interpolated="+subframes+" speedZoom="+fastZoom);done=true;mc.stop();}else stage++;}
  }catch(Throwable failure){System.out.println("RIVET_MINIMAP_MOTION_FAILED");failure.printStackTrace();done=true;mc.stop();}
 }
 private static MinimapProjection projection()throws Exception{var field=Minimap.class.getDeclaredField("projection");field.setAccessible(true);return (MinimapProjection)field.get(null);}
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){if(System.getenv("RIVET_MINIMAP_MOTION_TEST_ADDRESS")==null||done||stage<1)return;try{var p=projection();if(p==null)return;frames++;if(lastTick==ticks&&Math.abs(lastX-p.x())>.000001)subframes++;lastX=p.x();lastTick=ticks;}catch(Exception e){throw new RuntimeException(e);}}
}
