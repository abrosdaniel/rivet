package dev.abros.rivet.compattests;

import com.google.gson.*;
import dev.abros.rivet.client.CompatibilityClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Runs beside NumismaticsClientHarness; exercises the actual spark packet/provider/command path. */
@EventBusSubscriber(modid="rivet_compat_tests",value=Dist.CLIENT)
public final class AllAdaptersClientHarness {
 private static boolean done,pending;private static int stage;private static long began,next;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_ALL_ADAPTERS_TEST")==null||done)return;var mc=Minecraft.getInstance();
  if(mc.player==null||!dev.abros.rivet.network.Protocol.supportedFeatures.contains("compatibility-adapters"))return;
  long now=System.currentTimeMillis();if(began==0){began=now;next=now+10000;}
  if(now-began>100000){fail(new AssertionError("spark timeout stage="+stage));return;}
  if(pending||now<next)return;pending=true;next=now+3000;
  var request=new JsonObject();request.addProperty("op",stage==1||stage==6?"start":stage==3||stage==8?"cancel":"snapshot");request.addProperty("seconds",60);
  CompatibilityClient.request("spark",request).whenComplete((reply,error)->{
   pending=false;if(error!=null){fail(error);return;}
   try{
    if(stage==0){check(reply.get("commands").getAsBoolean(),"spark controls unavailable: "+reply);check(reply.get("canProfile").getAsBoolean(),"spark permission missing: "+reply);check(reply.getAsJsonObject("current").has("tps"),"spark metrics missing");if(!reply.get("profiler").getAsString().equals("idle")){System.out.println("RIVET_SPARK_PROBE state="+reply.get("profiler")+" notice="+reply.get("notice"));return;}stage=1;}
    else if(stage==1)stage=2;
    else if(stage==2){if(!reply.get("profiler").getAsString().equals("running")){System.out.println("RIVET_SPARK_WAIT state="+reply.get("profiler")+" notice="+reply.get("notice"));return;}check(reply.get("owned").getAsBoolean(),"Profiler ownership missing");stage=3;}
    else if(stage==3)stage=4;
    else if(stage==4){if(!reply.get("profiler").getAsString().equals("idle"))return;check(!reply.get("owned").getAsBoolean(),"Profiler ownership survived cancel");check(reply.getAsJsonArray("reports").isEmpty(),"Cancel unexpectedly published a report");mc.player.connection.sendCommand("spark profiler start --force-java-sampler --timeout 120");stage=5;}
    else if(stage==5){if(!reply.get("profiler").getAsString().equals("running"))return;check(!reply.get("owned").getAsBoolean(),"Foreign profiler claimed");stage=6;}
    else if(stage==6)stage=7;
    else if(stage==7){check(reply.get("profiler").getAsString().equals("running")&&!reply.get("owned").getAsBoolean(),"Foreign recording replaced");stage=8;}
    else if(stage==8)stage=9;
    else if(stage==9){check(reply.get("profiler").getAsString().equals("running")&&!reply.get("owned").getAsBoolean(),"Foreign recording cancelled");mc.player.connection.sendCommand("spark profiler cancel");stage=10;}
    else if(stage==10){if(!reply.get("profiler").getAsString().equals("idle"))return;verifyVoiceClient();System.out.println("RIVET_ALL_ADAPTERS_CLIENT_OK spark metrics/start/cancel/foreign ownership; both voice diagnostics");done=true;mc.stop();}
   }catch(Throwable failure){fail(failure);}
  });
 }
 private static void verifyVoiceClient()throws Exception{
  var cls=Class.forName("dev.abros.rivet.client.ClientCompatibilityRegistry");var method=cls.getDeclaredMethod("diagnostics");method.setAccessible(true);var rows=(JsonArray)method.invoke(null);
  for(String name:java.util.List.of("plasmovoice:diagnostics","voicechat:diagnostics")){var row=rows.asList().stream().map(JsonElement::getAsJsonObject).filter(j->j.get("id").getAsString().equals(name)).findFirst().orElseThrow();check(row.get("available").getAsBoolean(),"Client voice diagnostics unavailable: "+row);}
  var voice=cls.getDeclaredMethod("voice");voice.setAccessible(true);check(((java.util.List<?>)voice.invoke(null)).size()>=10,"Missing voice diagnostics rows");
 }
 private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 private static void fail(Throwable error){System.out.println("RIVET_ALL_ADAPTERS_CLIENT_FAILED stage="+stage);error.printStackTrace();done=true;Minecraft.getInstance().stop();}
}
