package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RivetWorldMapHarness {
 private static int stage;private static long deadline,next;private static boolean attached;private static net.minecraft.client.gui.screens.Screen parent;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_WORLD_MAP_REVIEW")==null||stage==3)return;
  var mc=Minecraft.getInstance();if(mc.player==null||!SocialClient.available())return;long now=System.currentTimeMillis();if(deadline==0)deadline=now+60000;
  try{
   if(stage==0){if(now<next)return;next=now+1000;parent=mc.screen;var type=Class.forName("xaero.map.gui.GuiMap");var follow=type.getDeclaredField("attachedCamera");follow.setAccessible(true);attached=follow.getBoolean(null);
    var point=new dev.abros.rivet.core.CommunityLocation("Adapter test",mc.level.dimension().location().toString(),mc.player.getBlockX()+16,mc.player.getBlockY(),mc.player.getBlockZ()+16,false);
    try{ClientCompatibilityRegistry.openWorldMap(parent,point);}catch(IllegalArgumentException pending){if(now<deadline)return;throw pending;}
    if(!type.isInstance(mc.screen))throw new AssertionError("World Map screen not opened");for(String coordinate:new String[]{"cameraX","cameraZ"}){var f=type.getDeclaredField(coordinate);f.setAccessible(true);double value=f.getDouble(mc.screen),wanted=(coordinate.equals("cameraX")?point.x():point.z())+0.5;if(value!=wanted)throw new AssertionError("Wrong map camera "+coordinate+": "+value);}
    stage=1;next=now+500;return;
   }
   if(stage==1&&now>=next){mc.setScreen(parent);stage=2;return;}
   if(stage==2){ClientCompatibilityRegistry.tickWorldMap();var follow=Class.forName("xaero.map.gui.GuiMap").getDeclaredField("attachedCamera");follow.setAccessible(true);if(follow.getBoolean(null)!=attached)throw new AssertionError("Camera follow setting not restored");stage=3;System.out.println("RIVET_WORLD_MAP_REVIEW_OK open/camera/restore");}
  }catch(Throwable failure){stage=3;System.out.println("RIVET_WORLD_MAP_REVIEW_FAILED");failure.printStackTrace();}
 }
}
