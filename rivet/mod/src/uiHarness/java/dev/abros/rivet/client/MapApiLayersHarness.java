package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real negotiated transport, private filtering, overlays, shared toggles and owner cleanup. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapApiLayersHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_INTEGRATION_ADDRESS");
 private static int phase=-1,step;private static long deadline,at;private static boolean done;
 private static void check(boolean value,String text){if(!value)throw new IllegalStateException(text);}
 private static final class Overlay extends Screen {
  Overlay(){super(Component.literal("Integration overlays"));}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d){
   g.fill(0,0,width,height,0xff000000);var mc=Minecraft.getInstance();var view=new MapViewport();view.center(mc.player.getX(),mc.player.getZ());String dim=mc.level.dimension().location().toString();
   MapIntegrationLayers.worldAreas(g,dim,view,width,height);
   MapIntegrationLayers.world(g,dim,view,width,height,new BoxIntersectionIndex(),java.util.List.of());
   MapIntegrationLayers.minimap(g,new MinimapProjection(view.x(),view.z(),1,45,true),dim,10,10,100,step%2==0);
  }
  @Override public boolean isPauseScreen(){return false;}
 }
 private static int pixels(int left,int top,int right,int bottom){var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();int count=0;
  try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){for(int y=(int)(top*scale);y<Math.min(image.getHeight(),(int)(bottom*scale));y++)for(int x=(int)(left*scale);x<Math.min(image.getWidth(),(int)(right*scale));x++)if((image.getPixelRGBA(x,y)&0xffffff)!=0)count++;}return count;
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done||phase!=0||!WorldMapClient.ready())return;var mc=Minecraft.getInstance();
  mc.player.connection.sendCommand("tag @s add rivet_layers_fixture");mc.setScreen(new Overlay());phase=7;
 }
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(ADDRESS),new net.minecraft.client.multiplayer.ServerData("Layers",ADDRESS,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Timeout phase="+phase);
   if(!WorldMapClient.ready()||System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+250;
   if(phase==7){if(MapIntegrationLayers.layers().size()!=12)return;check(MapIntegrationLayers.layers().stream().noneMatch(l->l.title().contains("SECRET")),"Private layer leaked");phase=1;}
   if(phase==1){mc.options.guiScale().set(step<3?step+1:3);mc.resizeDisplay();UiPalette.preview(step==3?14:0);var contrast=AccessibilityScreen.class.getDeclaredField("contrast");contrast.setAccessible(true);contrast.setBoolean(null,step==4);mc.setScreen(new Overlay());phase=2;return;}
   if(phase==2){int w=mc.screen.width,h=mc.screen.height;check(pixels(w/2-30,h/2-30,w/2+30,h/2+30)>20,"World overlay missing");check(pixels(10,10,110,110)>20,"Minimap overlay missing");for(var layer:MapIntegrationLayers.layers())MapIntegrationLayers.toggle(layer.id());phase=3;return;}
   if(phase==3){check(pixels(0,0,mc.screen.width,mc.screen.height)==0,"Hidden layers still render");for(var layer:MapIntegrationLayers.layers())MapIntegrationLayers.toggle(layer.id());mc.setScreen(new MapLayersScreen(null));((ScrollScreen)mc.screen).revealRow(100);mc.screen.resize(mc,mc.screen.width,mc.screen.height);phase=4;return;}
   if(phase==4){UiGeometryHarness.verify(mc.screen);var toggle=mc.screen.children().stream().filter(c->c instanceof UiToggle t&&t.getMessage().getString().contains("Integration")).map(c->(UiToggle)c).findFirst().orElseThrow(()->new IllegalStateException("Integration toggles inaccessible"));toggle.onPress();check(MapIntegrationLayers.layers().stream().anyMatch(l->!MapIntegrationLayers.shown(l.id())),"Toggle did not apply");for(var layer:MapIntegrationLayers.layers())if(!MapIntegrationLayers.shown(layer.id()))MapIntegrationLayers.toggle(layer.id());
    if(++step<5){phase=1;return;}MapIntegrationLayers.reset();mc.setScreen(new Overlay());phase=5;return;}
   if(phase==5){if(MapIntegrationLayers.layers().size()!=12)return;mc.player.connection.sendCommand("tag @s add rivet_layers_close");phase=6;return;}
   if(phase==6){if(!MapIntegrationLayers.layers().isEmpty())return;System.out.println("RIVET_MAP_INTEGRATION_OK scales=3 light=true contrast=true world=true minimap=true toggles=true resync=true cleanup=true");done=true;mc.stop();}
  }catch(Throwable error){System.out.println("RIVET_MAP_INTEGRATION_FAILED phase="+phase);error.printStackTrace();done=true;mc.stop();}
 }
}
