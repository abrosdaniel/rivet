package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityPlusTest {
 @TempDir Path temp;CommunityStore store;PgDatabase db;
 CommunityStore.Actor owner=actor("Owner",true),member=actor("Member",false),outsider=actor("Outside",false);
 static CommunityStore.Actor actor(String name,boolean admin){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,admin,admin);}
 @BeforeEach void start()throws Exception{db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());for(var a:List.of(owner,member,outsider))store.seen(a);}
 JsonObject input(String section,String op,String id){var q=new JsonObject();q.addProperty("section",section);q.addProperty("op",op);if(op.equals("cancel"))q.addProperty("reason","Changed plans");q.addProperty("id",id);return q;}
 JsonObject create(String section){var q=input(section,"create","");q.addProperty("title","Example");q.addProperty("description","Description");q.addProperty("type","Команда");q.addProperty("days",7);q.addProperty("capacity",1);q.addProperty("startsAt",Instant.now().plusSeconds(7200).toString());return q;}
 String group()throws Exception{return Json.str(store.request(owner,create("groups")).getAsJsonObject("detail"),"id");}
 void join(String id)throws Exception{var q=input("groups","invite",id);q.addProperty("target",member.id());store.request(owner,q);q=input("groups","invitation",id);q.addProperty("accept",true);store.request(member,q);}
 JsonObject detail(CommunityStore.Actor actor,String section,String id)throws Exception{return store.request(actor,input(section,"detail",id)).getAsJsonObject("detail");}




 @Test void privatePlaceReadDoesNotWaitForGlobalMutationLockOrReplayRevokedAccess()throws Exception{
  String group=group();join(group);
  var save=input("groups","plusItemSave",group);save.addProperty("kind","place");save.addProperty("title","Private");save.addProperty("description","Only members");save.add("location",new CommunityLocation("Private","minecraft:overworld",1,64,2,true).json());
  String item=Json.str(store.request(owner,save),"changedId");var read=input("groups","plusItemRead",group);read.addProperty("itemId",item);read.addProperty("operationId",UUID.randomUUID().toString());read.addProperty("issuedAt",System.currentTimeMillis());
  try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
   db.transaction(()->{db.lock("community:groups");var future=executor.submit(()->store.request(member,read));assertEquals("Private",Json.str(future.get(2,java.util.concurrent.TimeUnit.SECONDS).getAsJsonObject("item"),"title"));return null;});
  }
  assertThrows(CommunityFailure.class,()->store.request(outsider,read));
  var remove=input("groups","plusRemoveMember",group);remove.addProperty("target",member.id());remove.addProperty("reason","Removed");store.request(owner,remove);
  assertThrows(CommunityFailure.class,()->store.request(member,read),"A reused operation id cannot restore revoked access");
 }

 @Test void ignoreDoesNotSuppressSystemNoticesAndProfileWritesAreOwnOnly()throws Exception{
  var ignore=input("home","plusIgnore","");ignore.addProperty("target",owner.name());store.request(member,ignore);String group=group();var invite=input("groups","invite",group);invite.addProperty("target",member.id());assertThrows(CommunityFailure.class,()->store.request(owner,invite));store.externalNotice(member.id(),"System");assertEquals(1,store.unread(member.id()));
  var profile=input("home","plusProfileSave",owner.id());profile.addProperty("about","Hello");assertThrows(CommunityFailure.class,()->store.request(member,profile));profile.addProperty("id",member.id());store.request(member,profile);assertEquals("Hello",Json.str(store.request(owner,input("home","plusProfile",member.id())).getAsJsonObject("profile"),"about"));
 }


 @Test void groupRemovalProtectsLeaderAndNormalizedMembersSurviveEdits()throws Exception{
  String group=group();join(group);var remove=input("groups","plusRemoveMember",group);remove.addProperty("target",owner.id());remove.addProperty("reason","Reason");assertThrows(CommunityFailure.class,()->store.request(owner,remove));remove.addProperty("target",member.id());assertThrows(CommunityFailure.class,()->store.request(member,remove));store.request(owner,remove);assertFalse(detail(owner,"groups",group).getAsJsonObject("members").has(member.id()));
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT body ? 'members' FROM documents WHERE section='groups'")){assertTrue(rows.next());assertFalse(rows.getBoolean(1));}return null;});
 }


 @Test void existingJsonCollectionsMigrateWithoutLosingData()throws Exception{
  class Rollback extends RuntimeException{}
  assertThrows(Rollback.class,()->db.transaction(()->{
   try(var q=db.connection().createStatement()){q.execute("DROP VIEW community_documents");q.execute("DROP TABLE api_key_audit,api_keys,api_apps,chat_history,community_task_stocks,community_task_codes,community_relations,community_profile,community_ignores,community_event_preferences,community_group_items");q.execute("ALTER TABLE skin_library DROP COLUMN position");q.execute("ALTER TABLE notices DROP COLUMN expires_at CASCADE");q.execute("DROP TABLE auth_backup_codes,auth_factors");q.execute("ALTER TABLE skin_profiles DROP COLUMN undo_active,DROP COLUMN undo_slim,DROP COLUMN undo_available");q.execute("DELETE FROM schema_versions WHERE version>=4");q.execute("DROP SEQUENCE community_task_code_sequence");}
   var old=new JsonObject();old.addProperty("id","old-record");old.addProperty("section","events");var participants=new JsonObject();participants.addProperty(member.id(),member.name());participants.addProperty(owner.id(),owner.name());old.add("participants",participants);var order=new JsonArray();order.add(owner.id());order.add(member.id());old.add("participantOrder",order);var votes=new JsonObject();var choices=new JsonArray();choices.add(1);votes.add(member.id(),choices);old.add("votes",votes);
   try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES('old-record','events',?::jsonb)")){q.setString(1,old.toString());q.executeUpdate();}DatabaseMigrations.apply(db);
   var restored=store.get("old-record");assertEquals(List.of(owner.id(),member.id()),new ArrayList<>(restored.getAsJsonObject("participants").keySet()));assertEquals(votes,restored.getAsJsonObject("votes"));throw new Rollback();
  }));
 }



 @Test void quickInviteActionIsRecipientScopedAndReplaySafe()throws Exception{String group=group();var invite=input("groups","invite",group);invite.addProperty("target",member.id());store.request(owner,invite);String notice=latestNotice(member.id());var action=input("groups","noticeAction",notice);action.addProperty("action","community");action=MenuCommands.prepare(action,System.currentTimeMillis());var command=action;assertThrows(CommunityFailure.class,()->store.request(outsider,command));assertTrue(store.request(member,action).get("noticeActionApplied").getAsBoolean());action.addProperty("request","retry");assertTrue(store.request(member,action).get("noticeActionApplied").getAsBoolean());assertTrue(detail(owner,"groups",group).getAsJsonObject("members").has(member.id()));}




 String latestNotice(String actor)throws Exception{return db.communityTransaction(()->{try(var q=db.connection().prepareStatement("SELECT id FROM notices WHERE recipient=? ORDER BY id DESC LIMIT 1")){q.setString(1,actor);try(var rs=q.executeQuery()){rs.next();return rs.getString(1);}}});}
}
