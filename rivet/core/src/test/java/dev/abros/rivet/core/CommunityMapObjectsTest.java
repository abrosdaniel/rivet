package dev.abros.rivet.core;
import com.google.gson.*;
import dev.abros.rivet.core.map.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class CommunityMapObjectsTest {
 @TempDir Path temp;CommunityStore store;CommunityStore.Actor owner,admin,other;String id;
 @BeforeEach void setup()throws Exception{var db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());owner=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",false,false);admin=new CommunityStore.Actor(UUID.randomUUID().toString(),"Admin",true,true);other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);for(var a:List.of(owner,admin,other))store.seen(a);id=UUID.randomUUID().toString();}
 JsonObject command(String op,long revision){var j=new JsonObject();j.addProperty("section","home");j.addProperty("op",op);j.addProperty("id",id);j.addProperty("revision",revision);return j;}
 JsonObject submit(String name,long revision){var j=command("mapObjectSubmit",revision);j.addProperty("type","marker");j.add("draft",MapMarkerLink.json(new MapMarker(UUID.fromString(id),"minecraft:overworld",name,1,64,2,0xffffff,"pin")));return j;}
 JsonArray publicRows()throws Exception{return store.request(other,command("mapObjects",0)).getAsJsonArray("mapObjects");}
 @Test void approvalKeepsOldPublishedVersionUntilNewApproval()throws Exception{
  store.request(owner,submit("First",0));assertTrue(publicRows().isEmpty());
  assertThrows(CommunityFailure.class,()->store.request(owner,command("mapObjectApprove",1)));
  store.request(admin,command("mapObjectApprove",1));assertEquals("First",Json.str(publicRows().get(0).getAsJsonObject().getAsJsonObject("published"),"name"));
  store.request(owner,submit("Second",2));var publicRow=publicRows().get(0).getAsJsonObject();assertFalse(publicRow.has("draft"));assertFalse(publicRow.has("owner"));assertEquals("First",Json.str(publicRow.getAsJsonObject("published"),"name"));
  assertThrows(CommunityFailure.class,()->store.request(admin,command("mapObjectApprove",2)));
  store.request(admin,command("mapObjectApprove",3));assertEquals("Second",Json.str(publicRows().get(0).getAsJsonObject().getAsJsonObject("published"),"name"));
  assertThrows(CommunityFailure.class,()->store.request(other,command("mapObjectWithdraw",4)));
  store.request(owner,command("mapObjectWithdraw",4));assertTrue(publicRows().isEmpty());
 }
 @Test void privateQueueAndOwnershipAreEnforced()throws Exception{
  store.request(owner,submit("Private",0));var q=command("mapObjects",0);q.addProperty("manage",true);
  assertTrue(store.request(other,q).getAsJsonArray("mapObjects").isEmpty());assertEquals(1,store.request(admin,q).getAsJsonArray("mapObjects").size());
  assertThrows(CommunityFailure.class,()->store.request(other,submit("Stolen",1)));
  store.request(admin,command("mapObjectReject",1));assertTrue(publicRows().isEmpty());
 }
 @Test void personalQueueDoesNotIncludeOtherAuthorsForAdministrators()throws Exception{
  store.request(owner,submit("Other author",0));var q=command("mapObjects",0);q.remove("id");q.addProperty("manage",true);q.addProperty("mine",true);
  assertTrue(store.request(admin,q).getAsJsonArray("mapObjects").isEmpty());
  assertEquals(1,store.request(owner,q).getAsJsonArray("mapObjects").size());
  q.addProperty("mine",false);assertEquals(1,store.request(admin,q).getAsJsonArray("mapObjects").size());
  assertTrue(store.request(other,q).getAsJsonArray("mapObjects").isEmpty());
 }
 @Test void unchangedSnapshotsAreSmallAndWithdrawnIdsCannotReuseApprovalRevision()throws Exception{
  store.request(owner,submit("First",0));store.request(admin,command("mapObjectApprove",1));
  var q=command("mapObjects",0);var first=store.request(other,q);q.addProperty("snapshot",first.get("snapshot").getAsLong());
  var unchanged=store.request(other,q);assertTrue(unchanged.get("unchanged").getAsBoolean());assertFalse(unchanged.has("mapObjects"));
  store.request(owner,command("mapObjectWithdraw",2));assertTrue(store.request(other,q).getAsJsonArray("mapObjects").isEmpty());
  var saved=store.request(owner,submit("New",0));assertTrue(saved.get("revision").getAsLong()>2);
  assertThrows(CommunityFailure.class,()->store.request(admin,command("mapObjectApprove",1)));
  var page=command("mapObjects",0);page.addProperty("cursor",id);page.addProperty("snapshot",first.get("snapshot").getAsLong());assertTrue(store.request(other,page).get("restart").getAsBoolean());
 }
 @Test void territoriesUseTheSameApprovalRulesAndPrivateRecordsAreNotLeaked()throws Exception{
  var q=command("mapObjectSubmit",0);q.addProperty("type","territory");
  var t=new MapTerritory(UUID.fromString(id),"minecraft:overworld","Area",0x123456,List.of(new MapTerritory.Point(0,0),new MapTerritory.Point(10,0),new MapTerritory.Point(0,10)));q.add("draft",MapTerritoryJson.write(t));store.request(owner,q);
  assertTrue(publicRows().isEmpty());store.request(admin,command("mapObjectApprove",1));assertEquals(t,MapTerritoryJson.read(publicRows().get(0).getAsJsonObject().getAsJsonObject("published"),UUID.fromString(id)));
  q.addProperty("revision",2);q.getAsJsonObject("draft").addProperty("name","Changed");store.request(owner,q);store.request(admin,command("mapObjectReject",3));assertEquals("Area",publicRows().get(0).getAsJsonObject().getAsJsonObject("published").get("name").getAsString());
  var own=command("mapObjects",0);own.addProperty("manage",true);assertTrue(store.request(other,own).getAsJsonArray("mapObjects").isEmpty());
 }
 @Test void globalBoundMatchesClientCapacityAndRejectsOnlyNewObjects()throws Exception{
  var db=TestDatabase.database(temp);db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) SELECT 'map-object', 'capacity-' || n, '{}'::jsonb FROM generate_series(1,?) n")){q.setInt(1,MapPublicationLimits.TOTAL);q.executeUpdate();}return null;});
  assertThrows(CommunityFailure.class,()->store.request(owner,submit("Overflow",0)));
  db.transaction(()->{try(var q=db.connection().createStatement()){q.executeUpdate("DELETE FROM records WHERE namespace='map-object' AND id='capacity-1'");}return null;});
  var first=store.request(owner,submit("Last",0));
  assertDoesNotThrow(()->store.request(owner,submit("Edited",first.get("revision").getAsLong())));
 }

 @Test void alternateUuidSpellingCannotCreateDuplicatePublicObjects()throws Exception{
  var q=submit("First",0);q.addProperty("id",id.toUpperCase(Locale.ROOT));store.request(owner,q);
  store.request(admin,command("mapObjectApprove",1));assertEquals(id,Json.str(publicRows().get(0).getAsJsonObject(),"id"));
  assertThrows(CommunityFailure.class,()->store.request(other,submit("Stolen",2)));
  store.request(owner,submit("Second",2));assertEquals(1,publicRows().size());
 }


 @Test void privateEditsDoNotInvalidatePublicSnapshot()throws Exception{
  store.request(owner,submit("Public",0));store.request(admin,command("mapObjectApprove",1));
  var read=command("mapObjects",0);long version=store.request(other,read).get("snapshot").getAsLong();read.addProperty("snapshot",version);
  store.request(owner,submit("Private change",2));assertTrue(store.request(other,read).has("unchanged"));
  store.request(admin,command("mapObjectReject",3));assertTrue(store.request(other,read).has("unchanged"));
 }
 @Test void complexTerritoriesFitEveryPublicAndManagementPage()throws Exception{
  var db=TestDatabase.database(temp);var points=new ArrayList<MapTerritory.Point>();
  for(int n=0;n<128;n++){double a=n*Math.PI*2/128;points.add(new MapTerritory.Point(1000000+(int)Math.round(10000*Math.cos(a)),1000000+(int)Math.round(10000*Math.sin(a))));}
  db.transaction(()->{for(int n=0;n<16;n++){String key=new UUID(0,n+1).toString();var row=new JsonObject();row.addProperty("owner",owner.id());row.addProperty("author",owner.name());row.addProperty("type","territory");row.addProperty("revision",n+1);var body=MapTerritoryJson.write(new MapTerritory(UUID.fromString(key),"minecraft:overworld","Area "+n,0x123456,points));row.add("published",body);row.add("draft",body.deepCopy());store.record("map-object",key,row);}return null;});
  for(boolean manage:List.of(false,true)){var found=new HashSet<String>();String cursor="";int pages=0;
   do{var q=command("mapObjects",0);q.remove("id");q.addProperty("manage",manage);q.addProperty("cursor",cursor);var page=store.request(admin,q);
    assertTrue(Json.GSON.toJson(page).length()<=32767,"Oversized page");for(var row:page.getAsJsonArray("mapObjects"))assertTrue(found.add(Json.str(row.getAsJsonObject(),"id")));
    cursor=Json.opt(page,"nextCursor","");assertTrue(++pages<20);
   }while(!cursor.isEmpty());assertEquals(16,found.size());
  }
 }

 JsonObject delta(long since){var j=command("mapObjects",0);j.remove("id");j.addProperty("deltaProtocol",true);j.addProperty("since",since);return j;}
 @Test void incrementalFeedContainsOnlyPublishedChangesAndRemoval()throws Exception{
  var empty=store.request(other,delta(-1));assertEquals(0,empty.get("snapshot").getAsLong());
  store.request(owner,submit("First",0));assertTrue(store.request(other,delta(0)).has("unchanged"));
  store.request(admin,command("mapObjectApprove",1));var first=store.request(other,delta(0));assertEquals(1,first.getAsJsonArray("mapObjects").size());assertTrue(first.get("delta").getAsBoolean());
  long version=first.get("snapshot").getAsLong();store.request(owner,submit("Draft",2));assertTrue(store.request(other,delta(version)).has("unchanged"));
  store.request(owner,command("mapObjectWithdraw",3));var removed=store.request(other,delta(version));assertTrue(removed.getAsJsonArray("mapObjects").get(0).getAsJsonObject().get("removed").getAsBoolean());
  assertTrue(store.request(other,delta(-1)).getAsJsonArray("mapObjects").isEmpty());
 }
 @Test void compactedHistoryRequestsFullSnapshot()throws Exception{
  var db=TestDatabase.database(temp);db.transaction(()->{var state=new JsonObject();state.addProperty("value",20);state.addProperty("publicVersion",10);state.addProperty("floor",5);store.record("map-publication-state","version",state);return null;});
  assertTrue(store.request(other,delta(4)).has("restart"));assertFalse(store.request(other,delta(-1)).has("restart"));
 }

 @Test void largeSnapshotSurvivesConcurrentPublicationThenCatchesUpByDelta()throws Exception{
  var db=TestDatabase.database(temp);db.transaction(()->{
   try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) SELECT 'map-object', md5(n::text)::uuid::text, jsonb_build_object('owner',?::text,'author','Fixture','type','marker','revision',n,'publicationRevision',n,'published',jsonb_build_object('dimension','minecraft:overworld','name','Point '||n,'x',n,'y',64,'z',0,'color',-1,'icon','pin')) FROM generate_series(1,1024) n")){q.setString(1,other.id());q.executeUpdate();}
   var meta=new JsonObject();meta.addProperty("value",1024);meta.addProperty("publicVersion",1024);meta.addProperty("floor",1024);store.record("map-publication-state","version",meta);return null;
  });
  var state=new MapPublicationState();var query=delta(-1);var first=store.request(other,query);long version=first.get("snapshot").getAsLong();assertFalse(state.page(first,0));
  var submitted=store.request(owner,submit("New during load",0));store.request(admin,command("mapObjectApprove",submitted.get("revision").getAsLong()));
  String cursor=Json.str(first,"nextCursor");int pageCount=1;
  while(!cursor.isEmpty()){query=delta(-1);query.addProperty("cursor",cursor);query.addProperty("until",version);var page=store.request(other,query);assertFalse(page.has("restart"));assertTrue(Json.GSON.toJson(page).length()<32767,"page chars="+Json.GSON.toJson(page).length());state.page(page,pageCount++*10000L);cursor=Json.opt(page,"nextCursor","");assertTrue(pageCount<20,"Too many pages, cursor="+cursor+", rows="+page.getAsJsonArray("mapObjects").size());}
  var update=store.request(other,delta(state.snapshot()));state.begin();state.page(update,pageCount*10000L);assertEquals(1025,state.rows().size());assertTrue(state.rows().stream().anyMatch(r->Json.str(r,"id").equals(id)));
 }
 @Test void publicationJournalPrunesOldestEntryAndRequiresRecovery()throws Exception{
  var db=TestDatabase.database(temp);db.transaction(()->{
   try(var q=db.connection().createStatement()){q.executeUpdate("INSERT INTO records(namespace,id,body) SELECT 'map-publication-change', md5(n::text)::uuid::text, jsonb_build_object('id',md5(n::text)::uuid::text,'revision',n,'removed',true) FROM generate_series(1,8192) n");}
   var meta=new JsonObject();meta.addProperty("value",8192);meta.addProperty("publicVersion",8192);meta.addProperty("floor",0);store.record("map-publication-state","version",meta);return null;
  });
  var saved=store.request(owner,submit("After history capacity",0));store.request(admin,command("mapObjectApprove",saved.get("revision").getAsLong()));
  db.transaction(()->{try(var q=db.connection().createStatement();var rs=q.executeQuery("SELECT count(*) FROM records WHERE namespace='map-publication-change'")){assertTrue(rs.next());assertEquals(8192,rs.getInt(1));}return null;});
  assertTrue(store.request(other,delta(0)).has("restart"));assertFalse(store.request(other,delta(1)).has("restart"));assertEquals(1,store.request(other,delta(-1)).getAsJsonArray("mapObjects").size());
 }

}
