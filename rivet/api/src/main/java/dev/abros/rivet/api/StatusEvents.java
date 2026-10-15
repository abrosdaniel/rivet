package dev.abros.rivet.api;

import java.util.List;
import java.util.Objects;

/** Bounded ordered changes. Continue after nextCursor; hasMore describes this read only. */
public record StatusEvents(List<StatusSnapshot> changes, ApiCursor nextCursor, boolean hasMore) {
    public StatusEvents {
        changes = List.copyOf(changes);
        Objects.requireNonNull(nextCursor);
    }
}
