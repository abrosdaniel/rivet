package dev.abros.rivet.api;

import java.util.Objects;

/** Snapshot and replay cursor captured atomically. Read subsequent events after this cursor. */
public record StatusSnapshot(ApiCursor cursor, long capturedAtEpochMillis, ServerStatus status) {
    public StatusSnapshot {
        Objects.requireNonNull(cursor); Objects.requireNonNull(status);
        if (capturedAtEpochMillis < 0) throw new IllegalArgumentException("Negative timestamp");
    }
}
