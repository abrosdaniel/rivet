package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.SkinWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Native input, acknowledgement and responsive wheel checks; isolated test client only. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class SkinWheelHarness {
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(3,0);
 private static int step=-1;private static long at;
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static JsonObject library(){var j=new JsonObject();var p=new JsonObject();p.addProperty("active","");p.addProperty("owner",new UUID(0,1).toString());j.add("profile",p);var entries=new JsonArray();for(int i=0;i<9;i++){var e=new JsonObject();e.addProperty("id","skin-"+i);e.addProperty("name",List.of("Строитель","Исследователь","Механик","Путешественник","Фермер","Архитектор","Шахтёр","Пилот","Мастер").get(i));e.addProperty("hash","");e.addProperty("slim",false);entries.add(e);}j.add("entries",entries);return j;}
 private static String pending()throws Exception{var f=SkinClient.class.getDeclaredField("pending");f.setAccessible(true);return (String)f.get(null);}
 private static String expected(QuickSkinsScreen screen)throws Exception{var f=QuickSkinsScreen.class.getDeclaredField("expected");f.setAccessible(true);return (String)f.get(screen);}
 private static void inputs()throws Exception{
  var mc=Minecraft.getInstance();
  for(int n=1;n<=8;n++)for(int i=0;i<n;i++){double a=2*Math.PI*i/n-Math.PI/2;require(QuickSkinsScreen.sector(Math.cos(a)*100,Math.sin(a)*100,n,100)==i,"sector "+n+"/"+i);}
  require(QuickSkinsScreen.sector(0,0,8,100)==-1&&QuickSkinsScreen.sector(2000,0,8,100)==2,"dead zones");
  require(SkinWheelFavorites.entries().size()==8,"default slots");require(SkinWheelFavorites.toggle("skin-8").equals("skins.wheel.full"),"full limit");require(SkinWheelFavorites.toggle("").isEmpty(),"remove");require(SkinWheelFavorites.toggle("skin-8").isEmpty(),"add");require(SkinWheelFavorites.contains("skin-8"),"favorite saved");require(SkinWheelFavorites.toggle("skin-8").isEmpty()&&SkinWheelFavorites.toggle("").isEmpty(),"restore");
  var parent=new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()){};var screen=new QuickSkinsScreen(parent);mc.setScreen(screen);screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(mc.screen==parent,"escape");
  screen=new QuickSkinsScreen(parent);mc.setScreen(screen);screen.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0);screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
  require(SkinClient.busy&&mc.screen==screen,"wait for acknowledgement");String request=pending();require(expected(screen).equals("skin-0"),"selected slot");
  var failure=new JsonObject();failure.addProperty("kind","error");failure.addProperty("request",request);failure.addProperty("text","Test refusal");SkinWire.client.accept(failure);require(mc.screen==screen&&!SkinClient.busy&&!SkinClient.status.isEmpty(),"error remains visible");
  screen.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0);screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);request=pending();var reply=library();reply.addProperty("kind","library");reply.addProperty("request",request);reply.getAsJsonObject("profile").addProperty("active",expected(screen));SkinWire.client.accept(reply);require(mc.screen==parent&&!SkinClient.busy,"close on ack");SkinClient.library=library();SkinClient.status="";
 }
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){String output=System.getenv("RIVET_SKIN_WHEEL_UI");if(output==null||step>FRAMES.size())return;var mc=Minecraft.getInstance();try{
  if(step<0){if(!(mc.screen instanceof TitleScreen))return;ServerMenuClient.previewTransport=q->{};SkinClient.library=library();SkinClient.busy=false;inputs();ServerMenuClient.previewTransport=q->{};new java.io.File(output).mkdirs();step=0;at=System.currentTimeMillis()+400;}
  if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+350;
  if(step>0){require(mc.screen instanceof QuickSkinsScreen,"Wheel closed unexpectedly");for(var child:mc.screen.children())if(child instanceof AbstractWidget w)require(w.getX()>=0&&w.getY()>=0&&w.getRight()<=mc.screen.width&&w.getBottom()<=mc.screen.height,"wheel outside viewport");UiCaptureHarness.grab(new java.io.File(output),"wheel-"+step+".png",mc.getMainRenderTarget(),m->{});}
  if(step==FRAMES.size()){step++;System.out.println("RIVET_SKIN_WHEEL_OK: "+FRAMES.size()+" frames; geometry, favorites, cancellation, server refusal and acknowledgement");mc.stop();return;}
  var frame=FRAMES.get(step++);frame.apply();SkinClient.library=library();SkinClient.busy=false;
  if(frame.scene()==1)SkinClient.busy=true;
  if(frame.scene()==2){SkinClient.status="Соединение прервано. Повторите запрос.";SkinClient.retryable=true;}else{SkinClient.status="";SkinClient.retryable=false;}
  mc.setScreen(new QuickSkinsScreen(null));
 }catch(Throwable error){System.out.println("RIVET_SKIN_WHEEL_FAILED step="+step);error.printStackTrace();step=FRAMES.size()+1;mc.stop();}}
}
