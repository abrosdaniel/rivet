package dev.abros.rivet.core;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres") class FeatureModulesPostgresTest {
 @TempDir Path root;
 CommunityStore.Actor actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",true,true);
 CommunityStore store(PgDatabase db,String... disabled)throws Exception{return new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),ServerSettings.parse(FeatureModulesTest.disabled(disabled)).modules());}
 @Test void archivedTaskCannotBeReadOrChangedThroughGroupItems()throws Exception{
  var db=TestDatabase.database(root);var store=store(db);store.seen(actor);
  var request=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Keep group\",\"description\":\"Preserve group\",\"type\":\"Команда\"}");String group=Json.str(store.request(actor,request).getAsJsonObject("detail"),"id"),task=UUID.randomUUID().toString();
  seedTask(db,task,group);
  for(String op:List.of("plusItemRead","plusItemDelete","plusItemSave","plusTaskStatus")){
   var input=Json.parse("{\"section\":\"groups\",\"kind\":\"task\",\"revision\":1}");input.addProperty("op",op);input.addProperty("id",group);input.addProperty("itemId",task);
   assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->store.request(actor,input)).code());
  }
  assertTaskUnchanged(db,task);
 }
 @Test void archivedTrashIsPreservedRegardlessOfOldFeatureFlags()throws Exception{
  var db=TestDatabase.database(root);for(String section:List.of("board","ideas","groups"))db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb)")){q.setString(1,section);var body=Json.parse("{\"status\":\"deleted\",\"deletedAt\":1}");body.addProperty("section",section);q.setString(2,section);q.setString(3,body.toString());q.executeUpdate();}return null;});
  var disabled=store(db,"board","tasks","storage");disabled.purgeDeleted(System.currentTimeMillis());
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT id FROM documents ORDER BY id")){var ids=new ArrayList<String>();while(rows.next())ids.add(rows.getString(1));assertEquals(List.of("board","groups","ideas"),ids);}return null;});
  assertThrows(CommunityFailure.class,()->disabled.request(actor,Json.parse("{\"section\":\"board\",\"op\":\"list\"}")));assertEquals(0,disabled.retentionPreview(System.currentTimeMillis()).get("trash").getAsInt());
 }
 @Test void archivedTasksDoNotConsumeMarkerQuotaAndMarkerLimitStillApplies()throws Exception{
  var db=TestDatabase.database(root);var store=store(db);store.seen(actor);
  var create=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Markers\",\"description\":\"Archive transition\",\"type\":\"Команда\"}");
  var group=store.request(actor,create).getAsJsonObject("detail");String id=Json.str(group,"id");
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body) VALUES(?,?,?,?::jsonb)")){
   for(String kind:List.of("task","place"))for(int i=0;i<(kind.equals("task")?200:199);i++){
    var body=Json.parse("{\"title\":\"Saved\",\"description\":\"\",\"status\":\"open\"}");body.addProperty("kind",kind);
    if(kind.equals("place"))body.add("location",new CommunityLocation("Saved","minecraft:overworld",i,64,0,true).json());
    q.setString(1,UUID.randomUUID().toString());q.setString(2,id);q.setString(3,kind);q.setString(4,body.toString());q.addBatch();
   }q.executeBatch();}return null;});
  var input=Json.parse("{\"section\":\"groups\",\"op\":\"plusItemSave\",\"kind\":\"place\",\"title\":\"Last allowed marker\",\"description\":\"\"}");input.addProperty("id",id);input.add("revision",group.get("revision"));input.add("location",new CommunityLocation("Last","minecraft:overworld",0,64,0,true).json());
  var saved=store.request(actor,input);assertEquals(200,saved.getAsJsonObject("detail").get("groupItemsCount").getAsInt());
  input.add("revision",saved.getAsJsonObject("detail").get("revision"));assertThrows(CommunityFailure.class,()->store.request(actor,input));
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT kind,count(*) FROM community_group_items GROUP BY kind")){int kinds=0;while(rows.next()){assertEquals(200,rows.getInt(2));kinds++;}assertEquals(2,kinds);}return null;});
 }
 @Test void disabledGroupsAndRetiredRecurrencesRemainUnchanged()throws Exception{
  var db=TestDatabase.database(root);var enabled=store(db);enabled.seen(actor);
  var request=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Keep group\",\"description\":\"Preserve group\",\"type\":\"Команда\"}");String id=Json.str(enabled.request(actor,request).getAsJsonObject("detail"),"id"),task=UUID.randomUUID().toString();seedTask(db,task,id);
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=jsonb_set(body,'{expiresAt}','1'::jsonb) WHERE id=?")){q.setString(1,id);q.executeUpdate();}return null;});
  store(db,"groups").reminders(System.currentTimeMillis());
  db.transaction(()->{try(var q=db.connection().prepareStatement("SELECT body->>'status' FROM documents WHERE id=?")){q.setString(1,id);try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals("open",rows.getString(1));}}return null;});assertTaskUnchanged(db,task);
 }
 private void seedTask(PgDatabase db,String id,String group)throws Exception{db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body) VALUES(?,?,'task','{\"kind\":\"task\",\"title\":\"Archived\",\"status\":\"open\",\"repeatDays\":1,\"nextRepeatAt\":1}'::jsonb)")){q.setString(1,id);q.setString(2,group);q.executeUpdate();}return null;});}
 private void assertTaskUnchanged(PgDatabase db,String id)throws Exception{db.transaction(()->{try(var q=db.connection().prepareStatement("SELECT body->>'nextRepeatAt',revision FROM community_group_items WHERE id=?")){q.setString(1,id);try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals("1",rows.getString(1));assertEquals(1,rows.getLong(2));}}return null;});}
}
