package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class HudUiHarness {
 private static final java.util.List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(18,0,5,6);
 private static final int TOTAL=FRAMES.size();
 private static int previewWindowWidth,previewWindowHeight,previewScale;
 private static int step=-1;private static long at;private static boolean previewStarted;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){if(System.getenv("RIVET_HUD_PREVIEW")!=null){var client=Minecraft.getInstance();if(!previewStarted&&client.screen instanceof TitleScreen){previewStarted=true;var title=client.screen;CommunityUiHarness.open("normal");ServerMenuClient.state.add("profile",Json.GSON.toJsonTree(java.util.Map.of("prefix","<#B7C4CB>Инженер","suffix","<#B7C4CB>Мастер механизмов")));ServerMenuClient.state.addProperty("sessionSeconds",4920);var settings=new HudSettingsScreen(title);client.setScreen(settings);client.setScreen(new HudInteractionScreen(true,settings));System.out.println("RIVET_HUD_PREVIEW_READY: demonstration data");}if(previewStarted&&client.screen instanceof HudInteractionScreen&&System.currentTimeMillis()>at){at=System.currentTimeMillis()+12000;RivetHud.QUEUE.add(new HudNoticeQueue.Notice("","notifications","demo","summary","Предпросмотр уведомления","Новое сообщение сервера.",HudNoticeQueue.Priority.ORDINARY),net.minecraft.Util.getMillis(),2);}return;}String output=System.getenv("RIVET_HUD_UI");if(output==null||step>TOTAL)return;var mc=Minecraft.getInstance();if(step<0){if(!(mc.screen instanceof TitleScreen))return;CommunityUiHarness.open("normal");ServerMenuClient.state.add("profile",Json.GSON.toJsonTree(java.util.Map.of("prefix","<#B7C4CB>Инженер","suffix","<#B7C4CB>Мастер механизмов")));ServerMenuClient.state.addProperty("sessionSeconds",4920);new java.io.File(output).mkdirs();step=0;at=System.currentTimeMillis()+500;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+220;
 try{if(step==0){verifyExternalNotices();verifyPlacement();}if(step>0){UiGeometryHarness.verify(mc.screen);if(mc.screen instanceof HudPreviewScreen){if(mc.getWindow().getWidth()!=previewWindowWidth||mc.getWindow().getHeight()!=previewWindowHeight||mc.options.guiScale().get()!=previewScale)throw new IllegalStateException("HUD preview changed game window/settings");if(HudRenderer.click(10,10,true))throw new IllegalStateException("Preview actions are interactive");var remaining=RivetHud.QUEUE.advance(net.minecraft.Util.getMillis()+20000,2,true);if(remaining.isEmpty()||remaining.getFirst().remaining(net.minecraft.Util.getMillis()+20000)<.9)throw new IllegalStateException("Preview consumed real notifications");RivetHud.QUEUE.clear();}if(mc.screen instanceof HudInteractionScreen){if(HudRenderer.lastX<0||HudRenderer.lastY<0||HudRenderer.lastX+HudRenderer.lastW>mc.screen.width||HudRenderer.lastY+HudRenderer.lastH>mc.screen.height)throw new IllegalStateException("HUD outside viewport");verifyGaps();}UiCaptureHarness.grab(new java.io.File(output),String.format("hud-%03d.png",step),mc.getMainRenderTarget(),m->{});}
 if(step>0&&step%36==0)System.out.println("RIVET_HUD_UI_PROGRESS: "+step+"/"+TOTAL);if(step==TOTAL){step++;System.out.println("RIVET_HUD_UI_OK: frames="+TOTAL+", all themes sampled; default/light/high contrast flows, three scales");mc.stop();return;}
 var frame=FRAMES.get(step);frame.apply();if(frame.scene()<5){var settings=new HudSettingsScreen(null);mc.setScreen(settings);settings.previewTab(frame.scene());}else if(frame.scene()==5){RivetHud.QUEUE.clear();RivetHud.QUEUE.add(new HudNoticeQueue.Notice("1","notifications","a","reminder","Новое уведомление","Откройте центр уведомлений для подробностей.",HudNoticeQueue.Priority.ORDINARY),net.minecraft.Util.getMillis()-200,2);RivetHud.QUEUE.add(new HudNoticeQueue.Notice("2","server","","server","Перезапуск сервера","Сервер сохранит мир через одну минуту.",HudNoticeQueue.Priority.URGENT),net.minecraft.Util.getMillis()-200,2);mc.setScreen(new HudInteractionScreen(true,null));}else{RivetHud.QUEUE.clear();RivetHud.QUEUE.add(new HudNoticeQueue.Notice("preview-keeps-real","notifications","","summary","Saved","Must survive preview",HudNoticeQueue.Priority.ORDINARY),net.minecraft.Util.getMillis(),2);previewWindowWidth=mc.getWindow().getWidth();previewWindowHeight=mc.getWindow().getHeight();previewScale=mc.options.guiScale().get();mc.setScreen(new HudPreviewScreen(null,(frame.scene()-6)/3,(frame.scene()-6)%3+1));}step++;
 }catch(Throwable failure){System.out.println("RIVET_HUD_UI_FAILED step="+step);failure.printStackTrace();step=TOTAL+1;mc.stop();}}
 private static void verifyPlacement()throws Exception{
  var mc=Minecraft.getInstance();var parent=mc.screen;var settings=HudSettings.INSTANCE;
  int ax=settings.anchorX,ay=settings.anchorY,ox=settings.offsetX,oy=settings.offsetY;float oldScale=settings.scale;
  try{
   var editor=new HudInteractionScreen(true,parent);mc.setScreen(editor);
   var f=HudInteractionScreen.class.getDeclaredField("targets");f.setAccessible(true);
   @SuppressWarnings("unchecked") var targets=(java.util.Map<String,dev.abros.rivet.core.NativeLayout.Box>)f.get(editor);
   var start=new dev.abros.rivet.core.NativeLayout.Box(8,8,30,30);var obstacle=new dev.abros.rivet.core.NativeLayout.Box(80,50,50,50);
   targets.put("widget",start);targets.put("minimap",obstacle);
   editor.mouseClicked(12,12,0);editor.mouseDragged(90,60,0,78,48);
   editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
   var restored=HudPlacement.fit(editor.width,editor.height,30,30,settings.anchorX,settings.anchorY,settings.offsetX,settings.offsetY);
   if(mc.screen!=editor||!restored.equals(start))throw new IllegalStateException("Esc did not cancel HUD drag in place");
   editor.mouseClicked(12,12,0);editor.mouseDragged(90,60,0,78,48);editor.mouseReleased(90,60,0);
   var dropped=HudPlacement.fit(editor.width,editor.height,30,30,settings.anchorX,settings.anchorY,settings.offsetX,settings.offsetY);
   if(dev.abros.rivet.core.HudDropLayout.overlaps(dropped,obstacle,4))throw new IllegalStateException("HUD drop overlaps another element");
   settings.scale=1;targets.clear();targets.put("widget",start);
   editor.mouseScrolled(12,12,0,1);
   if(settings.scale<=1)throw new IllegalStateException("HUD resize did not start");
   targets.put("widget",new dev.abros.rivet.core.NativeLayout.Box(8,8,33,33));
   targets.put("minimap",new dev.abros.rivet.core.NativeLayout.Box(4,4,editor.width-8,editor.height-8));
   editor.finishResize();
   if(settings.scale!=1)throw new IllegalStateException("Crowded HUD resize was not reverted");
   targets.clear();targets.put("widget",start);editor.mouseScrolled(12,12,0,1);
   targets.put("widget",new dev.abros.rivet.core.NativeLayout.Box(8,8,33,33));editor.finishResize();
   if(settings.scale<=1)throw new IllegalStateException("Free HUD resize was reverted");
   float applied=settings.scale;editor.mouseScrolled(12,12,0,1);editor.removed();
   if(settings.scale!=applied)throw new IllegalStateException("Unfinished HUD resize survived closing");
  }finally{mc.setScreen(parent);settings.scale=oldScale;settings.anchorX=ax;settings.anchorY=ay;settings.offsetX=ox;settings.offsetY=oy;settings.save();}
 }
 private static void verifyExternalNotices()throws Exception{
  var pending=RivetHud.class.getDeclaredField("pending");pending.setAccessible(true);
  var sequence=RivetHud.class.getDeclaredField("sequence");sequence.setAccessible(true);
  var enabled=ServerMenuClient.class.getDeclaredField("notices");enabled.setAccessible(true);
  var oldPending=pending.get(null);var oldSequence=sequence.get(null);var oldEnabled=enabled.get(null);
  var snapshot=RivetHud.snapshot;var muted=new java.util.HashSet<>(HudSettings.INSTANCE.muted);
  try{
   enabled.setBoolean(null,true);HudSettings.INSTANCE.muted.clear();
   var packet=Json.parse("{\"kind\":\"hud\",\"request\":\"external-check\",\"sequence\":1,\"more\":false,\"unread\":1,\"preferences\":{\"muted\":[]},\"holograms\":[],\"notices\":[{\"id\":\"1\",\"section\":\"notifications\",\"sourceType\":\"example:mail\",\"event\":\"external\",\"priority\":\"ordinary\",\"title\":\"Mail\",\"body\":\"Delivery\",\"read\":false}]}");
   for(String mode:java.util.List.of("visible","local-source","legacy-category","server-source")){
    RivetHud.QUEUE.clear();HudSettings.INSTANCE.muted.clear();packet.getAsJsonObject("preferences").add("muted",new com.google.gson.JsonArray());
    if(mode.equals("local-source"))HudSettings.INSTANCE.muted.add("example:mail");
    if(mode.equals("legacy-category"))HudSettings.INSTANCE.muted.add("notifications");
    if(mode.equals("server-source"))packet.getAsJsonObject("preferences").getAsJsonArray("muted").add("example:mail");
    pending.set(null,"external-check");sequence.setLong(null,0);if(!RivetHud.receive(packet))throw new IllegalStateException("HUD did not consume reply");
    int count=RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true).size();
    if(count!=(mode.equals("visible")?1:0))throw new IllegalStateException("External notice delivery policy: "+mode);
   }
  }finally{RivetHud.QUEUE.clear();RivetHud.snapshot=snapshot;pending.set(null,oldPending);sequence.set(null,oldSequence);enabled.set(null,oldEnabled);HudSettings.INSTANCE.muted.clear();HudSettings.INSTANCE.muted.addAll(muted);}
 }
 private static void verifyGaps()throws Exception{var count=HudRenderer.class.getDeclaredField("widgetHits");count.setAccessible(true);var field=HudRenderer.class.getDeclaredField("hits");field.setAccessible(true);var hits=(java.util.List<?>)field.get(null);for(int i=1;i<count.getInt(null);i++){var before=(HudRenderer.Hit)hits.get(i-1);var after=(HudRenderer.Hit)hits.get(i);double gap=after.y()-before.y()-before.h();if(gap<=0)throw new IllegalStateException("HUD islands overlap");double x=after.x()+after.w()/2,y=before.y()+before.h()+gap/2;if(HudRenderer.click(x,y,true))throw new IllegalStateException("HUD gap consumes a click");}}
}
