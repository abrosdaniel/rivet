package dev.abros.rivet.core;

import dev.abros.rivet.api.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.abros.rivet.core.NotificationPublisherTest.failure;

@Tag("postgres")
class NotificationPublisherPostgresTest {
    @TempDir Path root;

    @Test void offlineHistoryReplayReadAndReopenUseExistingConsumer() throws Exception {
        var database = TestDatabase.database(root);
        UUID recipient = UUID.randomUUID();
        var store = new CommunityStore(database, CommunityStore.defaults());
        var actor = new CommunityStore.Actor(recipient.toString(), "Reader", false, false);
        var list = Json.parse("{\"section\":\"notifications\",\"op\":\"list\"}");
        var original = new NotificationsApi.Message("Delivery", "Your package is ready");
        NotificationsApi.Receipt first, updated;
        try (var publisher = new NotificationPublisher(database)) {
            var type = publisher.owner("example").register("mail");
            first = type.put(recipient, "package", original, 0).join();
            long sequence = database.communityRevision();
            assertEquals(first, type.put(recipient, "package", original, 0).join());
            assertEquals(sequence, database.communityRevision());
            var entries = store.request(actor, list).getAsJsonArray("entries");
            assertEquals(1, entries.size());
            assertEquals(original.body(), Json.str(entries.get(0).getAsJsonObject(), "body"));
            var read = Json.parse("{\"section\":\"notifications\",\"op\":\"read\",\"exact\":true}");
            read.addProperty("id", Long.toString(first.id())); store.request(actor, read);
            updated = type.put(recipient, "package", new NotificationsApi.Message("Delivered", "Collected"), first.revision()).join();
            assertEquals(first.id(), updated.id()); assertTrue(updated.revision() > first.revision());
            assertEquals(0, store.unread(recipient.toString()));
            assertEquals(ApiException.Code.CONFLICT, failure(type.put(recipient, "package", original, first.revision())));
        }
        try (var restarted = new NotificationPublisher(database)) {
            var type = restarted.owner("example").register("mail");
            assertEquals(1, store.request(actor, list).getAsJsonArray("entries").size());
            type.revoke(recipient, "package", updated.revision()).join();
            assertTrue(store.request(actor, list).getAsJsonArray("entries").isEmpty());
            long sequence = database.communityRevision();
            type.revoke(recipient, "package", updated.revision()).join();
            assertEquals(sequence, database.communityRevision());
            var recreated = type.put(recipient, "package", original, 0).join();
            assertTrue(recreated.revision() > updated.revision());
            assertEquals(ApiException.Code.CONFLICT, failure(type.revoke(recipient, "package", updated.revision())));
        }
    }

    @Test void ownerTypeAndRecipientNeverModifyEachOthersNotices() throws Exception {
        var database = TestDatabase.database(root);
        UUID recipient = UUID.randomUUID(), other = UUID.randomUUID();
        try (var publisher = new NotificationPublisher(database)) {
            var a = publisher.owner("alpha").register("mail");
            var b = publisher.owner("bravo").register("mail");
            var c = publisher.owner("alpha").register("alerts");
            var message = new NotificationsApi.Message("Private", "Contents");
            var first = a.put(recipient, "key", message, 0).join();
            b.revoke(recipient, "key", first.revision()).join();
            c.revoke(recipient, "key", first.revision()).join();
            a.revoke(other, "key", first.revision()).join();
            assertEquals(ApiException.Code.CONFLICT, failure(b.put(recipient, "key", message, first.revision())));
            assertEquals(first, a.put(recipient, "key", message, 0).join());
            var second = b.put(recipient, "key", message, 0).join();
            assertNotEquals(first.id(), second.id());
            var store = new CommunityStore(database, CommunityStore.defaults());
            assertEquals(2, store.unread(recipient.toString())); assertEquals(0, store.unread(other.toString()));
            a.revoke(recipient, "key", first.revision()).join();
            assertEquals(1, store.unread(recipient.toString()));
        }
    }

    @Test void concurrentCreatesDeduplicateAndRetentionRemainsBounded() throws Exception {
        var database = TestDatabase.database(root);
        UUID recipient = UUID.randomUUID();
        var message = new NotificationsApi.Message("Title", "Body");
        try (var left = new NotificationPublisher(database); var right = new NotificationPublisher(database)) {
            var a = left.owner("example").register("mail");
            var b = right.owner("example").register("mail");
            var first = a.put(recipient, "same", message, 0);
            var second = b.put(recipient, "same", message, 0);
            assertEquals(first.join(), second.join());
            for (int i = 0; i < 205; i++) a.put(recipient, "key" + i, message, 0).join();
            assertEquals(200, new CommunityStore(database, CommunityStore.defaults()).unread(recipient.toString()));
        }
    }
}
