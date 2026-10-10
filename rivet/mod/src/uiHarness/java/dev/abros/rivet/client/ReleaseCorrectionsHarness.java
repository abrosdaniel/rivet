package dev.abros.rivet.client;
import dev.abros.rivet.core.pack.PackManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Real component selection, profile application and scrolling at supported UI scales. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ReleaseCorrectionsHarness {
 private static List<VisualMatrixHarness.Frame> frames;private static int frame,checks;private static long next;private static boolean done;
 private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);checks++;}
 private static Button button(String prefix){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith(prefix)).map(c->(Button)c).findFirst().orElseThrow();}
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){if(System.getenv("RIVET_RELEASE_CORRECTIONS")==null||done)return;var mc=Minecraft.getInstance();try{
  if(frames==null){if(!(mc.screen instanceof TitleScreen))return;frames=VisualMatrixHarness.samples(3,0,2);check(MapSettings.INSTANCE.size==112,"Fresh minimap default");}
  if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+140;
  if(frame>0)UiGeometryHarness.verify(mc.screen);
  if(frame==5){var output=new java.io.File(System.getenv("RIVET_RELEASE_CORRECTIONS"));output.mkdirs();UiCaptureHarness.grab(output,"required-components.png",mc.getMainRenderTarget(),message->{});}
  if(frame==frames.size()){System.out.println("RIVET_RELEASE_CORRECTIONS_OK: frames="+frame+" checks="+checks);done=true;mc.stop();return;}
  var f=frames.get(frame++);f.apply();
  if(f.scene()<2){
   var components=new ArrayList<PackManifest.Component>();for(int n=0;n<16;n++)components.add(new PackManifest.Component("group"+n,"Component "+n,"Several related mods selected together. Description wraps beneath the component name and remains readable at every supported scale.",false));
   components.add(new PackManifest.Component("library","Shared Library","Required library: always installed together with the pack.",false,false));
   var manifest=new PackManifest("1.21.1","neoforge",components,List.of(new PackManifest.Entry("mods/a.jar","0".repeat(64),1,"group0","replace"),new PackManifest.Entry("mods/b.jar","1".repeat(64),1,"group0","replace"),new PackManifest.Entry("mods/library.jar","2".repeat(64),1,"library","replace")));
   var screen=new ServerPackScreen(null,manifest,Set.of(),selection->check(manifest.selected(selection).size()==3,"Whole component selection"));mc.setScreen(screen);
   button("Component 0").onPress();button(Client.text("ui.review_changes_f5459a86")).onPress();
   if(f.scene()==1){screen.revealRow(100);screen.rebuildWidgets();check(screen.firstRow>0,"Component scrolling");check(!button("Shared Library").active,"Required component cannot be disabled");}
   var rows=screen.children().stream().filter(c->c instanceof UiChoiceRow).map(c->(UiChoiceRow)c).toList();check(!rows.isEmpty(),"Visible rows");if(rows.size()>1)check(rows.get(1).getY()-rows.get(0).getY()>=42,"Description space");
  }else{
   for(int p=0;p<3;p++){UiPresentationProfiles.apply(p);check(UiPresentationProfiles.current()==p,"Profile identification "+p);}
   SocialSettings.INSTANCE.heads=false;check(UiPresentationProfiles.current()==-1,"Manual changes must show Custom");
   var screen=new AccessibilityScreen(null);mc.setScreen(screen);screen.revealRow(100);screen.rebuildWidgets();button(Client.text("ui.appearance_preset_9ba15013")).onPress();check(mc.screen instanceof ChoicePopup,"Profile dropdown");
   button(Client.text("ui.compact_320acbbc")).onPress();check(mc.screen instanceof UiConfirmDialog,"First preset must be selectable");button(Client.text("ui.confirm_0467ae4b")).onPress();check(UiPresentationProfiles.current()==0,"First preset applied");
   button(Client.text("ui.appearance_preset_9ba15013")).getMessage().getString();check(button(Client.text("ui.appearance_preset_9ba15013")).getMessage().getString().contains(Client.text("ui.compact_320acbbc")),"Current preset label");
  }
  UiGeometryHarness.verify(mc.screen);
 }catch(Throwable failure){System.out.println("RIVET_RELEASE_CORRECTIONS_FAILED frame="+frame);failure.printStackTrace();done=true;mc.stop();}}
}
