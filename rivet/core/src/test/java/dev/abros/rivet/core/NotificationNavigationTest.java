package dev.abros.rivet.core;

import dev.abros.rivet.api.*;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class NotificationNavigationTest {
    @TempDir Path root;
    private JsonObject request(String op,long id,long revision) {
        var value=new JsonObject();value.addProperty("section","notifications");value.addProperty("op",op);
        value.addProperty("id",Long.toString(id));value.addProperty("sourceRevision",revision);return value;
    }
    private UUID group(CommunityStore store,CommunityStore.Actor owner) throws Exception {
        var create=Json.parse("{\"section\":\"groups\",\"op\":\"create\",\"title\":\"Team\",\"description\":\"Description\",\"type\":\"Команда\"}");
        return UUID.fromString(Json.str(store.request(owner,create).getAsJsonObject("detail"),"id"));
    }
    @Test void freshOpenAndNavigationRejectForeignStaleExpiredAndDisabledTargets() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);
        var other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);
        store.seen(actor);store.seen(other);UUID group=group(store,other);
        try(var publisher=new NotificationPublisher(db)) {
            var type=publisher.owner("example").register("mail");
            var message=new NotificationsApi.Message("Team news","Details",Map.of(),0,new NotificationsApi.OpenGroup(group));
            var receipt=type.put(UUID.fromString(actor.id()),"link",message,0).join();
            assertEquals(receipt,type.put(UUID.fromString(actor.id()),"link",message,0).join());
            var open=request("noticeOpen",receipt.id(),0);
            assertThrows(CommunityFailure.class,()->store.request(other,open));
            assertEquals(1,store.unread(actor.id()));
            var updated=type.put(UUID.fromString(actor.id()),"link",new NotificationsApi.Message("Current title","Details",Map.of(),0,new NotificationsApi.OpenGroup(group)),receipt.revision()).join();
            var fresh=store.request(actor,open).getAsJsonObject("notice");
            assertEquals("Current title",Json.str(fresh,"title"));assertEquals(updated.revision(),fresh.get("sourceRevision").getAsLong());
            assertEquals(0,store.unread(actor.id()));
            long afterOpen=db.communityRevision();store.request(actor,open);assertEquals(afterOpen,db.communityRevision());
            var navigate=request("noticeNavigate",receipt.id(),receipt.revision());
            assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->store.request(actor,navigate)).code());
            navigate.addProperty("sourceRevision",updated.revision());
            // A client cannot replace the persisted destination with another ID or command.
            navigate.addProperty("target",UUID.randomUUID().toString());navigate.addProperty("command","op Reader");
            assertEquals(group.toString(),Json.str(store.request(actor,navigate).getAsJsonObject("noticeRoute"),"id"));
            db.transaction(()->{try(var q=db.connection().prepareStatement("SELECT count(*) FROM community_relations WHERE document=? AND actor=? AND kind='members'")) {
                q.setString(1,group.toString());q.setString(2,actor.id());try(var rows=q.executeQuery()){rows.next();assertEquals(0,rows.getInt(1));}
            }return null;});
            var disabled=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),ServerSettings.parse(FeatureModulesTest.disabled("groups")).modules());
            assertThrows(CommunityFailure.class,()->disabled.request(actor,navigate));
            db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=jsonb_set(body,'{status}','\"hidden\"') WHERE id=?")){q.setString(1,group.toString());q.executeUpdate();}return null;});
            assertThrows(CommunityFailure.class,()->store.request(actor,navigate));
            db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE notices SET expires_at=1 WHERE id=?")){q.setLong(1,receipt.id());q.executeUpdate();}return null;});
            assertThrows(CommunityFailure.class,()->store.request(actor,open));
            assertThrows(CommunityFailure.class,()->store.request(actor,navigate));
        }
    }
    @Test void legacyHistoryUsesFreshOwnedRecordAndRechecksDestination() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);
        var other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);
        store.seen(actor);store.seen(other);UUID group=group(store,other);
        var body=Json.parse("{\"section\":\"groups\",\"title\":\"Legacy\",\"event\":\"activity\"}");
        body.addProperty("target",group.toString());
        long id=db.communityTransaction(()->new NotificationStore(db).append(actor.id(),body));
        assertThrows(CommunityFailure.class,()->store.request(other,request("noticeOpen",id,0)));
        var fresh=store.request(actor,request("noticeOpen",id,0)).getAsJsonObject("notice");
        assertEquals("Legacy",Json.str(fresh,"title"));assertTrue(fresh.get("canNavigate").getAsBoolean());
        var navigate=request("noticeNavigate",id,0);navigate.addProperty("target",UUID.randomUUID().toString());
        assertEquals(group.toString(),Json.str(store.request(actor,navigate).getAsJsonObject("noticeRoute"),"id"));
        var disabled=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),ServerSettings.parse(FeatureModulesTest.disabled("groups")).modules());
        assertThrows(CommunityFailure.class,()->disabled.request(actor,navigate));
        // Turning off the destination does not hide the player's saved history.
        assertEquals("Legacy",Json.str(disabled.request(actor,request("noticeOpen",id,0)).getAsJsonObject("notice"),"title"));
    }
    @Test void reportActionChecksOwnershipForTypedAndLegacyNotices() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);
        var other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);store.seen(actor);store.seen(other);
        String report=new CommunityReports(store).submit(UUID.fromString(actor.id()),actor.name(),Json.parse("{\"message\":\"Help\"}"),System.currentTimeMillis());
        try(var publisher=new NotificationPublisher(db)) {
            var type=publisher.owner("example").register("support");
            var message=new NotificationsApi.Message("Reply","Body",Map.of(),0,new NotificationsApi.OpenReport(UUID.fromString(report)));
            var own=type.put(UUID.fromString(actor.id()),"reply",message,0).join();
            var foreign=type.put(UUID.fromString(other.id()),"reply",message,0).join();
            assertEquals("help",Json.str(store.request(actor,request("noticeNavigate",own.id(),own.revision())).getAsJsonObject("noticeRoute"),"section"));
            assertThrows(IllegalArgumentException.class,()->store.request(other,request("noticeNavigate",foreign.id(),foreign.revision())));
            var legacy=Json.parse("{\"section\":\"help\",\"title\":\"Reply\"}");legacy.addProperty("target",report);
            long id=db.communityTransaction(()->new NotificationStore(db).append(actor.id(),legacy));
            assertEquals(report,Json.str(store.request(actor,request("noticeNavigate",id,0)).getAsJsonObject("noticeRoute"),"id"));
            var disabled=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),ServerSettings.parse(FeatureModulesTest.disabled("reports")).modules());
            assertThrows(Exception.class,()->disabled.request(actor,request("noticeNavigate",own.id(),own.revision())));
        }
    }
    @Test void ownPublicationActionNeverGrantsAccessToForeignDraftAndRejectsWithdrawal() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);
        var other=new CommunityStore.Actor(UUID.randomUUID().toString(),"Other",false,false);store.seen(actor);store.seen(other);
        UUID id=UUID.randomUUID();var submit=Json.parse("{\"section\":\"home\",\"op\":\"mapObjectSubmit\",\"revision\":0,\"type\":\"marker\"}");submit.addProperty("id",id.toString());
        submit.add("draft",dev.abros.rivet.core.map.MapMarkerLink.json(new dev.abros.rivet.core.map.MapMarker(id,"minecraft:overworld","Private",1,64,2,0xffffff,"pin")));
        store.request(actor,submit);
        try(var publisher=new NotificationPublisher(db)) {
            var type=publisher.owner("example").register("map");
            var message=new NotificationsApi.Message("Publication","Body",Map.of(),0,new NotificationsApi.OpenOwnPublication(id));
            var own=type.put(UUID.fromString(actor.id()),"own",message,0).join();var foreign=type.put(UUID.fromString(other.id()),"foreign",message,0).join();
            var route=store.request(actor,request("noticeNavigate",own.id(),own.revision())).getAsJsonObject("noticeRoute");
            assertEquals("map-publications",Json.str(route,"section"));assertEquals("marker",Json.str(route,"type"));assertFalse(route.has("draft"));
            assertThrows(CommunityFailure.class,()->store.request(other,request("noticeNavigate",foreign.id(),foreign.revision())));
            var withdraw=Json.parse("{\"section\":\"home\",\"op\":\"mapObjectWithdraw\",\"revision\":1}");withdraw.addProperty("id",id.toString());store.request(actor,withdraw);
            assertThrows(CommunityFailure.class,()->store.request(actor,request("noticeNavigate",own.id(),own.revision())));
            assertTrue(store.request(actor,request("noticeOpen",own.id(),0)).has("notice"));
        }
    }
    @Test void removalOfActionInvalidatesPreviouslyDisplayedDestination() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
        UUID group=group(store,actor);
        try(var publisher=new NotificationPublisher(db)) {
            var type=publisher.owner("example").register("mail");
            var withAction=new NotificationsApi.Message("Title","Body",Map.of(),0,new NotificationsApi.OpenGroup(group));
            var receipt=type.put(UUID.fromString(actor.id()),"link",withAction,0).join();
            var plain=new NotificationsApi.Message("Title","Body");
            assertEquals(ApiException.Code.CONFLICT,NotificationPublisherTest.failure(type.put(UUID.fromString(actor.id()),"link",plain,0)));
            var updated=type.put(UUID.fromString(actor.id()),"link",plain,receipt.revision()).join();
            assertFalse(store.request(actor,request("noticeOpen",receipt.id(),0)).getAsJsonObject("notice").has("notificationAction"));
            assertThrows(CommunityFailure.class,()->store.request(actor,request("noticeNavigate",receipt.id(),updated.revision())));
        }
    }
    @Test void announcementWithoutObjectOpensPublicServerPageAndChecksModule() throws Exception {
        var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
        var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
        var body=Json.parse("{\"section\":\"home\",\"title\":\"Announcement\",\"routeSection\":\"server\",\"routeId\":\"\",\"event\":\"important\"}");
        long id=db.communityTransaction(()->new NotificationStore(db).append(actor.id(),body));
        assertTrue(store.request(actor,request("noticeOpen",id,0)).getAsJsonObject("notice").get("canNavigate").getAsBoolean());
        var route=store.request(actor,request("noticeNavigate",id,0)).getAsJsonObject("noticeRoute");
        assertEquals("server",Json.str(route,"section"));assertEquals("",Json.str(route,"id"));
        var disabled=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),ServerSettings.parse(FeatureModulesTest.disabled("server")).modules());
        assertThrows(CommunityFailure.class,()->disabled.request(actor,request("noticeNavigate",id,0)));
        assertEquals("Announcement",Json.str(disabled.request(actor,request("noticeOpen",id,0)).getAsJsonObject("notice"),"title"));
    }
}
