package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class CommunityHudTest {
 @TempDir Path world;CommunityStore store;CommunityStore.Actor actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Player",false,false),other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);
 @BeforeEach void start()throws Exception{store=new CommunityStore(TestDatabase.database(world),CommunityStore.defaults());store.seen(actor);store.seen(other);}
 @AfterEach void close()throws Exception{store.close();}
 JsonObject request(){var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","hud");j.addProperty("request",UUID.randomUUID().toString());return j;}
 @Test void initialSnapshotSummarizesBacklogAndFeedIsActorScoped()throws Exception{store.externalNotice(actor.id(),"Old");store.externalNotice(other.id(),"Private");var first=store.request(actor,request());assertEquals("hud",Json.str(first,"kind"));assertEquals(1,first.get("unread").getAsInt());assertTrue(first.getAsJsonArray("notices").isEmpty());store.externalNotice(actor.id(),"New");var next=request();next.add("since",first.get("sequence"));var reply=store.request(actor,next);assertEquals(1,reply.getAsJsonArray("notices").size());assertEquals("New",Json.str(reply.getAsJsonArray("notices").get(0).getAsJsonObject(),"title"));assertFalse(reply.get("more").getAsBoolean());}
 @Test void digestCountsOnlyUnreadForActorWithoutReadingThem()throws Exception{store.transaction(()->{store.note(actor.id(),"events","event","Event");store.note(actor.id(),"groups","group","Invite");store.note(actor.id(),"groups","other-group","Invite two");store.note(other.id(),"events","private","Private");});var snapshot=store.request(actor,request());var digest=snapshot.getAsJsonArray("digest");assertEquals(2,digest.size());assertEquals("groups",Json.str(digest.get(0).getAsJsonObject(),"section"));assertEquals(2,digest.get(0).getAsJsonObject().get("count").getAsInt());assertEquals(3,store.unread(actor.id()));assertEquals(1,store.unread(other.id()));var read=new JsonObject();read.addProperty("section","notifications");read.addProperty("op","read");store.request(actor,read);assertTrue(store.request(actor,request()).getAsJsonArray("digest").isEmpty());}
 @Test void feedPagesWithoutDroppingNotices()throws Exception{var first=store.request(actor,request());for(int n=0;n<65;n++)store.externalNotice(actor.id(),"Notice "+n);var next=request();next.add("since",first.get("sequence"));var page=store.request(actor,next);assertEquals(50,page.getAsJsonArray("notices").size());assertTrue(page.get("more").getAsBoolean());next.add("since",page.get("sequence"));page=store.request(actor,next);assertEquals(15,page.getAsJsonArray("notices").size());assertFalse(page.get("more").getAsBoolean());}


 @Test void dismissReadsOnlyItsOwnNotice()throws Exception{store.transaction(()->{store.note(actor.id(),"events","same","One");store.note(actor.id(),"events","same","Two");store.note(other.id(),"events","same","Other");});var query=request();query.addProperty("since",0);var notice=store.request(actor,query).getAsJsonArray("notices").get(0).getAsJsonObject();var read=new JsonObject();read.addProperty("section","notifications");read.addProperty("op","read");read.addProperty("id",Json.str(notice,"id"));read.addProperty("exact",true);store.request(actor,read);assertEquals(1,store.unread(actor.id()));assertEquals(1,store.unread(other.id()));}
 @Test void pollingIsReadOnlyAndRejectsNegativeCursor(){assertTrue(MenuRequests.read(request()));var q=request();q.addProperty("since",-1);assertThrows(IllegalArgumentException.class,()->store.request(actor,q));}
}
