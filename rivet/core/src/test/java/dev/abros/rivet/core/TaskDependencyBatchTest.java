package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class TaskDependencyBatchTest {
 @TempDir Path temp;
 @Test void oneReadPreservesOrderMissingEntriesAndPrivateScope()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());db.transaction(()->{try(var q=db.connection().createStatement()){
   q.execute("INSERT INTO community_group_items(id,kind,owner,body) VALUES('a','task','reader','{\"title\":\"A\",\"status\":\"done\"}'),('b','task','reader','{\"title\":\"B\"}'),('foreign','task','other','{\"title\":\"Secret\"}')");q.execute("INSERT INTO community_task_codes VALUES('a','CODEA')");
  }return null;});
  var t=new JsonObject();t.addProperty("id","main");t.addProperty("owner","reader");var ids=new JsonArray();for(String id:new String[]{"b","missing","foreign","a","b"})ids.add(id);t.add("dependencies",ids);
  PerformanceMetrics.clear();PerformanceMetrics.detailed(true);try{
   var rows=db.transaction(()->new TaskWorkflow(db,store).dependencies(t));assertEquals(4,rows.size());assertEquals("B",Json.str(rows.get(0).getAsJsonObject(),"title"));assertFalse(rows.get(0).getAsJsonObject().has("code"));assertFalse(rows.get(0).getAsJsonObject().get("done").getAsBoolean());assertEquals("Зависимая задача удалена",Json.str(rows.get(1).getAsJsonObject(),"title"));assertEquals("CODEA",Json.str(rows.get(2).getAsJsonObject(),"code"));assertTrue(rows.get(2).getAsJsonObject().get("done").getAsBoolean());assertEquals("B",Json.str(rows.get(3).getAsJsonObject(),"title"));assertFalse(rows.toString().contains("Secret"));assertEquals(1,PerformanceMetrics.snapshot().get("task.dependencies.query").count());
   t.add("dependencies",new JsonArray());assertTrue(db.transaction(()->new TaskWorkflow(db,store).dependencies(t)).isEmpty());assertEquals(1,PerformanceMetrics.snapshot().get("task.dependencies.query").count());
  }finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }
 @Test void groupScopeAllowsDifferentOwnersButNotAnotherGroup()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());db.transaction(()->{try(var q=db.connection().createStatement()){
   q.execute("INSERT INTO documents(id,section,body) VALUES('one','groups','{}'),('two','groups','{}')");q.execute("INSERT INTO community_group_items(id,group_id,kind,owner,body) VALUES('same','one','task','other','{\"title\":\"Allowed\"}'),('different','two','task','reader','{\"title\":\"Secret\"}')");
  }return null;});var t=new JsonObject();t.addProperty("group","one");t.addProperty("owner","reader");var ids=new JsonArray();ids.add("different");ids.add("same");t.add("dependencies",ids);var rows=db.transaction(()->new TaskWorkflow(db,store).dependencies(t));assertEquals(1,rows.size());assertEquals("Allowed",Json.str(rows.get(0).getAsJsonObject(),"title"));
 }
}
