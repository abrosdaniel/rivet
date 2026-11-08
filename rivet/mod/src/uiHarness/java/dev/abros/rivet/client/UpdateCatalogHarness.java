package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.pack.PackDiscovery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Real HTTP failures/cache + the mandatory-update consumer and native retry dialog. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class UpdateCatalogHarness {
 private static boolean started;private static CompletableFuture<java.util.List<String>> checks;private static int screen;private static long shown;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_UPDATE_CATALOG_CHECK")==null)return;var mc=Minecraft.getInstance();
  try{
   if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;checks=CompletableFuture.supplyAsync(()->{try{return verify();}catch(Exception failure){throw new CompletionException(failure);}});return;}
   if(!checks.isDone())return;var messages=checks.join();
   if(screen>=messages.size()*3){System.out.println("RIVET_UPDATE_CATALOG_OK stale persisted empty cache, 429, 502, partial descriptor failure, recovery, confirmed absence, native retry scales=1,2,3");mc.stop();return;}
   if(shown==0){mc.options.guiScale().set(screen%3+1);mc.resizeDisplay();var parent=new TitleScreen();var view=new ReviewScreen(parent,Client.tr("ui.server_pack_88518674"),messages.get(screen/3),Client.tr("ui.retry_request_fcb97cf7"),()->{screen++;shown=0;},true);mc.setScreen(view);shown=System.currentTimeMillis();return;}
   if(System.currentTimeMillis()-shown<250)return;
   var retry=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().equals(Client.tr("ui.retry_request_fcb97cf7"))).map(w->(Button)w).findFirst().orElseThrow();check(retry.active,"Retry disabled");retry.onPress();
  }catch(Throwable failure){System.out.println("RIVET_UPDATE_CATALOG_FAILED");failure.printStackTrace();mc.stop();}
 }
 private static java.util.List<String> verify()throws Exception{
  var mode=new java.util.concurrent.atomic.AtomicInteger();var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  String next=Versions.sameMajor(dev.abros.rivet.Rivet.VERSION,"2.0.0")?"2.99.0":"9.99.0";
  server.createContext("/",exchange->{try{
   String url=java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(),StandardCharsets.UTF_8);int status=200;String body;
   if(mode.get()==2||mode.get()==3){status=mode.get()==2?429:502;body="temporary";}
   else if(url.equals(CoreUpdater.RELEASES)){body=mode.get()==0?"[]":"[{\"draft\":false,\"tag_name\":\"v"+next+"\"},{\"draft\":false,\"tag_name\":\"v2.1.0\"}]";}
   else if(mode.get()==4&&url.contains("/v"+next+"/")){status=502;body="descriptor unavailable";}
   else{String version=url.contains("/v"+next+"/")?next:"2.1.0";var artifact=new JsonObject();artifact.addProperty("minecraft","1.21.1");artifact.addProperty("neoForge","21.1.250");artifact.addProperty("java",21);artifact.addProperty("url",CoreUpdater.REPOSITORY+"/releases/download/v"+version+"/rivet-"+version+"-mc1.21.1-neoforge.jar");artifact.addProperty("sha256","0".repeat(64));artifact.addProperty("size",1);artifact.addProperty("helperProtocolVersion",1);var descriptor=new JsonObject();descriptor.addProperty("schemaVersion",1);descriptor.addProperty("version",version);descriptor.add("protocols",WireProtocols.current());var artifacts=new JsonArray();artifacts.add(artifact);descriptor.add("artifacts",artifacts);body=descriptor.toString();}
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
  }finally{exchange.close();}});server.start();
  try{
   var remote=new Remote(){@Override public byte[] bytes(String url,int limit)throws java.io.IOException,InterruptedException{
    var target=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/?"+java.net.URLEncoder.encode(url,StandardCharsets.UTF_8));
    var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(target).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    if(response.statusCode()!=200)throw new Remote.HttpFailure(response.statusCode(),java.net.URI.create(url));return response.body();
   }};
   var root=java.nio.file.Files.createTempDirectory(Minecraft.getInstance().gameDirectory.toPath(),"update-check-");var hub=new Hub(root,dev.abros.rivet.Rivet.VERSION,"21.1.250");var updater=new CoreUpdater(remote,root.resolve("metadata"));
   check(updater.catalog(dev.abros.rivet.Rivet.VERSION,"1.21.1","21.1.250",100,true,false).updates().isEmpty(),"Empty cache not seeded");
   var field=Hub.class.getDeclaredField("coreUpdater");field.setAccessible(true);field.set(hub,new CoreUpdater(remote,root.resolve("metadata")));
   var required=new PackDiscovery.Status(null,next,WireProtocols.current());mode.set(1);
   check(ServerPackClient.requiredUpdate(hub,required).version().equals(next),"Persisted empty cache hid published version");
   var errors=new java.util.ArrayList<String>();
   String limited=Errors.message(new Remote.HttpFailure(429,java.net.URI.create(CoreUpdater.RELEASES),System.currentTimeMillis()+600_000));check(!limited.contains("rivet.")&&limited.contains("600"),"Rate limit wait not localized");errors.add(limited);
   for(int failureMode:new int[]{2,3,4}){mode.set(failureMode);try{ServerPackClient.requiredUpdate(hub,required);throw new AssertionError("Failure became success");}catch(java.io.IOException expected){check(expected.getMessage().contains(Client.text("update.checkFailed")),"Failure became no compatible release");check(!expected.getMessage().contains(Client.text("update.noCompatibleRelease")),"False absence");check(!expected.getMessage().contains("rivet.core."),"Untranslated diagnosis");errors.add(expected.getMessage());}
    mode.set(1);check(ServerPackClient.requiredUpdate(hub,required).version().equals(next),"Retry failed to bypass backoff");
   }
   mode.set(0);try{ServerPackClient.requiredUpdate(hub,required);throw new AssertionError("Missing release accepted");}catch(java.io.IOException expected){check(expected.getMessage().contains(Client.text("update.noCompatibleRelease")),"Complete absence not distinguished");check(!expected.getMessage().contains("rivet.core."),"Untranslated diagnosis");errors.add(expected.getMessage());}
   check(!java.nio.file.Files.exists(root.resolve("rivet/pending.json")),"Check staged update");return errors;
  }finally{server.stop(0);}
 }
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
