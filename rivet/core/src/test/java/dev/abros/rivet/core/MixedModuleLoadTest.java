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
 @Test void concurrentChatGroupsPlacesAndNotificationsKeepBoundsAndIsolation()throws Exception{
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());var history=new ChatHistory(db,100,7);
  int players=Integer.parseInt(System.getenv().getOrDefault("RIVET_MIXED_PLAYERS","8"));if(players<1||players>100)throw new IllegalArgumentException("Load test supports 1–100 players");
  var actors=new ArrayList<CommunityStore.Actor>();var groups=new ArrayList<JsonObject>();var places=new ArrayList<String>();
  for(int i=0;i<players;i++){
   var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Load"+i,false,false);actors.add(actor);store.seen(actor);
   var q=request("groups","create");q.addProperty("title","Group "+i);q.addProperty("description","Group for load testing");q.addProperty("type","Команда");var group=store.request(actor,q).getAsJsonObject("detail");
   q=request("groups","plusItemSave");q.addProperty("id",Json.str(group,"id"));q.addProperty("kind","place");q.addProperty("title","Private "+i);q.addProperty("description","Private place");q.add("location",new CommunityLocation("Private "+i,"minecraft:overworld",i,64,i,true).json());
   var saved=store.request(actor,q);groups.add(saved.getAsJsonObject("detail"));places.add(Json.str(saved,"changedId"));
   store.externalNotice(actor.id(),LocalizedText.key("rivet.ui.notification_3a42cfeb"),"");
  }
  if(players>1){var foreign=request("groups","plusItemRead");foreign.addProperty("id",Json.str(groups.get(0),"id"));foreign.addProperty("itemId",places.get(0));assertThrows(CommunityFailure.class,()->store.request(actors.get(1),foreign));}
  var samples=new ConcurrentHashMap<String,java.util.concurrent.ConcurrentLinkedQueue<Long>>();
  var gate=new CountDownLatch(1);long begin=System.nanoTime();
  try(var workers=Executors.newFixedThreadPool(8)){
   var futures=new ArrayList<Future<?>>();
   for(int index=0;index<actors.size();index++){
    final int worker=index;futures.add(workers.submit(()->{
     var actor=actors.get(worker);var group=groups.get(worker);String place=places.get(worker);gate.await();
     try(var locale=Messages.locale(worker%2==0?"ru_ru":"en_us")){
      for(int i=0;i<240;i++){
       int op=i%6;long start=System.nanoTime();String metric;
       switch(op){
        case 0->{metric="group-list";var q=request("groups","list");q.addProperty("mine",true);var result=store.request(actor,q);var rows=result.getAsJsonArray("entries");assertEquals(1,rows.size());assertEquals(Json.str(group,"id"),Json.str(rows.get(0).getAsJsonObject(),"id"));LocalizedText.localize(result);}
        case 1->{metric="group-write";var q=request("groups","edit");q.addProperty("id",Json.str(group,"id"));q.add("revision",group.get("revision"));q.addProperty("title","Group "+worker);q.addProperty("description","Revision "+i);q.addProperty("type","Команда");group=store.request(actor,q).getAsJsonObject("detail");assertTrue(group.getAsJsonArray("history").size()<=5);LocalizedText.localize(group);}
        case 2->{metric="private-place-read";var q=request("groups","plusItemRead");q.addProperty("id",Json.str(group,"id"));q.addProperty("itemId",place);var item=store.request(actor,q).getAsJsonObject("item");assertEquals("Private "+worker,Json.str(item,"title"));assertTrue(item.getAsJsonObject("location").get("membersOnly").getAsBoolean());}
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
