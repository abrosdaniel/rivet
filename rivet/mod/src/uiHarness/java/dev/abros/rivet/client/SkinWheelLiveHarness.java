package dev.abros.rivet.client;

import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Optional isolated preview: real vanilla skin uploads and held-key selection over SkinWire. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class SkinWheelLiveHarness {
 private static int step;private static long deadline,nextUpload;private static boolean down=true;private static String expected;private static QuickSkinsScreen wheel;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(System.getenv("RIVET_SKIN_WHEEL_SEED")==null||step==99)return;var mc=Minecraft.getInstance();if(mc.player==null||!SkinClient.available()||!SkinClient.library.has("entries"))return;try{
  if(deadline==0)deadline=System.currentTimeMillis()+90000;
  if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Skin upload/selection timed out: "+SkinClient.status);
  if(SkinClient.busy)return;
  if(!SkinClient.status.isEmpty())throw new IllegalStateException(SkinClient.status);
  if(step==0){var entries=SkinClient.library.getAsJsonArray("entries");if(entries.size()<Math.min(8,SkinClient.library.get("limit").getAsInt())){if(System.currentTimeMillis()<nextUpload)return;nextUpload=System.currentTimeMillis()+3500;var resources=mc.getResourceManager().listResources("textures/entity/player/wide",r->r.getPath().endsWith(".png")).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();var resource=resources.get(entries.size());try(var in=resource.getValue().open()){SkinClient.upload(resource.getKey().getPath().replace("textures/entity/player/wide/","").replace(".png",""),false,in.readAllBytes());}return;}step=1;}
  if(step==1){if(!mc.isWindowActive())return;wheel=new QuickSkinsScreen(null,()->down);mc.setScreen(wheel);wheel.mouseMoved(wheel.width/2d,wheel.height/2d);down=false;wheel.tick();if(mc.screen!=null)throw new IllegalStateException("Center release did not cancel");down=true;wheel=new QuickSkinsScreen(null,()->down);mc.setScreen(wheel);var ids=new ArrayList<>(SkinWheelFavorites.entries().keySet());int index=ids.indexOf("");if(index<0)throw new IllegalStateException("Missing default skin");expected="";var card=(AbstractWidget)wheel.children().get(index);wheel.mouseMoved(wheel.width/2d+(card.getX()+card.getWidth()/2d-wheel.width/2d)*3,wheel.height/2d+(card.getY()+card.getHeight()/2d-wheel.height/2d)*3);down=false;wheel.tick();if(!SkinClient.busy)throw new IllegalStateException("Release did not send selection");step=2;return;}
  if(step==2){if(mc.screen==wheel||!Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","?").equals(expected))throw new IllegalStateException("Selection not acknowledged");System.out.println("RIVET_SKIN_WHEEL_LIVE_OK: uploads within server limit, center cancellation, held release, real server acknowledgement; F7 ready");step=99;}
 }catch(Throwable error){System.out.println("RIVET_SKIN_WHEEL_LIVE_FAILED step="+step);error.printStackTrace();step=99;}}
}
