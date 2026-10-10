package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_TASK_GRAPH_LOAD",matches="true")
class TaskGraphLoadTest {
 @TempDir Path temp;
 @Test void graphAndAccess()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var actor=new CommunityStore.Actor("reader","Reader",false,false);
  db.transaction(()->{try(var q=db.connection().createStatement()){
   q.execute("INSERT INTO community_group_items(id,kind,owner,body) SELECT 'chain-'||n,'task','reader',jsonb_build_object('title','Chain','dependencies',CASE WHEN n<100 THEN jsonb_build_array('chain-'||(n+1)) ELSE '[]'::jsonb END,'comments',repeat('Comment ',1000)) FROM generate_series(1,100) n");
   q.execute("INSERT INTO community_group_items(id,kind,owner,body) SELECT 'root-'||n,'task','reader',jsonb_build_object('dependencies',jsonb_build_array('chain-1')) FROM generate_series(1,10) n");q.execute("INSERT INTO community_task_codes SELECT 'root-'||n,'ROOT'||n FROM generate_series(1,10) n");
   q.execute("INSERT INTO documents(id,section,body) VALUES('graph-group','groups','{\"id\":\"graph-group\",\"owner\":\"owner\",\"status\":\"open\"}')");q.execute("INSERT INTO community_relations SELECT 'graph-group','members',CASE WHEN n=1 THEN 'reader' ELSE 'member-'||n END,to_jsonb(CASE WHEN n=1 THEN 'assistant' ELSE 'member' END) FROM generate_series(1,1000) n");
  }return null;});var output=new JsonArray();for(boolean permissions:new boolean[]{false,true}){
   long[] times=new long[9];for(int i=-1;i<times.length;i++){long started=System.nanoTime();db.transaction(()->{var t=new JsonObject();t.addProperty("id","main");t.addProperty("owner","reader");if(permissions){t.addProperty("group","graph-group");var tasks=new CommunityTasks(db,store);assertTrue(tasks.access(actor,t,false));assertTrue(tasks.access(actor,t,true));}else{db.lock("task-workflow");new TaskWorkflow(db,store).dependencies(t,"ROOT1 ROOT2 ROOT3 ROOT4 ROOT5 ROOT6 ROOT7 ROOT8 ROOT9 ROOT10");assertEquals(10,t.getAsJsonArray("dependencies").size());}return null;});if(i>=0)times[i]=System.nanoTime()-started;}Arrays.sort(times);var row=new JsonObject();row.addProperty("operation",permissions?"read-and-manage-access":"shared-chain");row.addProperty("medianMs",times[4]/1e6);row.addProperty("maxMs",times[8]/1e6);output.add(row);
  }Files.writeString(Path.of(System.getenv("RIVET_TASK_GRAPH_LOAD_OUTPUT")),Json.GSON.toJson(output));System.out.println("RIVET_TASK_GRAPH_LOAD_OK nodes=110 members=1000 scenarios=2");
 }
}
