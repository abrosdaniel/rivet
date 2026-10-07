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
 JsonObject task(CommunityStore store)throws Exception{return store.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"workSave\",\"title\":\"Saved task\",\"description\":\"Keep it\"}")).getAsJsonObject("task");}
 @Test void personalTasksWorkWithoutGroupsAndSurviveDisableEnable()throws Exception{var db=TestDatabase.database(root);var enabled=store(db,"groups","storage");enabled.seen(actor);var saved=task(enabled);var disabled=store(db,"tasks","storage","groups");assertFalse(disabled.activeModules().contains("tasks"));assertThrows(CommunityFailure.class,()->disabled.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"workList\"}")));assertTrue(disabled.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"list\"}")).getAsJsonArray("tasks").isEmpty());var request=Json.parse("{\"section\":\"home\",\"op\":\"workGet\"}");request.addProperty("task",Json.str(saved,"id"));assertEquals("Saved task",Json.str(store(db,"groups","storage").request(actor,request).getAsJsonObject("task"),"title"));assertThrows(CommunityFailure.class,()->new CommunityTasks(db,enabled).bindings());}
 @Test void disabledTrashIsPreservedWhileEnabledTrashIsCleaned()throws Exception{var db=TestDatabase.database(root);for(String section:List.of("board","ideas","groups"))db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb)")){q.setString(1,section);var body=Json.parse("{\"status\":\"deleted\",\"deletedAt\":1}");body.addProperty("section",section);q.setString(2,section);q.setString(3,body.toString());q.executeUpdate();}return null;});var disabled=store(db,"board","tasks","storage");disabled.purgeDeleted(System.currentTimeMillis());db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT id FROM documents ORDER BY id")){assertTrue(rows.next());assertEquals("board",rows.getString(1));assertTrue(rows.next());assertEquals("groups",rows.getString(1));assertFalse(rows.next());}return null;});assertThrows(CommunityFailure.class,()->disabled.request(actor,Json.parse("{\"section\":\"board\",\"op\":\"list\"}")));assertEquals(0,disabled.retentionPreview(System.currentTimeMillis()).get("trash").getAsInt());}
 @Test void disabledGroupsKeepTemporaryGroupsAndRecurringTasksUntouched()throws Exception{
  var db=TestDatabase.database(root);var enabled=store(db);enabled.seen(actor);
  var request=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Keep group\",\"description\":\"Preserve group\",\"type\":\"Команда\"}");var group=enabled.request(actor,request).getAsJsonObject("detail");String id=Json.str(group,"id");
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=jsonb_set(body,'{expiresAt}','1'::jsonb) WHERE id=?")){q.setString(1,id);q.executeUpdate();}return null;});
  var create=Json.parse("{\"section\":\"home\",\"op\":\"workSave\",\"title\":\"Recurring\",\"description\":\"\"}");create.addProperty("group",id);var task=enabled.request(actor,create).getAsJsonObject("task");String taskId=Json.str(task,"id");
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE community_group_items SET body=body||'{\"repeatDays\":1,\"nextRepeatAt\":1}'::jsonb WHERE id=?")){q.setString(1,taskId);q.executeUpdate();}return null;});
  var disabled=store(db,"groups");disabled.reminders(System.currentTimeMillis());
  db.transaction(()->{try(var q=db.connection().prepareStatement("SELECT body->>'status' FROM documents WHERE id=?")){q.setString(1,id);try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals("open",rows.getString(1));}}try(var q=db.connection().prepareStatement("SELECT body->>'nextRepeatAt' FROM community_group_items WHERE id=?")){q.setString(1,taskId);try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals("1",rows.getString(1));}}return null;});
 }

}
