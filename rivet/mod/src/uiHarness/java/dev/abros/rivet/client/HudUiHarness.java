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
 private static final java.util.List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(6,0,5);
 private static final int TOTAL=FRAMES.size();
 private static int step=-1;private static long at;private static boolean previewStarted;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){if(System.getenv("RIVET_HUD_PREVIEW")!=null){var client=Minecraft.getInstance();if(!previewStarted&&client.screen instanceof TitleScreen){previewStarted=true;var title=client.screen;CommunityUiHarness.open("normal");ServerMenuClient.state.add("profile",Json.GSON.toJsonTree(java.util.Map.of("prefix","<#B7C4CB>Инженер","suffix","<#B7C4CB>Мастер механизмов")));ServerMenuClient.state.addProperty("sessionSeconds",4920);var settings=new HudSettingsScreen(title);client.setScreen(settings);client.setScreen(new HudInteractionScreen(true,settings));System.out.println("RIVET_HUD_PREVIEW_READY: demonstration data");}if(previewStarted&&client.screen instanceof HudInteractionScreen&&System.currentTimeMillis()>at){at=System.currentTimeMillis()+12000;RivetHud.QUEUE.add(new HudNoticeQueue.Notice("","events","demo","reminder","Предпросмотр уведомления","Событие начнётся через пять минут.",HudNoticeQueue.Priority.ORDINARY),net.minecraft.Util.getMillis(),2);}return;}String output=System.getenv("RIVET_HUD_UI");if(output==null||step>TOTAL)return;var mc=Minecraft.getInstance();if(step<0){if(!(mc.screen instanceof TitleScreen))return;CommunityUiHarness.open("normal");ServerMenuClient.state.add("profile",Json.GSON.toJsonTree(java.util.Map.of("prefix","<#B7C4CB>Инженер","suffix","<#B7C4CB>Мастер механизмов")));ServerMenuClient.state.addProperty("sessionSeconds",4920);new java.io.File(output).mkdirs();step=0;at=System.currentTimeMillis()+500;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+220;
 try{if(step>0){UiGeometryHarness.verify(mc.screen);if(mc.screen instanceof HudInteractionScreen){if(HudRenderer.lastX<0||HudRenderer.lastY<0||HudRenderer.lastX+HudRenderer.lastW>mc.screen.width||HudRenderer.lastY+HudRenderer.lastH>mc.screen.height)throw new IllegalStateException("HUD outside viewport");}UiCaptureHarness.grab(new java.io.File(output),String.format("hud-%03d.png",step),mc.getMainRenderTarget(),m->{});}
 if(step>0&&step%36==0)System.out.println("RIVET_HUD_UI_PROGRESS: "+step+"/"+TOTAL);if(step==TOTAL){step++;System.out.println("RIVET_HUD_UI_OK: frames="+TOTAL+", all themes sampled; default/light/high contrast flows, three scales");mc.stop();return;}
 var frame=FRAMES.get(step);frame.apply();if(frame.scene()<5){var settings=new HudSettingsScreen(null);mc.setScreen(settings);settings.previewTab(frame.scene());}else{RivetHud.QUEUE.clear();RivetHud.QUEUE.add(new HudNoticeQueue.Notice("1","events","a","reminder","Событие начинается","Сбор у станции. Приготовьте инструменты.",HudNoticeQueue.Priority.ORDINARY),net.minecraft.Util.getMillis()-200,2);RivetHud.QUEUE.add(new HudNoticeQueue.Notice("2","server","","server","Перезапуск сервера","Сервер сохранит мир через одну минуту.",HudNoticeQueue.Priority.URGENT),net.minecraft.Util.getMillis()-200,2);mc.setScreen(new HudInteractionScreen(true,null));}step++;
 }catch(Throwable failure){System.out.println("RIVET_HUD_UI_FAILED step="+step);failure.printStackTrace();step=TOTAL+1;mc.stop();}}
}
