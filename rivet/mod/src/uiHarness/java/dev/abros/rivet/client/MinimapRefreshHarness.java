package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Hold the compositor deliberately: visible terrain must survive movement and resize. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MinimapRefreshHarness {
 private static final String ADDRESS=System.getenv("RIVET_MINIMAP_REFRESH_ADDRESS");
 private static int stage=-1,ticks,checks;private static long deadline;private static boolean done;
 private static CountDownLatch entered,release;private static DynamicTexture previous;private static Object area;
 private static int pixel;private static long signature;
 private static Object get(String name)throws Exception{var f=Minimap.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void set(String name,Object value)throws Exception{var f=Minimap.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 private static void hold(){entered=new CountDownLatch(1);release=new CountDownLatch(1);if(!MapTerrainCache.work(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}))throw new IllegalStateException("Cannot hold compositor");}
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(stage==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+60000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Minimap refresh test",ADDRESS,ServerData.Type.OTHER),false,null);stage=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Refresh test timed out at "+stage);
   if(!WorldMapClient.ready())return;
   ticks++;
   if(stage==0){MapSettings.INSTANCE.reset();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new HudInteractionScreen(false,null));stage=1;ticks=0;}
   else if(stage==1){if(ticks<100||get("texture")==null||(boolean)get("composing"))return;previous=(DynamicTexture)get("texture");area=get("displayed");pixel=previous.getPixels().getPixelRGBA(0,0);signature=(long)get("terrainSignature");hold();stage=2;}
   else if(stage==2){if(entered.getCount()!=0)return;set("textureX",(int)get("textureX")+16);set("terrainSignature",Long.MIN_VALUE);var update=Minimap.class.getDeclaredMethod("update");update.setAccessible(true);update.invoke(null);check((boolean)get("composing"),"No replacement queued");stage=3;ticks=0;}
   else if(stage==3){check(get("texture")==previous&&get("displayed")==area,"Moved pixels before replacement was ready");check(previous.getPixels().getPixelRGBA(0,0)==pixel,"Changed cached pixels while waiting");if(ticks>=5){release.countDown();stage=4;}}
   else if(stage==4){if((boolean)get("composing")||get("displayed")==area)return;check(get("displayed")!=area,"Replacement did not publish");check((long)get("terrainSignature")!=Long.MIN_VALUE,"Missing published signature");previous=(DynamicTexture)get("texture");area=get("displayed");pixel=previous.getPixels().getPixelRGBA(0,0);hold();stage=5;}
   else if(stage==5){if(entered.getCount()!=0)return;MapSettings.INSTANCE.zoom=.25f;Minimap.invalidate();stage=6;ticks=0;}
   else if(stage==6){check(get("texture")==previous&&get("displayed")==area,"Resize discarded visible terrain before replacement");if(ticks>=5){release.countDown();stage=7;}}
   else if(stage==7){if((boolean)get("composing")||get("displayed")==area)return;var texture=(DynamicTexture)get("texture");check(texture.getPixels()!=null,"Missing pixels after resize");texture.bind();check(org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10242)==33071&&org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10243)==33071,"Terrain repeats past texture edges");check(org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10240)==9728&&org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10241)==9729,"Sharp magnification / filtered minification was reset by upload");stage=8;ticks=0;signature=(long)get("terrainSignature");}
   else if(stage==8){if(ticks<20)return;check(get("texture")!=null,"Stationary terrain disappeared");System.out.println("RIVET_MINIMAP_REFRESH_OK checks="+checks+" movement=true resize=true clamp=true filtering=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;if(release!=null)release.countDown();System.out.println("RIVET_MINIMAP_REFRESH_FAILED stage="+stage);failure.printStackTrace();mc.stop();}
 }
}
