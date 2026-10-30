package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityReadPerformanceTest {
 @TempDir Path temp;
 @Test void notificationRefreshForOneChangedPlayerDoesNotScanEveryone()throws Exception{
  var database=TestDatabase.database(temp);var store=new CommunityStore(database,CommunityStore.defaults());var players=new ArrayList<UUID>();
  for(int i=0;i<100;i++)players.add(UUID.randomUUID());
  database.communityTransaction(()->{try(var insert=database.connection().prepareStatement("INSERT INTO notices(recipient,body) SELECT ?, '{\"section\":\"events\"}'::jsonb FROM generate_series(1,200)")){
   for(var id:players){insert.setString(1,id.toString());insert.addBatch();}insert.executeBatch();}return null;});
  var affected=List.of(players.getFirst());for(int i=0;i<10;i++){store.notificationSummary(players);store.notificationSummary(affected);}
  long[] full=new long[100],scoped=new long[100];
  for(int i=0;i<100;i++){
   long started=System.nanoTime();var all=store.notificationSummary(players);full[i]=System.nanoTime()-started;
   started=System.nanoTime();var one=store.notificationSummary(affected);scoped[i]=System.nanoTime()-started;
   assertEquals(100,all.size());assertEquals(1,one.size());assertEquals(all.get(players.getFirst()),one.get(players.getFirst()));assertEquals(200,one.get(players.getFirst()).unread());
  }
  for(var sample:List.of(full,scoped))Arrays.sort(sample);
  System.out.printf(Locale.ROOT,"RIVET_NOTICE_BENCH players=100 notices=20000 samples=100 fullP50Ms=%.3f fullP95Ms=%.3f fullMaxMs=%.3f affectedP50Ms=%.3f affectedP95Ms=%.3f affectedMaxMs=%.3f%n",full[49]/1e6,full[94]/1e6,full[99]/1e6,scoped[49]/1e6,scoped[94]/1e6,scoped[99]/1e6);
 }
 @Test void repeatedPagedReadsRemainStable()throws Exception{
  var store=new CommunityStore(TestDatabase.database(temp),CommunityStore.defaults());var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"PerfReader",true,true);store.seen(actor);
  for(int i=0;i<40;i++){var author=new CommunityStore.Actor(UUID.randomUUID().toString(),"Author"+i,false,false);store.seen(author);var q=new JsonObject();q.addProperty("section","board");q.addProperty("op","create");q.addProperty("title","Entry "+i);q.addProperty("description","Representative description ".repeat(20));q.addProperty("days",7);q.addProperty("type","Предложение");store.request(author,q);}
  var q=new JsonObject();q.addProperty("section","board");q.addProperty("op","list");var baseline=store.request(actor,q);assertFalse(baseline.getAsJsonArray("entries").isEmpty());
  for(int i=0;i<10;i++)store.request(actor,q);var times=new long[100];for(int i=0;i<times.length;i++){long start=System.nanoTime();var result=store.request(actor,q);times[i]=System.nanoTime()-start;assertEquals(baseline.getAsJsonArray("entries"),result.getAsJsonArray("entries"));}
  Arrays.sort(times);System.out.printf(Locale.ROOT,"RIVET_READ_BENCH records=40 reads=100 median=%.2fms p95=%.2fms max=%.2fms%n",times[50]/1e6,times[95]/1e6,times[99]/1e6);
 }
}
