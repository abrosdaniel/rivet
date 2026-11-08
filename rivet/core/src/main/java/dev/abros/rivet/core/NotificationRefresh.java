package dev.abros.rivet.core;

/** Game-thread reconciliation clock; an older completion cannot consume a reconnect refresh. */
public final class NotificationRefresh {
    public record Attempt(long generation, long at, boolean required) {}
    private long generation, lastCompleted;
    private boolean completed;
    public Attempt begin(long now) { return new Attempt(generation, now, !completed || now - lastCompleted >= 5000); }
    public void invalidate() { generation++; completed = false; }
    public void complete(Attempt attempt) {
        if (attempt.required && attempt.generation == generation) {
            lastCompleted = attempt.at;
            completed = true;
        }
    }
}
