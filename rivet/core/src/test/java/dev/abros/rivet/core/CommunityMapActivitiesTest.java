package dev.abros.rivet.core;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres") class CommunityMapActivitiesTest {
 @TempDir Path temp;PgDatabase db;CommunityStore store;
 CommunityStore.Actor owner=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",false,true),other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false),admin=new CommunityStore.Actor(UUID.randomUUID().toString(),"Admin",true,true);
 @BeforeEach void init()throws Exception{db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());store.seen(owner);store.seen(other);store.seen(admin);}
 JsonObject input(String op){var j=new JsonObject();j.addProperty("section","home");j.addProperty("op",op);return j;}
 JsonObject location(){return new CommunityLocation("Место","minecraft:overworld",12,64,-8,false).json();}
 JsonArray points(CommunityStore.Actor a,String source)throws Exception{var q=input("mapActivities");q.addProperty("source",source);return store.request(a,q).getAsJsonArray("activities");}
 JsonObject task()throws Exception{var q=input("workSave");q.addProperty("title","Build");q.addProperty("description","");return store.request(owner,q).getAsJsonObject("task");}
 JsonObject change(JsonObject task,String op){var q=input(op);q.add("task",task.get("id"));q.add("revision",task.get("revision"));return q;}
 JsonObject locate(JsonObject task)throws Exception{var q=change(task,"workLocation");q.add("location",location());return store.request(owner,q).getAsJsonObject("task");}
 @Test void taskDestinationIsOptionalRemovableAndRevisionProtected()throws Exception{
  var task=task();assertFalse(task.has("location"));assertTrue(points(owner,"tasks").isEmpty());
  var stale=change(task,"workLocation");stale.add("location",JsonNull.INSTANCE);task=locate(task);
  assertEquals(1,points(owner,"tasks").size());assertTrue(points(other,"tasks").isEmpty());assertTrue(points(admin,"tasks").isEmpty());
  assertThrows(CommunityFailure.class,()->store.request(owner,stale));
  var foreign=change(task,"workLocation");foreign.add("location",location());assertThrows(CommunityFailure.class,()->store.request(other,foreign));
  var rename=change(task,"workSave");rename.addProperty("title","Bridge");rename.addProperty("description","");task=store.request(owner,rename).getAsJsonObject("task");assertEquals("Bridge",Json.str(points(owner,"tasks").get(0).getAsJsonObject(),"title"));assertTrue(task.has("location"));
  var remove=change(task,"workLocation");remove.add("location",JsonNull.INSTANCE);task=store.request(owner,remove).getAsJsonObject("task");assertFalse(task.has("location"));assertTrue(points(owner,"tasks").isEmpty());
  task=locate(task);var done=change(task,"workStatus");done.addProperty("status","done");store.request(owner,done);assertTrue(points(owner,"tasks").isEmpty());
 }
 @Test void invalidCoordinatesDoNotChangeSavedLocation()throws Exception{var t=locate(task());var q=change(t,"workLocation");var p=location();p.addProperty("x",1.5);q.add("location",p);assertThrows(IllegalArgumentException.class,()->store.request(owner,q));assertEquals(12,points(owner,"tasks").get(0).getAsJsonObject().getAsJsonObject("location").get("x").getAsInt());}
 void put(JsonObject j)throws Exception{db.communityTransaction(()->{store.put(j);return null;});}
 JsonObject get(String id)throws Exception{return db.communityTransaction(()->store.get(id));}
 String group()throws Exception{var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("section","groups");j.addProperty("title","Group");j.addProperty("status","open");j.addProperty("owner",owner.id());var members=new JsonObject();members.addProperty(owner.id(),owner.name());j.add("members",members);put(j);return id;}
 void membership(String group,boolean active)throws Exception{var j=get(group);if(active)j.getAsJsonObject("members").addProperty(other.id(),other.name());else j.getAsJsonObject("members").remove(other.id());put(j);}
 @Test void groupMembershipLimitsTasks()throws Exception{String group=group();var t=task();String taskId=Json.str(t,"id");db.communityTransaction(()->{try(var q=db.connection().prepareStatement("UPDATE community_group_items SET group_id=? WHERE id=?")){q.setString(1,group);q.setString(2,taskId);q.executeUpdate();}return null;});t=store.request(owner,change(t,"workGet")).getAsJsonObject("task");t=locate(t);assertTrue(points(other,"tasks").isEmpty());membership(group,true);assertEquals(1,points(other,"tasks").size());membership(group,false);assertTrue(points(other,"tasks").isEmpty());var g=get(group);g.addProperty("status","closed");put(g);assertTrue(points(owner,"tasks").isEmpty());}
 JsonObject event(String visibility,String group,boolean privateLocation,long start)throws Exception{var j=new JsonObject();j.addProperty("id",UUID.randomUUID().toString());j.addProperty("section","events");j.addProperty("title","Meeting");j.addProperty("owner",owner.id());j.addProperty("status","open");j.addProperty("visibility",visibility);j.addProperty("startsAt",start);j.addProperty("durationMinutes",60);if(!group.isEmpty())j.addProperty("group",group);var p=location();p.addProperty("membersOnly",privateLocation);j.add("location",p);j.add("eventInvites",new JsonArray());put(j);return j;}
 @Test void eventsRespectInvitesPrivateLocationsMembershipAndEndTime()throws Exception{long now=System.currentTimeMillis();var event=event("invited","",false,now+60000);assertTrue(points(other,"events").isEmpty());event.getAsJsonArray("eventInvites").add(other.id());put(event);assertEquals(1,points(other,"events").size());event.getAsJsonArray("eventInvites").remove(0);put(event);assertTrue(points(other,"events").isEmpty());event.addProperty("status","cancelled");put(event);assertTrue(points(owner,"events").isEmpty());
  String group=group();event=event("public",group,true,now-60000);assertTrue(points(other,"events").isEmpty());membership(group,true);assertEquals(1,points(other,"events").size());membership(group,false);assertTrue(points(other,"events").isEmpty());assertEquals(1,points(admin,"events").size());event.addProperty("startsAt",now-3600001);put(event);assertTrue(points(owner,"events").isEmpty());
  event=event("group",group,false,now+60000);assertTrue(points(other,"events").isEmpty());membership(group,true);assertEquals(1,points(other,"events").size());event.remove("location");put(event);assertTrue(points(other,"events").isEmpty());
 }
 @Test void disabledModulesReturnNoCoordinates()throws Exception{locate(task());event("public","",false,System.currentTimeMillis()+60000);var modules=FeatureModules.from(ServerSettings.parse(ServerSettings.template().replaceAll("(?ms)(\\[(?:tasks|storage|events)\\][^\\[]*?)^enabled = true", "$1enabled = false")));store=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),modules);assertTrue(points(owner,"tasks").isEmpty());assertTrue(points(owner,"events").isEmpty());}
 @Test void paginationIsBoundedAndDoesNotExposeTaskBodies()throws Exception{for(int i=0;i<35;i++)locate(task());var q=input("mapActivities");q.addProperty("source","tasks");var first=store.request(owner,q);assertEquals(32,first.getAsJsonArray("activities").size());q.add("cursor",first.get("nextCursor"));var second=store.request(owner,q);assertEquals(3,second.getAsJsonArray("activities").size());assertFalse(second.has("nextCursor"));var row=first.getAsJsonArray("activities").get(0).getAsJsonObject();assertEquals(Set.of("id","title","location","group"),row.keySet());q.addProperty("cursor","bad");assertThrows(IllegalArgumentException.class,()->store.request(owner,q));}
}
