package dev.abros.rivet.core;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Shared command guarantees must survive retirement of the former document domains. */
@Tag("postgres")
class GroupCommandContractTest {
 @TempDir Path root;
 CommunityStore store;
 CommunityStore.Actor owner=actor("Owner"),reader=actor("Reader"),other=actor("Other");
 static CommunityStore.Actor actor(String name){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,false,false);}
 @BeforeEach void setup()throws Exception{store=new CommunityStore(TestDatabase.database(root),CommunityStore.defaults());for(var a:List.of(owner,reader,other))store.seen(a);}
 JsonObject request(String op,String id){var q=new JsonObject();q.addProperty("section","groups");q.addProperty("op",op);q.addProperty("id",id);return q;}
 JsonObject create(CommunityStore.Actor creator,String title)throws Exception{var q=request("create","");q.addProperty("title",title);q.addProperty("description","Description");q.addProperty("type","Команда");return store.request(creator,q).getAsJsonObject("detail");}
 JsonObject command(String op,String id){var q=request(op,id);q.addProperty("operationId",UUID.randomUUID().toString());q.addProperty("issuedAt",System.currentTimeMillis());return q;}
 String id(JsonObject j){return Json.str(j,"id");}
 @Test void retryRetainsReceiptAndCannotBeReusedForAnotherObjectOrPrivilege()throws Exception{
  var group=create(owner,"Team");var apply=command("apply",id(group));apply.addProperty("text","Private application");
  var first=store.request(reader,apply);apply.addProperty("request","second transport request");var repeated=store.request(reader,apply);
  assertTrue(repeated.get("replayed").getAsBoolean());assertEquals(first.get("detail"),repeated.get("detail"));assertEquals(1,store.unread(owner.id()));
  var changed=new CommunityStore.Actor(reader.id(),reader.name(),true,true);assertEquals(CommunityFailure.Code.INVALID,assertThrows(CommunityFailure.class,()->store.request(changed,apply)).code());
  apply.addProperty("id",UUID.randomUUID().toString());assertThrows(CommunityFailure.class,()->store.request(reader,apply));
 }
 @Test void staleEditAndForeignEditCannotChangeTitleOrExposePrivateApplication()throws Exception{
  var group=create(owner,"Original");var edit=command("edit",id(group));edit.add("revision",group.get("revision"));edit.addProperty("title","Updated");edit.addProperty("description","Public");edit.addProperty("type","Команда");
  assertThrows(CommunityFailure.class,()->store.request(other,edit));var updated=store.request(owner,edit).getAsJsonObject("detail");assertEquals("Updated",Json.str(updated,"title"));
  var stale=edit.deepCopy();stale.addProperty("operationId",UUID.randomUUID().toString());stale.addProperty("title","Must not win");assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->store.request(owner,stale)).code());
  var apply=request("apply",id(group));apply.addProperty("text","PRIVATE_APPLICATION_BODY");store.request(reader,apply);
  var publicView=store.request(other,request("detail",id(group))).getAsJsonObject("detail");assertEquals("Updated",Json.str(publicView,"title"));assertFalse(publicView.toString().contains("PRIVATE_APPLICATION_BODY"));
 }
 @Test void rollbackAndAcknowledgementPreserveLaterOutboxRecords()throws Exception{
  store.acknowledgeChanges(store.pendingChanges());
  assertThrows(Exception.class,()->store.transaction(()->{create(owner,"Rolled back");throw new Exception("rollback");}));assertTrue(store.pendingChanges().isEmpty());
  create(owner,"Committed");var batch=store.pendingChanges();assertFalse(batch.isEmpty());store.externalNotice(reader.id(),"After batch");store.acknowledgeChanges(batch);
  var later=store.pendingChanges();assertEquals(1,later.size());assertEquals("notifications",Json.str(later.get(0).getAsJsonObject(),"topic"));assertEquals(reader.id(),Json.str(later.get(0).getAsJsonObject(),"recipient"));
 }
 @Test void chronologicalCursorIsStableAcrossInsertAndBoundToReaderAndSort()throws Exception{
  var expected=new ArrayList<String>();for(int i=0;i<12;i++)expected.add(id(create(actor("Owner"+i),"Group "+i)));
  var list=request("list","");list.addProperty("sort","oldest");var first=store.request(reader,list);var rows=first.getAsJsonArray("entries");assertEquals(10,rows.size());for(int i=0;i<10;i++)assertEquals(expected.get(i),id(rows.get(i).getAsJsonObject()));
  // The cursor fixes an upper sequence bound: later inserts belong to the next refresh.
  create(actor("Later"),"Later group");list.addProperty("cursor",Json.str(first,"nextCursor"));var tail=store.request(reader,list).getAsJsonArray("entries");assertEquals(2,tail.size());assertEquals(expected.get(10),id(tail.get(0).getAsJsonObject()));
  assertThrows(CommunityFailure.class,()->store.request(other,list));list.addProperty("sort","recent");assertThrows(IllegalArgumentException.class,()->store.request(reader,list));
 }
 @Test void deletingAndRestoringGroupPreservesApplicationsAndPrivacy()throws Exception{
  var group=create(owner,"Team");var apply=request("apply",id(group));apply.addProperty("text","Private queue");store.request(reader,apply);
  store.request(owner,request("delete",id(group)));assertThrows(CommunityFailure.class,()->store.request(reader,request("detail",id(group))));
  assertThrows(CommunityFailure.class,()->store.request(reader,request("restore",id(group))));store.request(owner,request("restore",id(group)));
  assertTrue(store.request(reader,request("detail",id(group))).getAsJsonObject("detail").get("hasApplication").getAsBoolean());
  assertFalse(store.request(other,request("detail",id(group))).toString().contains("Private queue"));
 }
 @Test void groupFiltersIncludeInvitationsAndApplicationsWithoutExposingTheirContents()throws Exception{
  var group=create(owner,"Searchable team");var invite=request("invite",id(group));invite.addProperty("target",reader.id());store.request(owner,invite);
  var apply=request("apply",id(group));apply.addProperty("text","PRIVATE_APPLICATION");store.request(other,apply);
  var list=request("list","");list.addProperty("participating",true);
  for(var actor:List.of(owner,reader,other)){
   var rows=store.request(actor,list).getAsJsonArray("entries");assertEquals(1,rows.size());assertEquals(id(group),id(rows.get(0).getAsJsonObject()));assertFalse(rows.toString().contains("PRIVATE_APPLICATION"));
  }
  assertTrue(store.request(actor("Stranger"),list).getAsJsonArray("entries").isEmpty());
  list=request("list","");list.addProperty("member",reader.id());assertTrue(store.request(reader,list).getAsJsonArray("entries").isEmpty());
  list.addProperty("member",owner.id());assertEquals(1,store.request(reader,list).getAsJsonArray("entries").size());
  list.addProperty("query","unmatched");assertTrue(store.request(reader,list).getAsJsonArray("entries").isEmpty());
  list=request("list","");list.addProperty("mine",true);assertTrue(store.request(reader,list).getAsJsonArray("entries").isEmpty());assertEquals(1,store.request(owner,list).getAsJsonArray("entries").size());
 }
}
