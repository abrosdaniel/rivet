package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_TASK_LOAD",matches="true")
class TaskLargeReadTest {
 @TempDir Path temp;
 @Test void lists()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var actor=new CommunityStore.Actor("reader","Reader",false,false);
  db.transaction(()->{try(var s=db.connection().createStatement()){
   s.execute("INSERT INTO documents(id,section,body) VALUES('task-group','groups','{\"id\":\"task-group\",\"section\":\"groups\",\"title\":\"Group\",\"owner\":\"reader\",\"status\":\"open\"}')");
   s.execute("INSERT INTO community_relations SELECT 'task-group','members',CASE WHEN n=1 THEN 'reader' ELSE 'member-'||n END,to_jsonb('member'::text) FROM generate_series(1,1000) n");
   s.execute("INSERT INTO community_group_items(id,group_id,kind,owner,body) SELECT '00000000-0000-0000-0000-'||lpad(n::text,12,'0'),CASE WHEN n%2=0 THEN 'task-group' ELSE NULL END,'task','reader',jsonb_build_object('title',CASE WHEN n%101=0 THEN 'Needle ' ELSE 'Task ' END||n,'description',repeat('Details ',100),'status',CASE WHEN n%3=0 THEN 'done' ELSE 'open' END,'comments',(SELECT jsonb_agg(jsonb_build_object('author','Member','text',repeat('Comment ',60))) FROM generate_series(1,40)),'history',(SELECT jsonb_agg(jsonb_build_object('op','update','text',repeat('History ',30))) FROM generate_series(1,20)),'subtasks',jsonb_build_array(jsonb_build_object('title','Stage','done',true)),'resources','[]'::jsonb) FROM generate_series(1,6000) n");
   s.execute("INSERT INTO community_task_codes SELECT id,'CODE'||right(id,12) FROM community_group_items");s.execute("ANALYZE community_group_items");s.execute("ANALYZE community_task_codes");s.execute("ANALYZE community_relations");
  }return null;});
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT octet_length(body::text),octet_length((body - ARRAY['comments','stocks','history','dependencies','reservations']::text[])::text) FROM community_group_items ORDER BY id LIMIT 1")){assertTrue(rows.next());assertTrue(rows.getInt(2)<rows.getInt(1));System.out.println("RIVET_TASK_PAYLOAD fullBytes="+rows.getInt(1)+" listBytes="+rows.getInt(2));}return null;});
  var plans=new JsonArray();var out=new JsonArray();for(String group:List.of("","task-group"))for(String query:List.of("","Needle","CODE0000000059")){
   var q=new JsonObject();q.addProperty("section","home");q.addProperty("op","workList");q.addProperty("group",group);q.addProperty("query",query);q.addProperty("status","open");var expected=store.request(actor,q);assertFalse(expected.getAsJsonArray("tasks").isEmpty());var times=new long[7];for(int i=0;i<times.length;i++){long start=System.nanoTime();var result=store.request(actor,q);times[i]=System.nanoTime()-start;assertEquals(expected,result);}Arrays.sort(times);var row=new JsonObject();row.addProperty("group",!group.isEmpty());row.addProperty("query",query);row.addProperty("medianMs",times[3]/1e6);row.addProperty("maxMs",times[6]/1e6);row.addProperty("digest",Hashes.sha256(expected.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));out.add(row);
   db.transaction(()->{try(var statement=db.connection().prepareStatement("EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) "+CommunityTasks.LIST_SQL)){statement.setString(1,group);statement.setString(2,actor.id());statement.setString(3,group);statement.setString(4,"");statement.setString(5,"open");statement.setString(6,"open");statement.setString(7,"open");statement.setString(8,query);try(var rs=statement.executeQuery()){assertTrue(rs.next());var plan=new JsonObject();plan.addProperty("group",!group.isEmpty());plan.addProperty("query",query);plan.add("plan",JsonParser.parseString(rs.getString(1)));plans.add(plan);}}return null;});
  }
  Files.writeString(Path.of(System.getenv("RIVET_TASK_LOAD_OUTPUT")+".plans.json"),Json.GSON.toJson(plans));
  Files.writeString(Path.of(System.getenv("RIVET_TASK_LOAD_OUTPUT")),Json.GSON.toJson(out));System.out.println("RIVET_TASK_LOAD_OK tasks=6000 members=1000 scenarios=6");
 }
}
