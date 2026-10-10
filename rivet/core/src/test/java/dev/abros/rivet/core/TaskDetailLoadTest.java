package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_TASK_DETAIL_LOAD",matches="true")
class TaskDetailLoadTest {
 @TempDir Path temp;
 @Test void details()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var actor=new CommunityStore.Actor("reader","Reader",false,false);store.seen(actor);
  db.transaction(()->{try(var s=db.connection().createStatement()){
   s.execute("INSERT INTO community_group_items(id,kind,owner,body) SELECT 'detail-'||n,'task','reader',jsonb_build_object('title','Task '||n,'status','open','comments',(SELECT jsonb_agg(jsonb_build_object('text',repeat('Comment ',60))) FROM generate_series(1,40)),'history',(SELECT jsonb_agg(jsonb_build_object('operation','update','reason',repeat('History ',30))) FROM generate_series(1,20))) FROM generate_series(0,10) n");
   s.execute("INSERT INTO community_task_codes SELECT id,'CODE'||id FROM community_group_items");
   s.execute("INSERT INTO community_task_stocks SELECT id||'-sign-'||n,id,jsonb_build_object('inventory',id||'-chest-'||n,'at',100,'items',jsonb_build_object('minecraft:stone',64,'minecraft:dirt',32)) FROM community_group_items CROSS JOIN generate_series(1,64) n");s.execute("ANALYZE community_task_stocks");
  }return null;});
  var output=new JsonArray();for(int count:new int[]{0,1,10})for(boolean resources:new boolean[]{false,true}){
   var deps=new JsonArray();for(int i=1;i<=count;i++)deps.add("detail-"+i);var items=new JsonArray();if(resources){var item=new JsonObject();item.addProperty("item","minecraft:stone");item.addProperty("amount",64);items.add(item);}
   db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE community_group_items SET body=body || jsonb_build_object('dependencies',?::jsonb,'resources',?::jsonb) WHERE id='detail-0'")){q.setString(1,deps.toString());q.setString(2,items.toString());q.executeUpdate();}return null;});
   var q=new JsonObject();q.addProperty("section","home");q.addProperty("op","workGet");q.addProperty("task","detail-0");var baseline=store.request(actor,q);assertEquals(count,baseline.getAsJsonObject("task").getAsJsonArray("dependencyDetails").size());long[] times=new long[9];for(int i=0;i<times.length;i++){long start=System.nanoTime();var response=store.request(actor,q);times[i]=System.nanoTime()-start;assertEquals(baseline,response);}Arrays.sort(times);var row=new JsonObject();row.addProperty("dependencies",count);row.addProperty("resources",resources);row.addProperty("medianMs",times[4]/1e6);row.addProperty("maxMs",times[8]/1e6);row.addProperty("digest",Hashes.sha256(baseline.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));output.add(row);
  }
  Files.writeString(Path.of(System.getenv("RIVET_TASK_DETAIL_LOAD_OUTPUT")),Json.GSON.toJson(output));System.out.println("RIVET_TASK_DETAIL_LOAD_OK tasks=11 stocks=704 scenarios=6");
 }
}
