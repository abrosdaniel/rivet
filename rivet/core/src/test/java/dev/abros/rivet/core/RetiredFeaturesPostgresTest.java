package dev.abros.rivet.core;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class RetiredFeaturesPostgresTest {
    @TempDir Path root;
    @Test void homeAndSearchNeverReadSavedUpcomingEvents()throws Exception{
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",true,true);store.seen(actor);
        String id=UUID.randomUUID().toString();
        var saved=Json.parse("{\"section\":\"events\",\"status\":\"open\",\"title\":\"Archived upcoming meeting\",\"visibility\":\"public\",\"capacity\":0}");
        saved.addProperty("id",id);saved.addProperty("owner",actor.id());saved.addProperty("startsAt",System.currentTimeMillis()+86400000);
        db.communityTransaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'events',?::jsonb)")){q.setString(1,id);q.setString(2,saved.toString());q.executeUpdate();}return null;});
        assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->store.publicView(saved,actor,0)).code());
        var home=store.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"list\"}"));
        assertTrue(home.getAsJsonArray("entries").isEmpty());assertFalse(home.toString().contains("Archived upcoming meeting"));
        var search=store.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"globalSearch\",\"query\":\"Archived\"}"));
        assertTrue(search.getAsJsonArray("entries").isEmpty());
        db.communityTransaction(()->{try(var q=db.connection().prepareStatement("SELECT body FROM documents WHERE id=?")){q.setString(1,id);try(var row=q.executeQuery()){assertTrue(row.next());assertEquals(saved,Json.parse(row.getString(1)));}}return null;});
    }
    @Test void oldConfigurationCannotStartRetiredWorkOrDeleteItsArchive()throws Exception{
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",true,true);store.seen(actor);
        var notices=new ArrayList<Long>();var originals=new LinkedHashMap<String,String>();
        db.communityTransaction(()->{
            for(String section:List.of("board","events","polls","ideas")){
                String id=UUID.randomUUID().toString();var body=Json.parse("{\"status\":\"deleted\",\"deletedAt\":1,\"startsAt\":1,\"endsAt\":1,\"title\":\"Archived\"}");body.addProperty("section",section);body.addProperty("id",id);body.addProperty("owner",actor.id());originals.put(id,body.toString());
                try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb)")){q.setString(1,id);q.setString(2,section);q.setString(3,body.toString());q.executeUpdate();}
                var notice=Json.parse("{\"title\":\"Saved notice\",\"event\":\"activity\"}");notice.addProperty("section",section);notice.addProperty("target",id);notices.add(new NotificationStore(db).append(actor.id(),notice));
            }return null;
        });
        assertEquals(Set.of("groups"),store.activeModules());
        for(String section:RetiredFeatures.IDS){
            var request=Json.parse("{\"op\":\"create\",\"title\":\"Must not create\"}");request.addProperty("section",section);
            assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->store.request(actor,request)).code());
        }
        for(long id:notices){
            var request=Json.parse("{\"section\":\"notifications\",\"op\":\"noticeOpen\"}");request.addProperty("id",Long.toString(id));
            var notice=store.request(actor,request).getAsJsonObject("notice");assertEquals("Saved notice",Json.str(notice,"title"));assertFalse(notice.get("canNavigate").getAsBoolean());
            request.addProperty("op","noticeNavigate");assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->store.request(actor,request)).code());
        }
        store.reminders(System.currentTimeMillis());store.purgeDeleted(System.currentTimeMillis());
        db.communityTransaction(()->{for(var entry:originals.entrySet())try(var q=db.connection().prepareStatement("SELECT body FROM documents WHERE id=?")){q.setString(1,entry.getKey());try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals(Json.parse(entry.getValue()),Json.parse(rows.getString(1)));}}return null;});
        var group=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Active team\",\"description\":\"Still supported\",\"type\":\"Команда\"}");assertEquals("Active team",Json.str(store.request(actor,group).getAsJsonObject("detail"),"title"));
    }
}
