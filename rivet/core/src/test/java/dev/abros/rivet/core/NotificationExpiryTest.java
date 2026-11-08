package dev.abros.rivet.core;

import dev.abros.rivet.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class NotificationExpiryTest {
    @TempDir Path root;

    @Test void expiryFiltersAllConsumersBeforeCleanupAndPublishesInvalidation() throws Exception {
        var db=TestDatabase.database(root);
        UUID player=UUID.randomUUID();
        var actor=new CommunityStore.Actor(player.toString(),"Reader",false,false);
        var store=new CommunityStore(db,CommunityStore.defaults());
        try(var publisher=new NotificationPublisher(db)) {
            var type=publisher.owner("example").register("mail");
            store.externalNotice(player.toString(),"Keep");
            var receipt=type.put(player,"expiring",new NotificationsApi.Message("Temporary","Body",Map.of(),System.currentTimeMillis()+60000),0).join();
            assertEquals(2,store.unread(player.toString()));
            // Advance the persisted deadline without sleeping or depending on scheduler timing.
            db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE notices SET expires_at=1,body=jsonb_set(body,'{expiresAt}','1') WHERE id=?")){q.setLong(1,receipt.id());q.executeUpdate();}return null;});
            var reopened=new CommunityStore(db,CommunityStore.defaults());
            assertEquals(1,reopened.unread(player.toString()));
            assertEquals(1,reopened.notificationSummary(List.of(player)).get(player).unread());
            var list=Json.parse("{\"section\":\"notifications\",\"op\":\"list\"}");
            assertEquals(1,reopened.request(actor,list).getAsJsonArray("entries").size());
            var hud=Json.parse("{\"section\":\"home\",\"op\":\"hud\",\"since\":0}");
            var feed=reopened.request(actor,hud);
            assertEquals(1,feed.getAsJsonArray("notices").size());
            assertFalse(feed.get("more").getAsBoolean());
            assertTrue(feed.get("sequence").getAsLong()>=receipt.id());
            var resources=new CommunityApiResources(db,reopened,ServerSettings.parse(ServerSettings.template()));
            assertEquals(1,resources.read(actor,"me/notifications",Map.of()).getAsJsonArray("items").size());
            long before=db.communityRevision();
            assertEquals(1,publisher.maintain().join());
            assertFalse(reopened.pendingChanges().isEmpty());
            assertTrue(db.communityRevision()>before);
            assertEquals(0,new NotificationStore(db).expire());
            var next=type.put(player,"expiring",new NotificationsApi.Message("New","Body"),0).join();
            assertNotEquals(receipt.id(),next.id());
            assertEquals(ApiException.Code.CONFLICT,NotificationPublisherTest.failure(type.revoke(player,"expiring",receipt.revision())));
        }
    }

    @Test void cleanupIsBoundedAtomicAndKeepsPermanentHistory() throws Exception {
        var db=TestDatabase.database(root);var notices=new NotificationStore(db);
        db.transaction(()->{
            for(int i=0;i<260;i++) {
                var body=Json.parse("{\"title\":\"Expired\",\"section\":\"notifications\",\"expiresAt\":1}");
                notices.append("recipient"+(i%2),body);
            }
            notices.append("permanent",Json.parse("{\"title\":\"Old without TTL\"}"));
            return null;
        });
        long before=db.communityRevision();
        assertThrows(IllegalStateException.class,()->db.transaction(()->{assertEquals(256,notices.expire());throw new IllegalStateException("rollback");}));
        assertEquals(before,db.communityRevision());
        assertEquals(256,notices.expire());assertEquals(4,notices.expire());assertEquals(0,notices.expire());
        assertEquals(1,notices.unread("permanent"));
    }

    @Test void migrationPreservesExistingNoticeBodyAndReadState() throws Exception {
        var db=TestDatabase.database(root);
        db.transaction(()->{
            try(var q=db.connection().createStatement()) {
                q.execute("ALTER TABLE notices DROP COLUMN expires_at CASCADE");
                q.execute("DROP TABLE auth_backup_codes,auth_factors");q.execute("ALTER TABLE skin_profiles DROP COLUMN undo_active,DROP COLUMN undo_slim,DROP COLUMN undo_available");q.execute("DELETE FROM schema_versions WHERE version>=10");
                q.execute("INSERT INTO notices(recipient,body,read) VALUES('legacy','{\"title\":\"Saved\"}',1)");
            }
            DatabaseMigrations.apply(db);DatabaseMigrations.apply(db);
            try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT body->>'title',read,expires_at FROM notices WHERE recipient='legacy'")) {
                assertTrue(rows.next());assertEquals("Saved",rows.getString(1));assertEquals(1,rows.getInt(2));assertEquals(0,rows.getLong(3));
            }
            return null;
        });
    }
}
