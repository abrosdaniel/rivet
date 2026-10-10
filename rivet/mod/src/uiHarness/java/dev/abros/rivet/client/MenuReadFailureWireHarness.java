package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.function.Consumer;

/** Read-only live server checks: asynchronous failures retain correlation and allow recovery. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MenuReadFailureWireHarness {
 private static boolean started,done;private static long deadline;private static int phase;
 private static String correlation;private static Consumer<JsonObject> previous;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_MENU_READ_FAILURES")==null||done)return;
  try{
   if(!started){if(!WorldMapClient.ready()||!ServerMenuClient.available())return;started=true;verifyPrivacyRetry();previous=Protocol.featureState;Protocol.featureState=MenuReadFailureWireHarness::receive;send();}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("No correlated reply for phase "+phase);
  }catch(Throwable failure){fail(failure);}
 }
 private static void verifyPrivacyRetry(){
  var mc=Minecraft.getInstance();var parent=mc.screen;var transport=ServerMenuClient.previewTransport;int scale=mc.options.guiScale().get();
  try{for(int gui=1;gui<=3;gui++){
   mc.options.guiScale().set(gui);mc.resizeDisplay();var sent=new ArrayList<JsonObject>();ServerMenuClient.previewTransport=j->sent.add(j.deepCopy());
   var screen=new MapSharingScreen(parent);mc.setScreen(screen);
   var initial=sent.stream().filter(j->Json.opt(j,"op","").equals("mapPositionSettings")).findFirst().orElseThrow();
   var failure=new JsonObject();failure.addProperty("request",Json.str(initial,"request"));failure.addProperty("error",true);failure.addProperty("text","Проверка временной ошибки");screen.receiveCommunity(failure);UiGeometryHarness.verify(screen);MapPositionsHarness.verifySidebar(screen);
   var retry=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&UiActions.commandOf(b)==UiActions.Command.RETRY).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();
   if(!retry.active)throw new IllegalStateException("Privacy retry disabled");retry.onPress();var request=sent.getLast();
   if(!Json.str(request,"op").equals("mapPositionSettings")||Json.str(request,"request").equals(Json.str(initial,"request")))throw new IllegalStateException("Privacy retry reused old correlation or wrote settings");
   var response=new JsonObject();response.addProperty("request",Json.str(request,"request"));response.add("positionSettings",new dev.abros.rivet.core.map.MapPositionPolicy("full","all").json());response.addProperty("positionGroups",true);screen.receiveCommunity(response);UiGeometryHarness.verify(screen);
   if(screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&b.active&&b.getMessage().getString().equals("Сохранить")))throw new IllegalStateException("Privacy form did not recover");
  }}finally{ServerMenuClient.previewTransport=transport;mc.options.guiScale().set(scale);mc.resizeDisplay();mc.setScreen(parent);}
  System.out.println("RIVET_PRIVACY_RETRY_OK scales=3 read-only");
 }
 private static void send(){
  var request=new JsonObject();request.addProperty("action",phase==0?"myReport":phase==1||phase==3?"myReports":"players");request.addProperty("page",0);
  if(phase==0)request.addProperty("id",UUID.randomUUID().toString());
  if(phase==1||phase==2)request.addProperty("cursor","invalid-cursor");
  if(phase==2){request.addProperty("query","");request.addProperty("all",false);}
  correlation=UUID.randomUUID().toString();request.addProperty("request",correlation);deadline=System.currentTimeMillis()+8000;
  ServerMenuClient.requestBackground(request);
 }
 private static void receive(JsonObject reply){
  try{
   previous.accept(reply);if(!correlation.equals(Json.opt(reply,"request","")))return;
   if(phase<3){if(!reply.has("error")||!reply.has("code")||!reply.has("recovery"))throw new IllegalStateException("Unstructured read failure: phase "+phase);}
   else{if(reply.has("error")||!reply.has("reports"))throw new IllegalStateException("Read did not recover");System.out.println("RIVET_MENU_READ_FAILURES_OK errors=3 recovery=1");finish();return;}
   phase++;send();
  }catch(Throwable failure){fail(failure);}
 }
 private static void fail(Throwable failure){System.out.println("RIVET_MENU_READ_FAILURES_FAILED phase="+phase);failure.printStackTrace();finish();}
 private static void finish(){done=true;if(previous!=null)Protocol.featureState=previous;Minecraft.getInstance().stop();}
}
