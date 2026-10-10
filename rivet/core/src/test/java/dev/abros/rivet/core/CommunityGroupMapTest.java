package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres") class CommunityGroupMapTest {
 @TempDir Path temp;PgDatabase db;CommunityStore store;
 CommunityStore.Actor leader=actor("Leader",false),member=actor("Member",false),outside=actor("Outside",false),admin=actor("Admin",true);
 static CommunityStore.Actor actor(String name,boolean admin){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,admin,admin);}
 @BeforeEach void init()throws Exception{db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());for(var a:List.of(leader,member,outside,admin))store.seen(a);}
 JsonObject command(String op,String id){var j=new JsonObject();j.addProperty("section","groups");j.addProperty("op",op);j.addProperty("id",id);return j;}
 JsonObject create()throws Exception{var j=command("create","");j.addProperty("title","Group");j.addProperty("description","Description");j.addProperty("type","Команда");return store.request(leader,j).getAsJsonObject("detail");}
 JsonObject detail(String id)throws Exception{return store.request(leader,command("detail",id)).getAsJsonObject("detail");}
 void join(String id)throws Exception{var j=command("invite",id);j.addProperty("target",member.id());store.request(leader,j);j=command("invitation",id);j.addProperty("accept",true);store.request(member,j);}
 JsonObject polygon(String id){return MapTerritoryJson.write(new MapTerritory(UUID.fromString(id),"minecraft:overworld","Base",0xff55aa55,List.of(new MapTerritory.Point(0,0),new MapTerritory.Point(30,0),new MapTerritory.Point(30,30),new MapTerritory.Point(0,30))));}
 JsonObject save(JsonObject group,JsonElement polygon){var j=command("plusTerritorySave",Json.str(group,"id"));j.add("revision",group.get("revision"));j.add("territory",polygon);j.addProperty("clearTerritory",polygon.isJsonNull());return j;}
 JsonArray territories(CommunityStore.Actor a)throws Exception{return store.request(a,command("groupMap","")).getAsJsonArray("territories");}
 JsonArray markers(CommunityStore.Actor a)throws Exception{var j=command("mapActivities","");j.addProperty("source","groupmarkers");return store.request(a,j).getAsJsonArray("activities");}
 @Test void territoryIsGroupOwnedAndRequiresManagerRevision()throws Exception{
  var g=create();String id=Json.str(g,"id");join(id);g=detail(id);var q=save(g,polygon(id));
  assertThrows(CommunityFailure.class,()->store.request(member,q));assertThrows(CommunityFailure.class,()->store.request(outside,q));
  var saved=store.request(leader,q).getAsJsonObject("detail");assertTrue(saved.has("territory"));assertEquals(1,territories(outside).size());assertFalse(territories(outside).get(0).getAsJsonObject().get("manage").getAsBoolean());
  assertThrows(CommunityFailure.class,()->store.request(leader,q));var role=command("role",id);role.addProperty("target",member.id());role.addProperty("role","assistant");store.request(leader,role);
  var change=save(detail(id),polygon(id));change.getAsJsonObject("territory").addProperty("name","Assistant base");store.request(member,change);assertEquals("Group",detail(id).getAsJsonObject("territory").get("name").getAsString());
  var remove=save(detail(id),JsonNull.INSTANCE);store.request(admin,Json.parse(Json.GSON.toJson(remove)));assertTrue(territories(outside).isEmpty());assertFalse(detail(id).has("territory"));
 }
 @Test void invalidGeometryAndLegacyLocationCannotOverwriteTerritory()throws Exception{var g=create();String id=Json.str(g,"id");g=store.request(leader,save(g,polygon(id))).getAsJsonObject("detail");var invalid=polygon(id);invalid.getAsJsonArray("points").get(0).getAsJsonObject().addProperty("x",.5);var q=save(g,invalid);assertThrows(RuntimeException.class,()->store.request(leader,q));var expected=polygon(id);expected.addProperty("name","Group");assertEquals(expected,detail(id).get("territory"));var location=command("location",id);location.add("location",new CommunityLocation("Old","minecraft:overworld",0,64,0,false).json());assertThrows(IllegalArgumentException.class,()->store.request(leader,location));}
 @Test void markersAreInternalEvenWithForgedPublicFlagAndDisappearAfterLeaving()throws Exception{var g=create();String id=Json.str(g,"id");join(id);var j=command("plusItemSave",id);j.addProperty("kind","place");j.addProperty("title","Secret");j.add("location",new CommunityLocation("Secret","minecraft:overworld",10,64,10,false).json());var saved=store.request(leader,j).getAsJsonObject("detail");assertTrue(saved.getAsJsonArray("groupItems").get(0).getAsJsonObject().getAsJsonObject("location").get("membersOnly").getAsBoolean());assertEquals(1,markers(member).size());assertTrue(markers(outside).isEmpty());assertTrue(store.request(outside,command("detail",id)).getAsJsonObject("detail").getAsJsonArray("groupItems").isEmpty());store.request(member,command("leave",id));assertTrue(markers(member).isEmpty());}
 @Test void disablingGroupsClosesEveryGroupMapEndpoint()throws Exception{var g=create();String id=Json.str(g,"id");store.request(leader,save(g,polygon(id)));var modules=FeatureModules.from(ServerSettings.parse(ServerSettings.template().replaceAll("(?ms)(\\[groups\\][^\\[]*?)^enabled = true","$1enabled = false")));store=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),modules);for(String op:List.of("groupMap","plusTerritorySave","toolsMapSettings","toolsMapPeers","toolsMapShare")){var q=command(op,"");q.addProperty("section","home");assertThrows(CommunityFailure.class,()->store.request(leader,q),op);}var q=command("mapActivities","");q.addProperty("section","home");q.addProperty("source","groupmarkers");assertTrue(store.request(leader,q).getAsJsonArray("activities").isEmpty());}
 @Test void retryDoesNotMutateTwiceAndReadDoesNotReplayOldTerritory()throws Exception{var g=create();String id=Json.str(g,"id");var q=save(g,polygon(id));q.addProperty("action","community");q=MenuCommands.prepare(q,System.currentTimeMillis());var first=store.request(leader,q);assertTrue(store.request(leader,q).get("replayed").getAsBoolean());assertEquals(first.getAsJsonObject("detail").get("revision"),detail(id).get("revision"));var read=command("groupMap","");read.addProperty("request","same-read");assertEquals(1,store.request(outside,read).getAsJsonArray("territories").size());store.request(leader,save(detail(id),JsonNull.INSTANCE));assertTrue(store.request(outside,read).getAsJsonArray("territories").isEmpty());}
 @Test void largestPolygonsFitWireAndPaginationDoesNotSkipGroups()throws Exception{
  for(int n=0;n<5;n++){String id=UUID.randomUUID().toString();var group=new JsonObject();group.addProperty("id",id);group.addProperty("section","groups");group.addProperty("title","Я".repeat(100));group.addProperty("owner",leader.id());group.addProperty("status","open");var vertices=new ArrayList<MapTerritory.Point>();for(int i=0;i<128;i++)vertices.add(new MapTerritory.Point(-29999000+i,-29999000+i*i));group.add("territory",MapTerritoryJson.write(new MapTerritory(UUID.fromString(id),"test:"+"a".repeat(250),"Я".repeat(80),-1,vertices)));db.communityTransaction(()->{store.put(group);return null;});}
  var ids=new HashSet<String>();var q=command("groupMap","");do{var response=store.request(outside,q);assertTrue(Json.GSON.toJson(response).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32767);var rows=response.getAsJsonArray("territories");assertTrue(rows.size()<=2);for(var row:rows)assertTrue(ids.add(Json.str(row.getAsJsonObject(),"id")));q.addProperty("cursor",Json.opt(response,"nextCursor",""));}while(!Json.str(q,"cursor").isEmpty());assertEquals(5,ids.size());
 }

 @Test void snapshotIncludesOneHundredGroupsWithoutPerPageRequests()throws Exception{
  for(int n=0;n<100;n++){String id=UUID.randomUUID().toString();var group=new JsonObject();group.addProperty("id",id);group.addProperty("section","groups");group.addProperty("title","Group "+n);group.addProperty("owner",leader.id());group.addProperty("status","open");group.add("territory",polygon(id));db.communityTransaction(()->{store.put(group);return null;});}
  var request=command("groupMap","");request.addProperty("groupSnapshot",true);var result=store.request(outside,request);
  assertEquals(100,result.getAsJsonArray("territories").size());var pages=MapGroupPages.split(result);var ids=new HashSet<String>();
  for(int n=0;n<pages.size();n++){var page=pages.get(n);assertEquals(n,page.get("groupPage").getAsInt());assertEquals(n==pages.size()-1,page.get("groupLast").getAsBoolean());assertTrue(Json.GSON.toJson(page).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32767);for(var row:page.getAsJsonArray("territories"))assertTrue(ids.add(Json.str(row.getAsJsonObject(),"id")));}
  assertEquals(100,ids.size());
 }

 @Test void territoryNameAlwaysFollowsGroupIncludingFullLengthRename()throws Exception{
  var g=create();String id=Json.str(g,"id");var forged=polygon(id);forged.addProperty("name","Forged name");g=store.request(leader,save(g,forged)).getAsJsonObject("detail");assertEquals("Group",g.getAsJsonObject("territory").get("name").getAsString());
  db.communityTransaction(()->{assertFalse(store.get(id).getAsJsonObject("territory").has("name"));return null;});
  var rename=command("edit",id);rename.addProperty("title","Я".repeat(100));rename.addProperty("description","Description");rename.addProperty("type","Команда");rename.add("revision",g.get("revision"));store.request(leader,rename);
  assertEquals("Я".repeat(100),detail(id).getAsJsonObject("territory").get("name").getAsString());assertEquals("Я".repeat(100),territories(outside).get(0).getAsJsonObject().getAsJsonObject("territory").get("name").getAsString());
 }

}
