package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class NotificationStorePostgresTest {
    @TempDir Path root;

    private static JsonObject notice(String section, String target) {
        var body = new JsonObject();
        body.addProperty("section", section);
        body.addProperty("target", target);
        body.addProperty("title", "Saved notification");
        return body;
    }

    private long count(PgDatabase database, String table) throws Exception {
        return database.transaction(() -> {
            try (var query = database.connection().createStatement();
                 var rows = query.executeQuery("SELECT count(*) FROM " + table)) {
                rows.next(); return rows.getLong(1);
            }
        });
    }

    @Test void rollbackKeepsNoticeAndOutboxAtomic() throws Exception {
        var database = TestDatabase.database(root);
        var store = new NotificationStore(database);
        String player = UUID.randomUUID().toString();
        long revision = database.communityRevision();
        assertThrows(IllegalStateException.class, () -> database.transaction(() -> {
            store.append(player, notice("groups", "group"));
            throw new IllegalStateException("Domain failed");
        }));
        assertEquals(0, store.unread(player));
        assertEquals(0, count(database, "community_events"));
        assertEquals(revision, database.communityRevision());
        assertThrows(IllegalStateException.class, () -> store.append(player, notice("groups", "group")));
    }

    @Test void boundedStorageAndPreferencesSurviveNewServiceInstance() throws Exception {
        var database = TestDatabase.database(root);
        var store = new NotificationStore(database);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        database.transaction(() -> {
            store.append(other.toString(), notice("help", "report"));
            for (int i = 0; i < 205; i++) store.append(owner.toString(), notice("groups", "group"));
            store.savePreferences(owner.toString(), Json.parse("{\"muted\":[\"groups\"]}"));
            return null;
        });
        var reopened = new CommunityStore(database, CommunityStore.defaults());
        assertEquals(200, reopened.unread(owner.toString()));
        assertEquals(1, reopened.unread(other.toString()));
        assertEquals(0, reopened.popupSequence(owner.toString()));
        assertTrue(reopened.popupSequence(other.toString()) > 0);
        var summary = reopened.notificationSummary(List.of(owner, other));
        assertEquals(200, summary.get(owner).unread());
        assertEquals(reopened.popupSequence(owner.toString()), summary.get(owner).sequence());
        database.transaction(() -> {
            var preferences = Json.parse("{\"muted\":[]}");
            preferences.addProperty("snoozeUntil", System.currentTimeMillis() + 60000);
            store.savePreferences(owner.toString(), preferences);
            return null;
        });
        assertEquals(0, reopened.popupSequence(owner.toString()));
        database.transaction(() -> { store.savePreferences(owner.toString(), Json.parse("{\"muted\":[]}")); return null; });
        assertTrue(reopened.popupSequence(owner.toString()) > 0);
    }

    @Test void existingMenuReadIsScopedAndRepeatedReadDoesNotPublishAgain() throws Exception {
        var database = TestDatabase.database(root);
        var store = new CommunityStore(database, CommunityStore.defaults());
        String owner = UUID.randomUUID().toString(), other = UUID.randomUUID().toString();
        store.externalNotice(owner, "First", "report");
        store.externalNotice(owner, "Second", "report");
        store.externalNotice(other, "Private", "report");
        var actor = new CommunityStore.Actor(owner, "Owner", false, false);
        var list = Json.parse("{\"section\":\"notifications\",\"op\":\"list\"}");
        String id = store.request(actor, list).getAsJsonArray("entries").get(0).getAsJsonObject().get("id").getAsString();
        var read = Json.parse("{\"section\":\"notifications\",\"op\":\"read\",\"exact\":true}");
        read.addProperty("id", id);
        store.request(new CommunityStore.Actor(other, "Other", false, false), read);
        assertEquals(2, store.unread(owner));
        store.request(actor, read);
        assertEquals(1, store.unread(owner));
        long events = count(database, "community_events");
        store.request(actor, read);
        assertEquals(events, count(database, "community_events"));
        read.addProperty("exact", false);
        store.request(actor, read);
        assertEquals(0, store.unread(owner));
        assertEquals(1, store.unread(other));
        var reconnected = new CommunityStore(database, CommunityStore.defaults());
        assertEquals(0, reconnected.unread(owner));
        assertEquals(1, reconnected.unread(other));
    }

    @Test void disabledDomainLinksCannotActButOldNoticesRemainReadable() throws Exception {
        var database = TestDatabase.database(root);
        String owner = UUID.randomUUID().toString();
        var enabled = new CommunityStore(database, CommunityStore.defaults());
        database.transaction(() -> {
            enabled.note(owner, "events", "old-event", "Saved event", "reminder");
            var legacy = notice("groups", "old-group");
            legacy.addProperty("routeSection", "tasks");legacy.addProperty("routeId", "old-task");legacy.addProperty("routeGroup", "old-group");legacy.addProperty("event", "workAssign");
            new NotificationStore(database).append(owner, legacy);
            return null;
        });
        var policy = ServerSettings.parse(FeatureModulesTest.disabled("events", "tasks", "storage", "board", "polls", "ideas")).modules();
        var disabled = new CommunityStore(database, CommunityStore.defaults(), TaskLimits.defaults(), policy);
        var actor = new CommunityStore.Actor(owner, "Owner", true, true);
        var list = Json.parse("{\"section\":\"notifications\",\"op\":\"list\"}");
        var entries = disabled.request(actor, list).getAsJsonArray("entries");
        assertEquals(2, entries.size());
        long before = count(database, "community_events");
        for (var entry : entries) {
            var action = Json.parse("{\"section\":\"notifications\",\"op\":\"noticeAction\"}");
            action.add("id", entry.getAsJsonObject().get("id"));
            assertThrows(CommunityFailure.class, () -> disabled.request(actor, action));
        }
        assertEquals(before, count(database, "community_events"));
        assertEquals(2, disabled.unread(owner));
        disabled.request(actor, Json.parse("{\"section\":\"notifications\",\"op\":\"read\"}"));
        assertEquals(0, disabled.unread(owner));
        assertEquals(2, disabled.request(actor, list).getAsJsonArray("entries").size());
    }

    @Test void menuPaginationKeepsSnapshotAndBindsCursorToRecipient() throws Exception {
        var database = TestDatabase.database(root);
        var store = new CommunityStore(database, CommunityStore.defaults());
        var actor = new CommunityStore.Actor(UUID.randomUUID().toString(), "Reader", false, false);
        for (int i = 0; i < 12; i++) store.externalNotice(actor.id(), "Notice " + i);
        var query = Json.parse("{\"section\":\"notifications\",\"op\":\"list\"}");
        var first = store.request(actor, query);
        assertEquals(10, first.getAsJsonArray("entries").size());
        query.addProperty("cursor", Json.str(first, "nextCursor"));
        store.externalNotice(actor.id(), "New arrival");
        var reopened = new CommunityStore(database, CommunityStore.defaults());
        var second = reopened.request(actor, query).getAsJsonArray("entries");
        assertEquals(2, second.size());
        var ids = new java.util.HashSet<String>();
        for (var entry : first.getAsJsonArray("entries")) ids.add(Json.str(entry.getAsJsonObject(), "id"));
        for (var entry : second) assertTrue(ids.add(Json.str(entry.getAsJsonObject(), "id")));
        var other = new CommunityStore.Actor(UUID.randomUUID().toString(), "Other", false, false);
        assertThrows(CommunityFailure.class, () -> reopened.request(other, query));
    }
}
