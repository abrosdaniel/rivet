package dev.abros.rivet.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionsTest {
    @Test void delayedDeliveryAndLogoutCannotAffectReconnectedPlayer() {
        var sessions = new PlayerSessions<Object>();
        UUID player = UUID.randomUUID();
        Object oldConnection = new Object(), newConnection = new Object();
        var previous = sessions.open(player, oldConnection);
        var pending = sessions.snapshot();
        var current = sessions.open(player, newConnection);
        assertFalse(sessions.current(pending.get(player)));
        assertFalse(sessions.current(previous));
        assertTrue(sessions.current(current));
        assertFalse(sessions.close(player, oldConnection));
        assertTrue(sessions.matches(player, newConnection));
        assertTrue(sessions.close(player, newConnection));
        assertFalse(sessions.current(current));
    }

    @Test void resetInvalidatesAllPendingWorkEvenIfConnectionObjectIsReused() {
        var sessions = new PlayerSessions<Object>();
        UUID player = UUID.randomUUID();
        Object connection = new Object();
        var old = sessions.open(player, connection);
        sessions.clear();
        assertTrue(sessions.snapshot().isEmpty());
        var fresh = sessions.open(player, connection);
        assertFalse(sessions.current(old));
        assertTrue(sessions.current(fresh));
        assertThrows(UnsupportedOperationException.class, () -> sessions.snapshot().clear());
    }

    @Test void oneReconnectDoesNotInvalidateOtherRecipients() {
        var sessions = new PlayerSessions<Object>();
        for (int i = 0; i < 100; i++) sessions.open(UUID.randomUUID(), new Object());
        var delivery = sessions.snapshot();
        UUID player = delivery.keySet().iterator().next();
        sessions.open(player, new Object());
        assertEquals(99, delivery.values().stream().filter(sessions::current).count());
        assertEquals(100, sessions.snapshot().values().stream().filter(sessions::current).count());
    }

    @Test void connectionIdentityCannotBeReplacedByEquals() {
        var sessions = new PlayerSessions<String>();
        UUID player = UUID.randomUUID();
        String first = new String("same"), second = new String("same");
        sessions.open(player, first);
        assertFalse(sessions.matches(player, second));
        assertFalse(sessions.close(player, second));
        assertTrue(sessions.matches(player, first));
    }
}
