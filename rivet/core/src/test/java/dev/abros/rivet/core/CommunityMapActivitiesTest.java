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


 void put(JsonObject j)throws Exception{db.communityTransaction(()->{store.put(j);return null;});}
 JsonObject get(String id)throws Exception{return db.communityTransaction(()->store.get(id));}
 String group()throws Exception{var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("section","groups");j.addProperty("title","Group");j.addProperty("status","open");j.addProperty("owner",owner.id());var members=new JsonObject();members.addProperty(owner.id(),owner.name());j.add("members",members);put(j);return id;}
 void membership(String group,boolean active)throws Exception{var j=get(group);if(active)j.getAsJsonObject("members").addProperty(other.id(),other.name());else j.getAsJsonObject("members").remove(other.id());put(j);}


 JsonObject event(String visibility,String group,boolean privateLocation,long start)throws Exception{var j=new JsonObject();j.addProperty("id",UUID.randomUUID().toString());j.addProperty("section","events");j.addProperty("title","Meeting");j.addProperty("owner",owner.id());j.addProperty("status","open");j.addProperty("visibility",visibility);j.addProperty("startsAt",start);j.addProperty("durationMinutes",60);if(!group.isEmpty())j.addProperty("group",group);var p=location();p.addProperty("membersOnly",privateLocation);j.add("location",p);j.add("eventInvites",new JsonArray());put(j);return j;}


}
