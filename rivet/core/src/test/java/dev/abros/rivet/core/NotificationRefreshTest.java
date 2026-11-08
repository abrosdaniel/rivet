package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationRefreshTest {
    @Test void reconnectRefreshSurvivesCompletionOfOlderRead() {
        var refresh = new NotificationRefresh();
        var pending = refresh.begin(100);
        assertTrue(pending.required());
        refresh.invalidate();
        refresh.complete(pending);
        var fresh = refresh.begin(200);
        assertTrue(fresh.required());
        refresh.complete(fresh);
        assertFalse(refresh.begin(201).required());
    }
    @Test void periodicReconciliationAndFailedReadRemainDue() {
        var refresh = new NotificationRefresh();
        refresh.begin(100);
        var retry = refresh.begin(200);
        assertTrue(retry.required());
        refresh.complete(retry);
        assertFalse(refresh.begin(5199).required());
        assertTrue(refresh.begin(5200).required());
    }
}
