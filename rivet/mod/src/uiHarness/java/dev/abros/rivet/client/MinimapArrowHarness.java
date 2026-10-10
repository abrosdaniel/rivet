package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in rendering of minimap sizes in each supported UI profile. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MinimapArrowHarness {
 private static java.util.List<VisualMatrixHarness.Frame> frames;private static int frame;private static long next;private static boolean done;
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
  if(System.getenv("RIVET_ARROW_TEST")==null||done)return;var mc=Minecraft.getInstance();try{
   if(frames==null){if(!(mc.screen instanceof TitleScreen))return;frames=VisualMatrixHarness.samples(3,1);}
   if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+180;
   if(frame>0&&frames.get(frame-1).theme()==0&&frames.get(frame-1).scale()==3){var dir=new java.io.File("/tmp/rivet-arrow-review");dir.mkdirs();UiCaptureHarness.grab(dir,"arrow-"+frame+".png",mc.getMainRenderTarget(),m->{});}
   if(frame==frames.size()){System.out.println("RIVET_ARROW_OK frames="+frame);done=true;mc.stop();return;}
   var f=frames.get(frame++);f.apply();MapSettings.INSTANCE.size=new int[]{80,112,192}[f.scene()];mc.setScreen(new Screen(Component.literal("Minimap arrow")){
    public void renderBackground(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,UiKit.surface());}
    public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);Minimap.draw(g,true);MapPlayerArrow.draw(g,width/2,height/2,45);}
   });
  }catch(Throwable error){System.out.println("RIVET_ARROW_FAILED");error.printStackTrace();done=true;mc.stop();}
 }
}
