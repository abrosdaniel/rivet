package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.MapPositionPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in synthetic recipients, real SQL/serialization. The 50 ms game-thread handoff is modelled. */
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_POSITION_LOAD",matches="true")
class PositionFanoutLoadTest {
 @TempDir Path temp;
 @Test void fanout()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());
  var results=new JsonArray();
  for(int count:new int[]{10,50,100})for(boolean handoff:new boolean[]{true,false})for(boolean spread:new boolean[]{false,true})for(int repeat=0;repeat<3;repeat++)results.add(run(store,count,handoff,spread,repeat));
  var out=new JsonObject();out.addProperty("synthetic",true);out.addProperty("arrivalSchedule","player UUID jitter rounded to 50ms ticks");out.addProperty("modelledGameTickMs",50);out.addProperty("readWorkers",2);out.addProperty("queueCapacity",64);out.add("results",results);
  Files.writeString(Path.of(System.getenv("RIVET_POSITION_LOAD_OUTPUT")),Json.GSON.toJson(out));System.out.println("RIVET_POSITION_LOAD_OK scenarios="+results.size());
 }
 private static JsonObject run(CommunityStore store,int count,boolean handoff,boolean spread,int repeat)throws Exception{
  var workers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(64),new ThreadPoolExecutor.AbortPolicy());
  var arrivals=Executors.newSingleThreadScheduledExecutor();var game=Executors.newSingleThreadScheduledExecutor();
  var waits=Collections.synchronizedList(new ArrayList<Long>());var latencies=Collections.synchronizedList(new ArrayList<Long>());var packing=Collections.synchronizedList(new ArrayList<Long>());
  var bytes=new AtomicLong();var rejected=new AtomicInteger();var peak=new AtomicInteger();var failures=new ConcurrentLinkedQueue<Throwable>();var done=new CountDownLatch(count);
  long start=System.nanoTime();
  try{
   for(int i=0;i<count;i++){int viewer=i;long delay=spread?((dev.abros.rivet.core.map.MapPositionPoll.initialDelay(new UUID(0,i+1))+49)/50)*50:0;
    arrivals.schedule(()->{long submitted=System.nanoTime();try{workers.execute(()->{long began=System.nanoTime();waits.add(began-submitted);try{
     if(handoff){long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);game.schedule(()->{},50-elapsed%50,TimeUnit.MILLISECONDS).get(2,TimeUnit.SECONDS);}
     var actor=new CommunityStore.Actor(new UUID(0,viewer+1).toString(),"Viewer"+viewer,false,false);var q=new JsonObject();q.addProperty("op","mapPositionPeers");q.addProperty("section","home");q.addProperty("positionCompact",true);var candidates=new JsonArray();for(int n=0;n<count;n++)if(n!=viewer)candidates.add(new UUID(0,n+1).toString());q.add("positionCandidates",candidates);
     var response=store.request(actor,q);var peers=new JsonArray();for(var row:response.getAsJsonArray("positionPolicies")){var peer=new JsonObject();peer.add("uuid",row.getAsJsonObject().get("uuid"));peer.addProperty("name","TestPlayer");peer.addProperty("dimension","minecraft:overworld");peer.addProperty("x",100);peer.addProperty("y",70);peer.addProperty("z",100);peers.add(peer);}assertEquals(count-1,peers.size());response.remove("positionPolicies");response.addProperty("positionRevision",1);
     long pack=System.nanoTime();for(var page:MapPositionPage.split(response,peers))bytes.addAndGet(MapPositionPage.wire(page).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);packing.add(System.nanoTime()-pack);
     latencies.add(System.nanoTime()-submitted);
    }catch(Throwable error){failures.add(error);}finally{done.countDown();}});peak.accumulateAndGet(workers.getQueue().size(),Math::max);}catch(RejectedExecutionException full){rejected.incrementAndGet();done.countDown();}},delay,TimeUnit.MILLISECONDS);
   }
   assertTrue(done.await(20,TimeUnit.SECONDS));assertTrue(failures.isEmpty(),failures.toString());assertEquals(count,latencies.size()+rejected.get());assertTrue(peak.get()<=64);
   var out=new JsonObject();out.addProperty("recipients",count);out.addProperty("handoff",handoff);out.addProperty("spread",spread);out.addProperty("repeat",repeat);out.addProperty("accepted",latencies.size());out.addProperty("rejected",rejected.get());out.addProperty("peakQueue",peak.get());out.addProperty("waitP95Ms",p95(waits));out.addProperty("responseP95Ms",p95(latencies));out.addProperty("packingP95Ms",p95(packing));out.addProperty("jsonBytes",bytes.get());return out;
  }finally{arrivals.shutdownNow();workers.shutdownNow();game.shutdownNow();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));}
 }
 private static double p95(List<Long> values){if(values.isEmpty())return 0;var sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6;}
}
