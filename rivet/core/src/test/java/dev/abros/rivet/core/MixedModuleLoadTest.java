package dev.abros.rivet.core;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in mixed SQL workload on the isolated test database; timings are observations, not CI thresholds. */
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_MIXED_LOAD",matches="true")
class MixedModuleLoadTest {
 @TempDir Path root;
 private static JsonObject request(String section,String operation){var q=new JsonObject();q.addProperty("section",section);q.addProperty("op",operation);return q;}
 @Test void concurrentChatTasksBoardAndNotificationsKeepBoundsAndIsolation()throws Exception{
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());var history=new ChatHistory(db,100,7);
  int players=Integer.parseInt(System.getenv().getOrDefault("RIVET_MIXED_PLAYERS","8"));if(players<1||players>100)throw new IllegalArgumentException("Load test supports 1–100 players");
  var actors=new ArrayList<CommunityStore.Actor>();var tasks=new ArrayList<JsonObject>();
  for(int i=0;i<players;i++){
   var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Load"+i,false,false);actors.add(actor);store.seen(actor);
   var q=request("home","workSave");q.addProperty("title","Private "+i);q.addProperty("description","Personal task");tasks.add(store.request(actor,q).getAsJsonObject("task"));
   store.externalNotice(actor.id(),LocalizedText.key("rivet.ui.notification_3a42cfeb"),"");
  }
  var samples=new ConcurrentHashMap<String,java.util.concurrent.ConcurrentLinkedQueue<Long>>();
  var gate=new CountDownLatch(1);long begin=System.nanoTime();
  try(var workers=Executors.newFixedThreadPool(8)){
   var futures=new ArrayList<Future<?>>();
   for(int index=0;index<actors.size();index++){
    final int worker=index;futures.add(workers.submit(()->{
     var actor=actors.get(worker);var task=tasks.get(worker);gate.await();
     try(var locale=Messages.locale(worker%2==0?"ru_ru":"en_us")){
      for(int i=0;i<240;i++){
       int op=i%6;long start=System.nanoTime();String metric;
       switch(op){
        case 0->{metric="task-list";var result=store.request(actor,request("home","workList"));var rows=result.getAsJsonArray("tasks");assertEquals(1,rows.size());assertEquals(actor.id(),Json.str(rows.get(0).getAsJsonObject(),"owner"));LocalizedText.localize(result);}
        case 1->{metric="task-write";var q=request("home","workSave");q.addProperty("task",Json.str(task,"id"));q.add("revision",task.get("revision"));q.addProperty("title","Private "+worker);q.addProperty("description","Revision "+i);task=store.request(actor,q).getAsJsonObject("task");assertTrue(task.getAsJsonArray("history").size()<=60);LocalizedText.localize(task);}
        case 2->{metric="board-read";store.request(actor,request("board","list"));}
        case 3->{metric="chat-write";history.append("local",System.currentTimeMillis(),actor.id(),List.of(actor.id()));}
        case 4->{metric="chat-replay";var rows=history.recent(actor.id(),System.currentTimeMillis()+1,true,true);assertTrue(rows.size()<=100);assertTrue(rows.stream().allMatch(row->row.body().equals(actor.id())));}
        default->{metric="notice-read";LocalizedText.localize(store.request(actor,request("notifications","list")));}
       }
       samples.computeIfAbsent(metric,k->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(System.nanoTime()-start);
      }
     }
     return null;
    }));
   }
   gate.countDown();for(var future:futures)future.get(120,TimeUnit.SECONDS);
  }
  assertEquals(players*240,samples.values().stream().mapToInt(Collection::size).sum());
  samples.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{long[] values=entry.getValue().stream().mapToLong(Long::longValue).sorted().toArray();System.out.printf(Locale.ROOT,"RIVET_MIXED_SAMPLE op=%s count=%d p50=%.3fms p95=%.3fms max=%.3fms%n",entry.getKey(),values.length,values[values.length/2]/1e6,values[(values.length*95)/100]/1e6,values[values.length-1]/1e6);});
  System.out.printf(Locale.ROOT,"RIVET_MIXED_LOAD_OK players=%d workers=8 operations=%d elapsed=%.3fs%n",players,players*240,(System.nanoTime()-begin)/1e9);
 }
}
