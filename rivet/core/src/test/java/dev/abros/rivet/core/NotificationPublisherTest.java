package dev.abros.rivet.core;

import dev.abros.rivet.api.*;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPublisherTest {
    @Test void namespacesCapacityAndClosedLeases() {
        try (var publisher = new NotificationPublisher(null)) {
            var owner = publisher.owner("example");
            var type = owner.register("mail");
            assertEquals("example:mail", type.id());
            assertEquals(ApiException.Code.CONFLICT, assertThrows(ApiException.class,
                    () -> publisher.owner("example").register("mail")).code());
            assertEquals("another:mail", publisher.owner("another").register("mail").id());
            for (int i = 0; i < 15; i++) owner.register("type" + i);
            assertEquals(ApiException.Code.BUSY, assertThrows(ApiException.class, () -> owner.register("overflow")).code());
            owner.close();
            var replacement = publisher.owner("example").register("mail");
            type.close(); // An old lease must not remove the new registration.
            assertEquals(ApiException.Code.CONFLICT, assertThrows(ApiException.class,
                    () -> publisher.owner("example").register("mail")).code());
            assertEquals(ApiException.Code.UNAVAILABLE, failure(type.put(UUID.randomUUID(), "key", new NotificationsApi.Message("Title", ""), 0)));
            replacement.close();
        }
    }
    @Test void invalidRequestsAndStoppedRuntimeDoNotReachStorage() {
        var publisher = new NotificationPublisher(null);
        var owner = publisher.owner("example");
        var type = owner.register("mail");
        UUID recipient = UUID.randomUUID();
        assertEquals(ApiException.Code.INVALID_ARGUMENT, failure(type.put(recipient, "key", new NotificationsApi.Message("x".repeat(301), ""), 0)));
        assertEquals(ApiException.Code.INVALID_ARGUMENT, failure(type.put(null, "key", new NotificationsApi.Message("Title", ""), 0)));
        assertEquals(ApiException.Code.INVALID_ARGUMENT, failure(type.revoke(recipient, "key", 0)));
        assertEquals(ApiException.Code.INVALID_ARGUMENT, failure(type.put(recipient, "other:key", new NotificationsApi.Message("Title", ""), 0)));
        publisher.close();
        assertEquals(ApiException.Code.UNAVAILABLE, failure(type.put(recipient, "key", new NotificationsApi.Message("Title", ""), 0)));
        assertEquals(ApiException.Code.UNAVAILABLE, assertThrows(ApiException.class, () -> owner.register("next")).code());
    }
    static ApiException.Code failure(java.util.concurrent.CompletableFuture<?> result) {
        return assertInstanceOf(ApiException.class, assertThrows(CompletionException.class, result::join).getCause()).code();
    }
}
