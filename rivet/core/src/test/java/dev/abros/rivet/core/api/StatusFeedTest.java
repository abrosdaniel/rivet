package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusFeedTest {
    private static ServerStatus status(int players) {
        return new ServerStatus(players, 100, false, 0, Set.of("server.status"));
    }
    private static void code(ApiException.Code code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).code());
    }

    @Test void snapshotAndReplayHaveNoGapAndPageWithoutSkippingChanges() {
        try (var feed = new StatusFeed(10, Clock.systemUTC(), status(0))) {
            var snapshot = feed.serverStatus();
            feed.publish(status(1)); feed.publish(status(2)); feed.publish(status(3));
            var first = feed.serverStatusEvents(snapshot.cursor(), 2);
            assertEquals(java.util.List.of(1, 2), first.changes().stream().map(x -> x.status().onlinePlayers()).toList());
            assertTrue(first.hasMore());
            var second = feed.serverStatusEvents(first.nextCursor(), 2);
            assertEquals(3, second.changes().getFirst().status().onlinePlayers());
            assertFalse(second.hasMore());
            assertEquals(feed.serverStatus().cursor(), second.nextCursor());
            assertTrue(feed.serverStatusEvents(second.nextCursor(), 2).changes().isEmpty());
        }
    }

    @Test void retentionAndRestartForceExplicitResync() {
        try (var feed = new StatusFeed(2, Clock.systemUTC(), status(0))) {
            var initial = feed.serverStatus().cursor();
            feed.publish(status(1)); var boundary = feed.serverStatus().cursor();
            feed.publish(status(2)); feed.publish(status(3));
            code(ApiException.Code.RESYNC_REQUIRED, () -> feed.serverStatusEvents(initial, 10));
            assertEquals(2, feed.serverStatusEvents(boundary, 10).changes().size());
            code(ApiException.Code.RESYNC_REQUIRED, () -> feed.serverStatusEvents(new ApiCursor(UUID.randomUUID(), 3), 10));
            code(ApiException.Code.RESYNC_REQUIRED, () -> feed.serverStatusEvents(new ApiCursor(initial.stream(), 4), 10));
        }
    }

    @Test void unchangedTicksDoNotExpireReadersAndStoppedHandlesCannotLeakState() {
        var feed = new StatusFeed(1, Clock.systemUTC(), status(0));
        var initial = feed.serverStatus();
        for (int i = 0; i < 1000; i++) feed.publish(status(0));
        assertSame(initial, feed.serverStatus());
        assertTrue(feed.serverStatusEvents(initial.cursor(), 1).changes().isEmpty());
        feed.close(); feed.close();
        code(ApiException.Code.UNAVAILABLE, feed::serverStatus);
        code(ApiException.Code.UNAVAILABLE, () -> feed.serverStatusEvents(initial.cursor(), 1));
        code(ApiException.Code.UNAVAILABLE, () -> feed.publish(status(1)));
    }

    @Test void immutableValuesAndLimitsProtectSharedState() {
        var capabilities = new HashSet<>(Set.of("server.status"));
        var status = new ServerStatus(0, 100, false, 0, capabilities);
        capabilities.clear();
        assertEquals(Set.of("server.status"), status.capabilities());
        assertThrows(UnsupportedOperationException.class, () -> status.capabilities().clear());
        try (var feed = new StatusFeed(2, Clock.systemUTC(), status)) {
            var cursor = feed.serverStatus().cursor();
            code(ApiException.Code.INVALID_ARGUMENT, () -> feed.serverStatusEvents(cursor, 0));
            code(ApiException.Code.INVALID_ARGUMENT, () -> feed.serverStatusEvents(cursor, 257));
            code(ApiException.Code.INVALID_ARGUMENT, () -> feed.serverStatusEvents(null, 1));
            feed.publish(status(1));
            assertThrows(UnsupportedOperationException.class, () -> feed.serverStatusEvents(cursor, 2).changes().clear());
        }
    }

    @Test void concurrentSnapshotsAndReplayRemainConsistent() throws Exception {
        try (var feed = new StatusFeed(4096, Clock.systemUTC(), status(0));
             var workers = Executors.newFixedThreadPool(3)) {
            var writer = workers.submit(() -> { for (int i = 1; i <= 2000; i++) feed.publish(status(i)); });
            var reader = workers.submit(() -> {
                for (int i = 0; i < 2000; i++) {
                    var snapshot = feed.serverStatus();
                    assertEquals(snapshot.cursor().sequence(), snapshot.status().onlinePlayers());
                    var events = feed.serverStatusEvents(snapshot.cursor(), 256);
                    long sequence = snapshot.cursor().sequence();
                    for (var change : events.changes()) {
                        assertEquals(++sequence, change.cursor().sequence());
                        assertEquals(sequence, change.status().onlinePlayers());
                    }
                    assertEquals(sequence, events.nextCursor().sequence());
                }
            });
            writer.get(); reader.get();
        }
    }
}
