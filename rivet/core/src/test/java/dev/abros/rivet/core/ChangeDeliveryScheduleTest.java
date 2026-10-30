package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChangeDeliveryScheduleTest {
    @Test void newCommitsDoNotWaitForPeriodicPoll() {
        var schedule = new ChangeDeliverySchedule();
        assertTrue(schedule.due(0, 0)); schedule.started(0, 0);
        assertFalse(schedule.due(0, 100));
        assertFalse(schedule.due(1, 99));
        assertTrue(schedule.due(1, 100)); schedule.started(1, 100);
        assertFalse(schedule.due(1, 200));
        assertTrue(schedule.due(2, 200));
    }
    @Test void fullBatchesDrainWithoutWaitingForAnotherCommit() {
        var schedule=new ChangeDeliverySchedule();schedule.started(1,0);schedule.backlog();
        assertFalse(schedule.due(1,99));assertTrue(schedule.due(1,100));
        schedule.started(1,100);assertFalse(schedule.due(1,200));
    }
    @Test void unsuccessfulOrExternalChangesStillGetPeriodicRecovery() {
        var schedule = new ChangeDeliverySchedule(); schedule.started(7, 500);
        assertFalse(schedule.due(7, 1499));
        assertTrue(schedule.due(7, 1500));
        // A busy worker does not consume a wakeup; caller has not started it.
        assertTrue(schedule.due(8, 1501));
    }
}
