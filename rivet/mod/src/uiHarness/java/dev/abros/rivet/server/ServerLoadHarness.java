package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.MapPositionPage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.concurrent.atomic.*;

/** Read-only opt-in work on the real server/executor/DB. Synthetic requests, one connected viewer. */
@EventBusSubscriber(modid="rivet",value=Dist.DEDICATED_SERVER)
public final class ServerLoadHarness {
 private static final String OUTPUT=System.getenv("RIVET_SERVER_LOAD_OUTPUT");
 private static final String[] MODES={"idle","positions","mixed"};
 private static final String[] SECTIONS={"home","groups","events","board"};
 private static final AtomicInteger pending=new AtomicInteger(),completed=new AtomicInteger(),rejected=new AtomicInteger();
 private static final java.util.concurrent.ConcurrentLinkedQueue<Throwable> failures=new java.util.concurrent.ConcurrentLinkedQueue<>();
 private static final JsonArray results=new JsonArray();private static int phase,ticks,peak,requests;private static boolean active,done,draining;private static long tickStart,deadline;
 @SubscribeEvent public static void pre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e){if(OUTPUT!=null&&active&&!done)tickStart=System.nanoTime();}
 @SubscribeEvent public static void post(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  if(OUTPUT==null||done)return;
  try{
   var server=e.getServer();var viewer=server.getPlayerList().getPlayerByName("RivetMapTest");if(viewer==null||!ServerIntegration.supports(viewer,MapPositionPage.FEATURE))return;
   if(!active){PerformanceMetrics.clear();PerformanceMetrics.detailed(true);active=true;deadline=System.currentTimeMillis()+30000;return;}
   if(tickStart!=0)PerformanceMetrics.record("server.tick.body",System.nanoTime()-tickStart,false);
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Phase deadline exceeded");
   if(draining){if(pending.get()!=0)return;finish();return;}
   int mode=phase%3;
   if(mode!=0)for(int i=0;i<5;i++){
    boolean position=mode==1||i==0;String section=position?"home":SECTIONS[i-1];String metric=position?"positions":section;
    var q=new JsonObject();q.addProperty("action","community");q.addProperty("section",section);q.addProperty("op",position?"mapPositionPeers":"list");if(position)ServerMapPlayers.prepare(viewer,q);
    var actor=new CommunityStore.Actor(viewer.getUUID().toString(),viewer.getGameProfile().getName(),false,false);var store=ServerFeatures.communityStore();long queued=System.nanoTime();pending.incrementAndGet();requests++;
    try{ServerFeatures.read(()->{PerformanceMetrics.record("load."+metric+".wait",System.nanoTime()-queued,false);long started=System.nanoTime();try{
      // Match the production pre-execution handoff for community menu reads.
      if(!position)server.submit(()->{if(server.getPlayerList().getPlayer(viewer.getUUID())!=viewer)throw new IllegalStateException("Viewer left");return true;}).get(2,java.util.concurrent.TimeUnit.SECONDS);
      var out=store.request(actor,q);PerformanceMetrics.record("load."+metric+".read",System.nanoTime()-started,false);long ready=System.nanoTime();
      server.execute(()->{try{PerformanceMetrics.record("load.reply.wait",System.nanoTime()-ready,false);long fill=System.nanoTime();if(position)for(var page:ServerMapPlayers.fill(viewer,q,out))MapPositionPage.wire(page);else Json.GSON.toJson(out);PerformanceMetrics.record("load."+metric+".reply",System.nanoTime()-fill,false);completed.incrementAndGet();}catch(Throwable ex){failures.add(ex);}finally{pending.decrementAndGet();}});
     }catch(Throwable ex){failures.add(ex);pending.decrementAndGet();}});}catch(java.util.concurrent.RejectedExecutionException full){rejected.incrementAndGet();pending.decrementAndGet();}
   }
   peak=Math.max(peak,ServerFeatures.sparkRuntime().get("readQueue").getAsInt());
   if(++ticks>=200)draining=true;
  }catch(Throwable ex){failures.add(ex);done=true;PerformanceMetrics.detailed(false);System.out.println("RIVET_SERVER_LOAD_FAILED");ex.printStackTrace();}
 }
 private static void finish()throws Exception{
  if(!failures.isEmpty())throw new IllegalStateException("Server load failures",failures.peek());
  if(completed.get()+rejected.get()!=requests)throw new IllegalStateException("Lost requests");
  var row=new JsonObject();row.addProperty("mode",MODES[phase%3]);row.addProperty("repeat",phase/3);row.addProperty("requests",requests);row.addProperty("completed",completed.get());row.addProperty("rejected",rejected.get());row.addProperty("peakReadQueue",peak);var metrics=new JsonObject();
  var distributions=PerformanceMetrics.distributions();for(var entry:PerformanceMetrics.snapshot().entrySet()){var sample=entry.getValue();var item=new JsonObject();item.addProperty("count",sample.count());item.addProperty("meanMs",sample.meanMillis());item.addProperty("maxMs",sample.maxNanos()/1e6);var d=distributions.get(entry.getKey());if(d!=null){item.addProperty("p95Ms",d.p95Nanos()/1e6);item.addProperty("p99Ms",d.p99Nanos()/1e6);}metrics.add(entry.getKey(),item);}row.add("metrics",metrics);results.add(row);
  System.out.println("RIVET_SERVER_LOAD_PHASE mode="+MODES[phase%3]+" completed="+completed.get()+" rejected="+rejected.get());
  if(++phase==9){var out=new JsonObject();out.addProperty("syntheticRequests",true);out.addProperty("connectedViewers",1);out.addProperty("requestsPerTick",5);out.addProperty("measuredTicksPerPhase",200);out.add("results",results);Files.writeString(Path.of(OUTPUT),Json.GSON.toJson(out));done=true;PerformanceMetrics.detailed(false);System.out.println("RIVET_SERVER_LOAD_OK phases=9");return;}
  PerformanceMetrics.clear();ticks=peak=requests=0;completed.set(0);rejected.set(0);draining=false;deadline=System.currentTimeMillis()+30000;
 }
 private ServerLoadHarness(){}
}
