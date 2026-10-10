package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityQueryRelationsTest {
 @TempDir Path temp;
 private static final long NOW=1800000000000L;
 private static JsonObject document(String id,String section){var j=new JsonObject();j.addProperty("id",id);j.addProperty("section",section);j.addProperty("title",id);j.addProperty("owner","owner");j.addProperty("status","open");j.addProperty("startsAt",NOW+10000);j.addProperty("endsAt",NOW+10000);j.addProperty("capacity",0);return j;}
 @Test void participationUsesEveryNormalizedRelationKind()throws Exception{
  var db=TestDatabase.database(temp);var actor=new CommunityStore.Actor("reader","Reader",false,false);
  for(var entry:Map.of("board",List.of("responses"),"groups",List.of("members","applications","invitations"),"events",List.of("participants"),"polls",List.of("votes"),"ideas",List.of("supporters")).entrySet()){
   var expected=new HashSet<String>();db.transaction(()->{CommunityDocuments.put(db,document(entry.getKey()+"-other",entry.getKey()));for(String kind:entry.getValue()){String id=entry.getKey()+"-"+kind;expected.add(id);var d=document(id,entry.getKey());var relation=new JsonObject();relation.addProperty("reader","Reader");d.add(kind,relation);CommunityDocuments.put(db,d);}return null;});
   var q=new JsonObject();q.addProperty("participating",true);var rows=db.transaction(()->new CommunityQueries(db).list(actor,q,entry.getKey(),NOW,CommunityStore.defaults()));var actual=new HashSet<String>();for(var row:rows)actual.add(Json.str(row.getAsJsonObject(),"id"));assertEquals(expected,actual);
  }
  var q=new JsonObject();q.addProperty("member","reader");var rows=db.transaction(()->new CommunityQueries(db).list(actor,q,"groups",NOW,CommunityStore.defaults()));assertEquals(1,rows.size());assertEquals("groups-members",Json.str(rows.get(0).getAsJsonObject(),"id"));
 }
 @Test void equalEventTimesKeepStableContinuationForEverySort()throws Exception{
  var db=TestDatabase.database(temp);db.transaction(()->{for(int i=0;i<25;i++)CommunityDocuments.put(db,document("event-"+i,"events"));return null;});var actor=new CommunityStore.Actor("reader","Reader",false,false);
  for(String sort:List.of("default","recent","oldest")){var q=new JsonObject();q.addProperty("sort",sort);var ids=new ArrayList<String>();do{var rows=db.transaction(()->new CommunityQueries(db).list(actor,q,"events",NOW,CommunityStore.defaults()));for(var row:rows)ids.add(Json.str(row.getAsJsonObject(),"id"));String cursor=rows.isEmpty()?"":Json.opt(rows.get(rows.size()-1).getAsJsonObject(),"_nextCursor","");if(cursor.isEmpty())break;q.addProperty("cursor",cursor);assertTrue(ids.size()<=25);}while(true);assertEquals(25,ids.size());for(int i=0;i<25;i++)assertEquals("event-"+(24-i),ids.get(i));}
 }
}
