package dev.abros.rivet.core;

/** Coalesces committed changes without querying storage on every server tick. */
public final class ChangeDeliverySchedule {
    private long revision = -1, lastAttempt;
    private boolean started, backlog;
    public boolean due(long currentRevision, long now) {
        if (!started) return true;
        long elapsed = now - lastAttempt;
        return elapsed >= 1000 || (backlog || currentRevision != revision) && elapsed >= 100;
    }
    public void backlog() { backlog = true; }
    /** Called only after a delivery worker has accepted ownership of the attempt. */
    public void started(long currentRevision, long now) {
        revision = currentRevision; lastAttempt = now; started = true; backlog = false;
    }
}
