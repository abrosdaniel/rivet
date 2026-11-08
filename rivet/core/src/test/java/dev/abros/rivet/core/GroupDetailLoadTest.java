package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dense active-group reads alongside an equally sized archived task set. */
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_GROUP_DETAIL_LOAD",matches="true")
class GroupDetailLoadTest {
 @TempDir Path temp;
 @Test void details()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());
  var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
  var create=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Large group\",\"description\":\"Load fixture\",\"type\":\"Команда\"}");
  String id=Json.str(store.request(actor,create).getAsJsonObject("detail"),"id");
  db.communityTransaction(()->{
   try(var q=db.connection().prepareStatement("INSERT INTO community_relations(document,kind,actor,value) SELECT ?,'members',md5('member-'||n)::uuid::text,to_jsonb('member'::text) FROM generate_series(1,100) n")){q.setString(1,id);q.executeUpdate();}
   for(String kind:List.of("place","task"))try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body) SELECT md5(?||n)::uuid::text,?,?,jsonb_build_object('kind',?,'title',?||n,'description',repeat('Description ',30),'location',jsonb_build_object('name','Place','dimension','minecraft:overworld','x',n,'y',64,'z',n,'membersOnly',true)) FROM generate_series(1,200) n")){
    q.setString(1,kind);q.setString(2,id);q.setString(3,kind);q.setString(4,kind);q.setString(5,kind);q.executeUpdate();
   }
   return null;
  });
  var output=new JsonArray();
  for(int page:new int[]{0,9,39}){
   var query=Json.parse("{\"section\":\"groups\",\"op\":\"detail\"}");query.addProperty("id",id);query.addProperty("page",page);
   var baseline=store.request(actor,query);var detail=baseline.getAsJsonObject("detail");assertEquals(200,detail.get("groupItemsCount").getAsInt());assertEquals(101,detail.get("membersCount").getAsInt());assertEquals(5,detail.getAsJsonArray("groupItems").size());assertTrue(detail.getAsJsonObject("members").size()<=20);
   for(var item:detail.getAsJsonArray("groupItems"))assertEquals("place",Json.str(item.getAsJsonObject(),"kind"));
   long[] times=new long[9];for(int i=0;i<times.length;i++){long start=System.nanoTime();var response=store.request(actor,query);times[i]=System.nanoTime()-start;assertEquals(baseline,response);}Arrays.sort(times);
   var row=new JsonObject();row.addProperty("page",page);row.addProperty("medianMs",times[4]/1e6);row.addProperty("maxMs",times[8]/1e6);row.addProperty("bytes",baseline.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);output.add(row);
  }
  String path=System.getenv("RIVET_GROUP_DETAIL_LOAD_OUTPUT");if(path!=null)Files.writeString(Path.of(path),Json.GSON.toJson(output));
  System.out.println("RIVET_GROUP_DETAIL_LOAD_OK members=101 places=200 archivedTasks=200 scenarios=3 "+output);
 }
}
