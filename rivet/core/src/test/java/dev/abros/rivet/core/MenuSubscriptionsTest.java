package dev.abros.rivet.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSubscriptionsTest {
    @Test void renewalsDoNotFloodButViewChangesAndPeriodicResyncDo() {
        var subscriptions = new MenuSubscriptions<Object>();
        UUID player = UUID.randomUUID(); Object session = new Object();
        assertTrue(subscriptions.subscribe(player, session, "groups", "one", 100));
        assertFalse(subscriptions.subscribe(player, session, "groups", "one", 200));
        assertEquals(new MenuSubscriptions.View("groups", "one"), subscriptions.current(player, session, 30100));
        assertTrue(subscriptions.subscribe(player, session, "groups", "one", 30100));
        assertTrue(subscriptions.subscribe(player, session, "groups", "two", 30200));
        assertNull(subscriptions.current(player, session, 60200));
    }

    @Test void closingViewKeepsNegotiationButReconnectInheritsNeither() {
        var subscriptions = new MenuSubscriptions<Object>();
        UUID player = UUID.randomUUID(); Object old = new Object(), fresh = new Object();
        subscriptions.ready(player, old);
        subscriptions.subscribe(player, old, "notifications", "", 100);
        assertFalse(subscriptions.subscribe(player, old, "", "", 200));
        assertTrue(subscriptions.isReady(player, old));
        assertNull(subscriptions.current(player, old, 200));
        assertFalse(subscriptions.isReady(player, fresh));
        assertTrue(subscriptions.subscribe(player, fresh, "notifications", "", 300));
        subscriptions.subscribe(player, old, "", "", 400);
        assertNotNull(subscriptions.current(player, fresh, 400));
        assertNull(subscriptions.current(player, old, 400));
    }

    @Test void expiryAndRemovalAreBoundToOneRecipient() {
        var subscriptions = new MenuSubscriptions<Object>();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); Object a = new Object(), b = new Object();
        subscriptions.ready(first, a); subscriptions.ready(second, b);
        subscriptions.subscribe(first, a, "home", "", 0);
        subscriptions.subscribe(second, b, "home", "", 1000);
        assertNull(subscriptions.current(first, a, 30000));
        assertNotNull(subscriptions.current(second, b, 30000));
        subscriptions.remove(first);
        assertFalse(subscriptions.isReady(first, a));
        assertTrue(subscriptions.isReady(second, b));
        subscriptions.clear();
        assertFalse(subscriptions.isReady(second, b));
        assertNull(subscriptions.current(second, b, 30000));
    }
}
