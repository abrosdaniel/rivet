package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("postgres")
class CommunityStoreTest {
 @TempDir Path world;CommunityStore db;
 CommunityStore.Actor owner=actor("Owner",false),alice=actor("Alice",false),bob=actor("Bob",false),admin=actor("Admin",true);
 static CommunityStore.Actor actor(String name,boolean admin){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,admin,admin);}
 @BeforeEach void start()throws Exception{db=new CommunityStore(TestDatabase.database(world),CommunityStore.defaults());for(var a:List.of(owner,alice,bob,admin))db.seen(a);}
 @AfterEach void close()throws Exception{db.close();}
 JsonObject input(String section,String op,String id){var j=new JsonObject();j.addProperty("section",section);j.addProperty("op",op);j.addProperty("id",id);return j;}
 JsonObject create(String section)throws Exception{var j=input(section,"create","");j.addProperty("title","Example");j.addProperty("description","Description");j.addProperty("days",14);j.addProperty("type","Команда");j.addProperty("startsAt",Instant.now().plusSeconds(120).toString());j.addProperty("endsAt",Instant.now().plusSeconds(60).toString());j.addProperty("capacity",1);var options=new JsonArray();options.add("One");options.add("Two");j.add("options",options);return db.request(Set.of("events","polls").contains(section)?admin:owner,j).getAsJsonObject("detail");}
 String id(JsonObject j){return Json.str(j,"id");}
 JsonObject detail(String section,String id,CommunityStore.Actor a)throws Exception{return db.request(a,input(section,"detail",id)).getAsJsonObject("detail");}
 @Test void membershipRoleSurvivesPagingAndApplicationResultIsExplicit()throws Exception{
  var group=create("groups");String id=id(group);
  assertEquals("leader",detail("groups",id,owner).get("myRole").getAsString());assertEquals("",detail("groups",id,alice).get("myRole").getAsString());
  for(boolean accepted:List.of(false,true)){
   var apply=input("groups","apply",id);apply.addProperty("text","I would like to join");db.request(alice,apply);
   var decision=input("groups","application",id);decision.addProperty("target",alice.id());decision.addProperty("accept",accepted);db.request(owner,decision);
   String key="rivet.flow."+(accepted?"applicationAccepted":"applicationDeclined");
   var notices=db.request(alice,input("notifications","list","")).getAsJsonArray("entries");assertTrue(notices.toString().contains(key));
  }
  var later=input("groups","detail",id);later.addProperty("page",1);var view=db.request(alice,later).getAsJsonObject("detail");assertEquals("member",view.get("myRole").getAsString());assertTrue(view.getAsJsonObject("members").isEmpty());
  var role=input("groups","role",id);role.addProperty("target",alice.id());role.addProperty("role","assistant");db.request(owner,role);assertEquals("assistant",detail("groups",id,alice).get("myRole").getAsString());
  db.request(alice,input("groups","leave",id));view=detail("groups",id,alice);assertEquals("",view.get("myRole").getAsString());assertFalse(view.get("manage").getAsBoolean());assertTrue(view.getAsJsonArray("groupItems").isEmpty());
 }
 @Test void trashMetadataUsesConfiguredRetentionWithoutPersistingDerivedDeadline()throws Exception{
  for(int days:List.of(1,30,365)){
   owner=actor("Owner"+days,false);db.seen(owner);
   db.retention(30,days,1000);var created=create("groups");String key=id(created);
   var deleted=db.request(owner,input("groups","delete",key));var row=deleted.getAsJsonObject("detail");
   assertEquals(days,deleted.get("trashDays").getAsInt());assertEquals(row.get("deletedAt").getAsLong()+days*86400000L,row.get("restoreUntil").getAsLong());
   assertFalse(TestDatabase.database(world).communityTransaction(()->db.get(key).has("restoreUntil")));
   var listed=db.request(owner,input("groups","list",""));assertEquals(days,listed.get("trashDays").getAsInt());
   db.retention(30,2,1000);var refreshed=detail("groups",key,owner);assertEquals(refreshed.get("deletedAt").getAsLong()+2*86400000L,refreshed.get("restoreUntil").getAsLong());
  }
 }
 @Test void restoreActionAndMutationUseSameConfiguredWindow()throws Exception{
  var group=create("groups");String key=id(group);db.request(owner,input("groups","delete",key));
  var database=TestDatabase.database(world);
  database.communityTransaction(()->{var saved=db.get(key);saved.addProperty("deletedAt",System.currentTimeMillis()-40L*86400000);db.put(saved);return null;});
  db.retention(30,7,1000);
  assertFalse(detail("groups",key,owner).getAsJsonArray("actions").contains(new JsonPrimitive("restore")));
  assertThrows(CommunityFailure.class,()->db.request(owner,input("groups","restore",key)));
  db.retention(30,60,1000);
  assertTrue(detail("groups",key,owner).getAsJsonArray("actions").contains(new JsonPrimitive("restore")));
  var restored=db.request(owner,input("groups","restore",key)).getAsJsonObject("detail");assertEquals("open",Json.str(restored,"status"));
 }
 @Test void removedWorkspaceOperationsAreRejectedBeforeReplay()throws Exception{
  for(String op:List.of("spaceList","spaceGet","spaceSave","spaceDelete","spaceLink","spaceFavorite","spaceFavorites")){
   var q=input("home",op,"");q.addProperty("operationId",UUID.randomUUID().toString());q.addProperty("issuedAt",System.currentTimeMillis());
   assertThrows(IllegalArgumentException.class,()->db.request(owner,q));
  }
 }
 @Test void groupRefreshWithoutRevisionCanBeUpdatedAndRejectsStaleChanges()throws Exception{
  var group=create("groups");String groupId=id(group);var database=TestDatabase.database(world);
  database.transaction(()->{try(var q=database.connection().prepareStatement("UPDATE documents SET body=body-'revision' WHERE id=?")){q.setString(1,groupId);q.executeUpdate();}return null;});
  var read=input("groups","detail",groupId);read.addProperty("action","community");read.addProperty("cursor","");
  var refreshed=db.request(owner,read).getAsJsonObject("detail");assertEquals(0,refreshed.get("revision").getAsLong());
  assertTrue(refreshed.getAsJsonObject("members").has(owner.id()));
  var update=command(input("groups","recruiting",groupId));update.add("revision",refreshed.get("revision"));
  var changed=db.request(owner,update).getAsJsonObject("detail");assertEquals(1,changed.get("revision").getAsLong());assertFalse(changed.get("recruiting").getAsBoolean());
  var stale=command(input("groups","recruiting",groupId));stale.addProperty("revision",0);
  assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(owner,stale)).code());
  assertEquals(1,detail("groups",groupId,owner).get("revision").getAsLong());
 }
 @Test void groupRefreshWithNullRevisionReturnsInitialRevision()throws Exception{
  var group=create("groups");var database=TestDatabase.database(world);
  database.transaction(()->{try(var q=database.connection().prepareStatement("UPDATE documents SET body=jsonb_set(body,'{revision}','null'::jsonb) WHERE id=?")){q.setString(1,id(group));q.executeUpdate();}return null;});
  assertEquals(0,detail("groups",id(group),owner).get("revision").getAsLong());
 }
 JsonObject mutate(String section,String op,JsonObject item){var q=command(input(section,op,id(item)));q.add("revision",item.get("revision"));return q;}


 @Test void applicationsAndInvitationsCanBeWithdrawnWithPrivateQueues()throws Exception{
  var group=create("groups");var apply=input("groups","apply",id(group));apply.addProperty("text","Hello");db.request(alice,apply);
  var withdraw=mutate("groups","withdrawApplication",detail("groups",id(group),alice));assertThrows(CommunityFailure.class,()->db.request(bob,withdraw));db.request(alice,withdraw);
  assertTrue(detail("groups",id(group),owner).getAsJsonObject("applications").isEmpty());
  var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(owner,invite);
  assertTrue(detail("groups",id(group),owner).getAsJsonObject("invitations").has(alice.id()));assertTrue(detail("groups",id(group),bob).getAsJsonObject("invitations").isEmpty());
  var revoke=mutate("groups","revokeInvitation",detail("groups",id(group),owner));revoke.addProperty("target",alice.id());assertThrows(CommunityFailure.class,()->db.request(bob,revoke));db.request(owner,revoke);
  var accept=input("groups","invitation",id(group));accept.addProperty("accept",true);assertThrows(CommunityFailure.class,()->db.request(alice,accept));
 }
 @Test void assistantCannotEditDeleteOrTransferOwnership()throws Exception{
  var group=create("groups");var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(owner,invite);var accept=input("groups","invitation",id(group));accept.addProperty("accept",true);db.request(alice,accept);
  var role=input("groups","role",id(group));role.addProperty("target",alice.id());role.addProperty("role","assistant");db.request(owner,role);
  var current=detail("groups",id(group),alice);for(String op:List.of("edit","delete","role"))assertThrows(CommunityFailure.class,()->db.request(alice,mutate("groups",op,current)));
  assertTrue(current.getAsJsonArray("actions").contains(new JsonPrimitive("invite")));
 }


 @Test void unauthorizedChangesRollBack()throws Exception{var p=create("groups");assertThrows(IllegalArgumentException.class,()->db.request(alice,input("groups","delete",id(p))));assertEquals("open",Json.str(detail("groups",id(p),owner),"status"));assertThrows(IllegalArgumentException.class,()->db.request(alice,input("notifications","delete",id(p))));assertEquals(0,db.unread(owner.id()));}


 @Test void disablingASectionBlocksRequests()throws Exception{var p=create("groups");var c=db.configuration();c.getAsJsonArray("sections").remove(new JsonPrimitive("groups"));db.configure(c);assertThrows(IllegalArgumentException.class,()->detail("groups",id(p),owner));}


 @Test void groupMembershipRequiresAcceptanceAndLeaderCannotLeave()throws Exception{var p=create("groups");var q=input("groups","apply",id(p));q.addProperty("text","Join please");db.request(alice,q);assertFalse(detail("groups",id(p),alice).get("isMember").getAsBoolean());var accept=input("groups","application",id(p));accept.addProperty("target",alice.id());accept.addProperty("accept",true);assertThrows(IllegalArgumentException.class,()->db.request(bob,accept));db.request(owner,accept);assertTrue(detail("groups",id(p),alice).get("isMember").getAsBoolean());assertThrows(IllegalArgumentException.class,()->db.request(owner,input("groups","leave",id(p))));}
 @Test void nonAdminCannotCreatePollsOrEvents()throws Exception{for(String section:List.of("polls","events")){var q=input(section,"create","");q.addProperty("title","Bad");q.addProperty("description","Bad");assertThrows(IllegalArgumentException.class,()->db.request(alice,q));}}


 @Test void readingGroupedObjectKeepsOtherSectionAndRecipientUnread()throws Exception{var sql=TestDatabase.database(world);sql.communityTransaction(()->{for(String section:List.of("board","board","polls")){var body=new JsonObject();body.addProperty("title",UUID.randomUUID().toString());body.addProperty("target","same-object");body.addProperty("section",section);body.addProperty("at",System.currentTimeMillis());try(var q=sql.connection().prepareStatement("INSERT INTO notices(recipient,body) VALUES(?,?::jsonb)")){q.setString(1,alice.id());q.setString(2,body.toString());q.executeUpdate();}}return null;});db.externalNotice(bob.id(),"Other recipient");var entries=db.request(alice,input("notifications","list","")).getAsJsonArray("entries");var board=entries.asList().stream().map(e->e.getAsJsonObject()).filter(e->Json.str(e,"section").equals("board")).findFirst().orElseThrow();db.request(alice,input("notifications","read",Json.str(board,"id")));assertEquals(1,db.unread(alice.id()));assertEquals(1,db.unread(bob.id()));}
 @Test void readingOneUntargetedNotificationDoesNotReadOthers()throws Exception{db.externalNotice(alice.id(),"First");db.externalNotice(alice.id(),"Second");var entries=db.request(alice,input("notifications","list","")).getAsJsonArray("entries");assertEquals(2,db.unread(alice.id()));db.request(alice,input("notifications","read",Json.str(entries.get(0).getAsJsonObject(),"id")));assertEquals(1,db.unread(alice.id()));}
 @Test void notificationsBelongToRecipient()throws Exception{db.externalNotice(alice.id(),"Private");assertEquals(1,db.request(alice,input("notifications","list","")).getAsJsonArray("entries").size());assertTrue(db.request(bob,input("notifications","list","")).getAsJsonArray("entries").isEmpty());db.request(bob,input("notifications","read",""));assertEquals(1,db.unread(alice.id()));db.request(alice,input("notifications","read",""));assertEquals(0,db.unread(alice.id()));}
 @Test void reportsUseDatabaseAndNotifyOnReply()throws Exception{var reports=new CommunityReports(db);var q=new JsonObject();q.addProperty("message","Help");String ticket=reports.submit(UUID.fromString(alice.id()),"Alice",q,System.currentTimeMillis());reports.reply(ticket,"Admin","Fixed",true);assertEquals(1,db.unread(alice.id()));assertTrue(reports.list(UUID.fromString(bob.id()),0).isEmpty());assertFalse(Files.exists(world.resolve("rivet/reports")));}
 @Test void playerSearchAndAuditAreInDatabase()throws Exception{assertEquals(1,db.people("ali",0).size());db.audit("Admin","test","done");assertEquals(1,db.records("audit").size());assertFalse(Files.exists(world.resolve("rivet/server.db")));assertFalse(Files.exists(world.resolve("rivet/admin-log.json")));}


 @Test void popupPreferencesDoNotDeleteNotifications()throws Exception{
  var q=input("home","preferences","");var prefs=new JsonObject();var muted=new JsonArray();muted.add("help");prefs.add("muted",muted);q.add("preferences",prefs);db.request(owner,q);
  db.externalNotice(owner.id(),"Saved message");
  assertEquals(1,db.unread(owner.id()));assertEquals(0,db.popupSequence(owner.id()));
  var summary=db.notificationSummary(List.of(UUID.fromString(owner.id()))).get(UUID.fromString(owner.id()));assertEquals(1,summary.unread());assertEquals(0,summary.sequence());
 }

 @Test void disabledSectionCannotBeReadThroughAnotherSection()throws Exception{var p=create("groups");var config=db.configuration();config.getAsJsonArray("sections").remove(new JsonPrimitive("groups"));db.configure(config);assertThrows(IllegalArgumentException.class,()->db.request(owner,input("home","detail",id(p))));}


 @Test void groupMembershipLimitIsEnforcedOnInvitation()throws Exception{var config=db.configuration();config.addProperty("maxMemberships",1);db.configure(config);var first=create("groups");var secondInput=input("groups","create","");secondInput.addProperty("title","Second");secondInput.addProperty("description","Different");secondInput.addProperty("type","Команда");var second=db.request(bob,secondInput).getAsJsonObject("detail");for(var group:List.of(first,second)){var who=id(group).equals(id(first))?owner:bob;var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(who,invite);}var accept=input("groups","invitation",id(first));accept.addProperty("accept",true);db.request(alice,accept);accept.addProperty("id",id(second));assertThrows(IllegalArgumentException.class,()->db.request(alice,accept));assertTrue(detail("groups",id(second),alice).getAsJsonObject("invitations").has(alice.id()));}
 @Test void failedTransactionRollsBackAllWrites()throws Exception{assertThrows(Exception.class,()->db.transaction(()->{db.record("test","one",new JsonObject());throw new Exception("stop");}));assertNull(db.record("test","one"));}

 @Test void applicationAndInvitationFlagsSurvivePaging()throws Exception{
  var group=create("groups");var apply=input("groups","apply",id(group));apply.addProperty("text","Hello");db.request(alice,apply);
  var invite=input("groups","invite",id(group));invite.addProperty("target",bob.id());db.request(owner,invite);
  var later=input("groups","detail",id(group));later.addProperty("page",1);var view=db.request(alice,later).getAsJsonObject("detail");
  assertTrue(view.get("hasApplication").getAsBoolean());assertTrue(view.getAsJsonObject("applications").isEmpty());
  view=db.request(bob,later).getAsJsonObject("detail");assertTrue(view.get("hasInvitation").getAsBoolean());assertTrue(view.getAsJsonObject("invitations").isEmpty());
 }


 JsonObject command(JsonObject j){j.addProperty("operationId",UUID.randomUUID().toString());j.addProperty("issuedAt",System.currentTimeMillis());return j;}
 @Test void duplicateMutationKeepsResultAndDoesNotToggleTwice()throws Exception{
  var p=create("groups");var q=mutate("groups","recruiting",p);var first=db.request(owner,q);q.addProperty("request","retry");var second=db.request(owner,q);
  assertEquals(first.get("detail"),second.get("detail"));assertTrue(second.get("replayed").getAsBoolean());assertFalse(detail("groups",id(p),owner).get("recruiting").getAsBoolean());
  q.addProperty("id","different");assertThrows(CommunityFailure.class,()->db.request(owner,q));
 }
 @Test void replayDoesNotLeakAfterPermissionChange()throws Exception{
  var p=create("groups");var q=mutate("groups","recruiting",p);db.request(owner,q);var changed=new CommunityStore.Actor(owner.id(),owner.name(),true,true);
  assertThrows(CommunityFailure.class,()->db.request(changed,q));assertFalse(detail("groups",id(p),owner).get("recruiting").getAsBoolean());
 }
 @Test void staleRevisionCannotOverwriteAndHistoryIsPublicOnly()throws Exception{
  var group=create("groups");var edit=mutate("groups","edit",group);edit.addProperty("title","Public title");edit.addProperty("description","Public answer");edit.addProperty("type","Команда");
  var updated=db.request(owner,edit).getAsJsonObject("detail");var stale=command(edit.deepCopy());stale.addProperty("description","Overwrite");
  assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(owner,stale)).code());
  assertEquals("Public answer",Json.str(detail("groups",id(group),alice),"description"));assertEquals(2,updated.getAsJsonArray("history").size());
  var apply=input("groups","apply",id(group));apply.addProperty("text","PRIVATE");db.request(alice,apply);
  assertFalse(detail("groups",id(group),bob).getAsJsonArray("history").toString().contains("PRIVATE"));
 }
 @Test void chronologicalSortingPagesAcrossAllRecordsAndBindsItsCursor()throws Exception{
  var expected=new ArrayList<String>();for(int n=0;n<12;n++){var q=input("groups","create","");q.addProperty("title","Sorted "+n);q.addProperty("description","description");q.addProperty("type","Команда");expected.add(id(db.request(actor("Creator"+n,true),q).getAsJsonObject("detail")));}
  var q=input("groups","list","");q.addProperty("sort","oldest");var first=db.request(alice,q);var rows=first.getAsJsonArray("entries");assertEquals(10,rows.size());for(int n=0;n<10;n++)assertEquals(expected.get(n),id(rows.get(n).getAsJsonObject()));q.addProperty("cursor",Json.str(first,"nextCursor"));var tail=db.request(alice,q).getAsJsonArray("entries");assertEquals(2,tail.size());assertEquals(expected.get(10),id(tail.get(0).getAsJsonObject()));q.addProperty("sort","recent");assertThrows(IllegalArgumentException.class,()->db.request(alice,q));q.remove("cursor");assertEquals(expected.get(11),id(db.request(alice,q).getAsJsonArray("entries").get(0).getAsJsonObject()));
 }
 @Test void archiveIsSeparateAndCursorSurvivesInsert()throws Exception{
  for(int n=0;n<12;n++){var j=input("groups","create","");j.addProperty("title","Idea "+n);j.addProperty("description","description");j.addProperty("type","Команда");db.request(actor("Creator"+n,true),j);}
  var first=db.request(alice,input("groups","list",""));assertEquals(10,first.getAsJsonArray("entries").size());String cursor=Json.str(first,"nextCursor");assertFalse(cursor.isEmpty());
  var extra=input("groups","create","");extra.addProperty("title","New arrival");extra.addProperty("description","description");extra.addProperty("type","Команда");db.request(admin,extra);
  var next=input("groups","list","");next.addProperty("cursor",cursor);var second=db.request(alice,next);assertEquals(2,second.getAsJsonArray("entries").size());var ids=new HashSet<String>();first.getAsJsonArray("entries").forEach(e->ids.add(id(e.getAsJsonObject())));second.getAsJsonArray("entries").forEach(e->assertTrue(ids.add(id(e.getAsJsonObject()))));assertThrows(CommunityFailure.class,()->db.request(bob,next));
  var group=create("groups");var hide=input("groups","hide",id(group));hide.addProperty("text","Moderated");db.request(admin,hide);
  var mine=input("groups","list","");mine.addProperty("mine",true);assertTrue(db.request(owner,mine).getAsJsonArray("entries").isEmpty());
  mine.addProperty("archive",true);assertEquals(1,db.request(owner,mine).getAsJsonArray("entries").size());
  assertTrue(db.request(alice,mine).getAsJsonArray("entries").isEmpty());
 }
 @Test void failedReceiptRollsBackAndCanRetry()throws Exception{
  var group=create("groups");var edit=mutate("groups","edit",group);edit.addProperty("title","Updated");edit.addProperty("description","Answer");edit.addProperty("type","Команда");
  assertThrows(CommunityFailure.class,()->db.request(alice,edit));assertNotNull(db.request(owner,edit));
  assertEquals("Answer",Json.str(detail("groups",id(group),owner),"description"));
 }
 @Test void trashHidesRecordsAndRestoresDependents()throws Exception{
  var item=create("groups");String key=id(item);var apply=input("groups","apply",key);apply.addProperty("text","Keep this response");db.request(alice,apply);
  db.request(owner,input("groups","delete",key));assertEquals("deleted",Json.str(detail("groups",key,owner),"status"));
  assertTrue(db.request(alice,input("groups","list","")).getAsJsonArray("entries").isEmpty());
  assertThrows(IllegalArgumentException.class,()->detail("groups",key,alice));assertThrows(CommunityFailure.class,()->db.request(alice,input("groups","restore",key)));
  var trash=input("groups","list","");trash.addProperty("trash",true);assertEquals(1,db.request(owner,trash).getAsJsonArray("entries").size());assertTrue(db.request(alice,trash).getAsJsonArray("entries").isEmpty());
  db.request(owner,input("groups","restore",key));var restored=detail("groups",key,alice);assertTrue(restored.get("hasApplication").getAsBoolean());
  assertTrue(detail("groups",key,owner).toString().contains("Keep this response"));assertFalse(detail("groups",key,bob).toString().contains("Keep this response"));
 }

 @Test void notificationReadsAndPreferencesPublishPrivateCommittedChanges()throws Exception{
  db.externalNotice(owner.id(),"Saved message");
  var notices=db.request(owner,input("notifications","list","")).getAsJsonArray("entries");assertFalse(notices.isEmpty());
  db.acknowledgeChanges(db.pendingChanges());
  var read=input("notifications","read",id(notices.get(0).getAsJsonObject()));read.addProperty("exact",true);
  assertThrows(Exception.class,()->db.transaction(()->{db.request(owner,read);throw new Exception("rollback");}));
  assertTrue(db.pendingChanges().isEmpty());assertEquals(1,db.unread(owner.id()));
  db.request(owner,read);assertEquals(0,db.unread(owner.id()));
  var events=db.pendingChanges();assertEquals(1,events.size());assertEquals("notifications",Json.str(events.get(0).getAsJsonObject(),"topic"));assertEquals(owner.id(),Json.str(events.get(0).getAsJsonObject(),"recipient"));
  db.acknowledgeChanges(events);
  var preferences=input("home","preferences","");var body=new JsonObject();body.add("muted",new JsonArray());preferences.add("preferences",body);db.request(owner,preferences);
  events=db.pendingChanges();assertEquals(1,events.size());assertEquals(owner.id(),Json.str(events.get(0).getAsJsonObject(),"recipient"));
 }
 @Test void transactionalEventsRollbackAndAcknowledgeExactlyTheBatch()throws Exception{
  var before=db.pendingChanges();db.acknowledgeChanges(before);assertTrue(db.pendingChanges().isEmpty());
  assertThrows(Exception.class,()->db.transaction(()->{create("groups");throw new Exception("rollback");}));assertTrue(db.pendingChanges().isEmpty());
  create("groups");var batch=db.pendingChanges();assertFalse(batch.isEmpty());db.externalNotice(alice.id(),"After batch");db.acknowledgeChanges(batch);
  var remaining=db.pendingChanges();assertEquals(1,remaining.size());assertEquals("notifications",Json.str(remaining.get(0).getAsJsonObject(),"topic"));assertEquals(alice.id(),Json.str(remaining.get(0).getAsJsonObject(),"recipient"));
 }
 @Test void pendingEventsAndPrivateSnapshotsRecoverWithANewDatabasePool()throws Exception{
  db.acknowledgeChanges(db.pendingChanges());db.externalNotice(owner.id(),"Saved private notice");db.externalNotice(alice.id(),"Other private notice");
  var before=db.pendingChanges();assertEquals(2,before.size());
  try(var connection=new PgDatabase(TestDatabase.settings())){
   assertEquals(0,connection.communityRevision());var schedule=new ChangeDeliverySchedule();assertTrue(schedule.due(connection.communityRevision(),1));
   var restored=new CommunityStore(connection,CommunityStore.defaults());assertEquals(before,restored.pendingChanges());
   var page=restored.request(owner,input("notifications","list",""));assertTrue(page.toString().contains("Saved private notice"));assertFalse(page.toString().contains("Other private notice"));
   restored.acknowledgeChanges(before);assertTrue(db.pendingChanges().isEmpty());assertEquals(1,restored.unread(owner.id()));
  }
 }

 @Test void searchFindsGroupMarkersOnlyForCurrentAudience()throws Exception{
  var group=create("groups");String groupId=id(group);
  var marker=input("groups","plusItemSave",groupId);marker.addProperty("kind","place");marker.addProperty("title","Hidden observatory");marker.addProperty("description","Private place");marker.add("location",new CommunityLocation("Hidden observatory","minecraft:overworld",123,64,456,true).json());db.request(owner,marker);
  var search=input("home","globalSearch","");search.addProperty("query","observatory");
  assertTrue(db.request(bob,search).getAsJsonArray("entries").isEmpty());
  var own=db.request(owner,search).getAsJsonArray("entries");assertEquals(1,own.size());assertEquals("group-place",Json.str(own.get(0).getAsJsonObject(),"kind"));assertEquals(groupId,Json.str(own.get(0).getAsJsonObject(),"id"));assertFalse(own.toString().contains("location"));
  assertEquals(1,db.request(admin,search).getAsJsonArray("entries").size());
  var apply=input("groups","apply",groupId);apply.addProperty("text","Join");db.request(alice,apply);var accept=input("groups","application",groupId);accept.addProperty("target",alice.id());accept.addProperty("accept",true);db.request(owner,accept);
  assertEquals(1,db.request(alice,search).getAsJsonArray("entries").size());db.request(alice,input("groups","leave",groupId));assertTrue(db.request(alice,search).getAsJsonArray("entries").isEmpty());
  db.request(owner,input("groups","delete",groupId));assertTrue(db.request(owner,search).getAsJsonArray("entries").isEmpty());assertTrue(db.request(admin,search).getAsJsonArray("entries").isEmpty());
 }

 @Test void privateLocationsAreNotSentToOutsidersAndSurviveTrash()throws Exception{
  var group=create("groups");var place=new CommunityLocation("Private base","minecraft:overworld",123,64,456,true).json();var change=input("groups","location",id(group));change.add("location",place);assertThrows(IllegalArgumentException.class,()->db.request(owner,change));
  assertFalse(detail("groups",id(group),owner).has("location"));assertFalse(detail("groups",id(group),alice).has("location"));assertFalse(db.request(alice,input("groups","list","")).toString().contains("Private base"));
  assertThrows(CommunityFailure.class,()->db.request(alice,change));var apply=input("groups","apply",id(group));apply.addProperty("text","Join");db.request(alice,apply);var accept=input("groups","application",id(group));accept.addProperty("target",alice.id());accept.addProperty("accept",true);db.request(owner,accept);assertFalse(detail("groups",id(group),alice).has("location"));
  var marker=input("groups","plusItemSave",id(group));marker.addProperty("kind","place");marker.addProperty("title","Private base");marker.addProperty("description","Private meeting");marker.add("location",place);db.request(owner,marker);
  assertTrue(detail("groups",id(group),alice).getAsJsonArray("groupItems").toString().contains("Private base"));assertTrue(detail("groups",id(group),bob).getAsJsonArray("groupItems").isEmpty());
  db.request(owner,input("groups","delete",id(group)));assertThrows(CommunityFailure.class,()->detail("groups",id(group),alice));
  db.request(owner,input("groups","restore",id(group)));assertFalse(detail("groups",id(group),alice).has("location"));
  assertTrue(detail("groups",id(group),alice).getAsJsonArray("groupItems").toString().contains("Private base"));assertTrue(detail("groups",id(group),bob).getAsJsonArray("groupItems").isEmpty());
  change.add("location",JsonNull.INSTANCE);assertThrows(IllegalArgumentException.class,()->db.request(owner,change));assertFalse(detail("groups",id(group),owner).has("location"));
 }


 @Test void groupChatHoldsMembershipThroughDelivery()throws Exception{
  var group=create("groups");String groupId=id(group);var database=TestDatabase.database(world);
  db.deliverGroupChat(groupId,owner.id(),(title,members)->{
   assertEquals("Example",title);assertEquals(Set.of(owner.id()),members);
   var lockAttempt=java.util.concurrent.CompletableFuture.supplyAsync(()->{
    try{return database.transaction(()->{try(var q=database.connection().prepareStatement("SELECT id FROM documents WHERE id=? FOR UPDATE NOWAIT")){q.setString(1,groupId);q.executeQuery().close();return "locked";}});}catch(Exception e){for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql)return sql.getSQLState();throw new RuntimeException(e);}
   });assertEquals("55P03",lockAttempt.get(3,java.util.concurrent.TimeUnit.SECONDS));
  });
  database.transaction(()->{try(var q=database.connection().prepareStatement("SELECT id FROM documents WHERE id=? FOR UPDATE NOWAIT")){q.setString(1,groupId);q.executeQuery().close();}return null;});
  assertThrows(CommunityFailure.class,()->db.deliverGroupChat(groupId,alice.id(),(title,members)->fail("Non-member received chat audience")));
 }
 @Test void chatAudienceUsesCurrentMembershipAndRejectsNonMembers()throws Exception{
  var group=create("groups");String groupId=id(group);assertTrue(db.chatMembers(groupId,owner.id()).contains(owner.id()));assertEquals(groupId,Json.str(db.playerGroups(owner.id()).getFirst(),"id"));
  assertTrue(db.playerGroups(alice.id()).isEmpty());assertThrows(CommunityFailure.class,()->db.chatMembers(groupId,alice.id()));
  // An archived document cannot become a chat channel even if it carries members.
  String archived=UUID.randomUUID().toString();var sql=TestDatabase.database(world);sql.communityTransaction(()->{
   var document=new JsonObject();document.addProperty("id",archived);document.addProperty("section","board");document.addProperty("status","open");var members=new JsonObject();members.addProperty(owner.id(),"leader");document.add("members",members);CommunityDocuments.put(sql,document);return null;
  });
  assertThrows(CommunityFailure.class,()->db.chatMembers(archived,owner.id()));
 }
}
