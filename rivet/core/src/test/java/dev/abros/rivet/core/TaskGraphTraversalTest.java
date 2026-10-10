package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class TaskGraphTraversalTest {
 @TempDir Path temp;
 private JsonObject main(){var t=new JsonObject();t.addProperty("id","main");t.addProperty("owner","reader");return t;}
 private void chain(PgDatabase db,int count)throws Exception{db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,kind,owner,body) VALUES(?,'task','reader',?::jsonb)")){for(int i=1;i<=count;i++){var body=new JsonObject();var deps=new JsonArray();if(i<count)deps.add("node-"+(i+1));body.add("dependencies",deps);q.setString(1,"node-"+i);q.setString(2,body.toString());q.addBatch();}q.executeBatch();}return null;});}
 @Test void sharedPathsReadEachNodeOnce()throws Exception{
  var db=TestDatabase.database(temp);chain(db,100);db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("INSERT INTO community_group_items(id,kind,owner,body) SELECT 'root-'||n,'task','reader','{\"dependencies\":[\"node-1\"]}' FROM generate_series(1,10) n");q.execute("INSERT INTO community_task_codes SELECT 'root-'||n,'ROOT'||n FROM generate_series(1,10) n");}return null;});
  var store=new CommunityStore(db,CommunityStore.defaults());var t=main();PerformanceMetrics.clear();PerformanceMetrics.detailed(true);try{
   db.transaction(()->{db.lock("task-workflow");new TaskWorkflow(db,store).dependencies(t,"ROOT1 ROOT2 ROOT3 ROOT4 ROOT5 ROOT6 ROOT7 ROOT8 ROOT9 ROOT10");return null;});assertEquals(110,PerformanceMetrics.snapshot().get("task.graph.query").count());for(int i=0;i<10;i++)assertEquals("root-"+(i+1),t.getAsJsonArray("dependencies").get(i).getAsString());
  }finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }
 @Test void boundaryCyclesAndDeletionAreRecheckedForEveryOperation()throws Exception{
  var db=TestDatabase.database(temp);chain(db,201);db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("INSERT INTO community_task_codes VALUES('node-1','ROOT')");}return null;});var store=new CommunityStore(db,CommunityStore.defaults());var workflow=new TaskWorkflow(db,store);var t=main();
  assertThrows(IllegalArgumentException.class,()->db.transaction(()->{workflow.dependencies(t,"ROOT");return null;}));assertFalse(t.has("dependencies"));
  db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("UPDATE community_group_items SET body='{\"dependencies\":[]}' WHERE id='node-200'");}workflow.dependencies(t,"ROOT");return null;});assertEquals(1,t.getAsJsonArray("dependencies").size());
  db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("UPDATE community_group_items SET body='{\"dependencies\":[\"main\"]}' WHERE id='node-200'");}return null;});assertThrows(IllegalArgumentException.class,()->db.transaction(()->{workflow.dependencies(t,"ROOT");return null;}));
  db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("DELETE FROM community_group_items WHERE id='node-100'");}return null;});assertThrows(CommunityFailure.class,()->db.transaction(()->{workflow.dependencies(t,"ROOT");return null;}));
 }
 @Test void accessUsesCurrentRoleAndPreservesOwnerAdminAndClosedRules()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var tasks=new CommunityTasks(db,store);var reader=new CommunityStore.Actor("reader","Reader",false,false);var owner=new CommunityStore.Actor("owner","Owner",false,false);var admin=new CommunityStore.Actor("admin","Admin",true,true);var t=new JsonObject();t.addProperty("group","team");
  db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("INSERT INTO documents(id,section,body) VALUES('team','groups','{\"owner\":\"owner\",\"status\":\"open\"}')");q.execute("INSERT INTO community_relations VALUES('team','members','reader','\"member\"')");}
   assertTrue(tasks.access(reader,t,false));assertFalse(tasks.access(reader,t,true));assertFalse(tasks.access(owner,t,false));assertTrue(tasks.access(owner,t,true));assertTrue(tasks.access(admin,t,false));assertTrue(tasks.access(admin,t,true));
   try(var q=db.connection().createStatement()){q.execute("UPDATE community_relations SET value='\"assistant\"' WHERE actor='reader'");}assertTrue(tasks.access(reader,t,true));
   try(var q=db.connection().createStatement()){q.execute("DELETE FROM community_relations WHERE actor='reader'");}assertFalse(tasks.access(reader,t,false));assertFalse(tasks.access(reader,t,true));
   try(var q=db.connection().createStatement()){q.execute("UPDATE documents SET body=jsonb_set(body,'{status}','\"closed\"') WHERE id='team'");}assertFalse(tasks.access(admin,t,false));assertFalse(tasks.access(owner,t,true));
   t.addProperty("group","");t.addProperty("owner","reader");assertTrue(tasks.access(reader,t,true));assertFalse(tasks.access(admin,t,false));return null;
  });
 }
}
