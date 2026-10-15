package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/** Single stream shared by Java and future network adapters; all state is protected by this monitor. */
public final class StatusFeed implements RivetApi, AutoCloseable {
    private final int capacity;
    private final Clock clock;
    private final UUID stream = UUID.randomUUID();
    private final ArrayDeque<StatusSnapshot> history = new ArrayDeque<>();
    private StatusSnapshot current;
    private boolean closed;

    public StatusFeed(int capacity, Clock clock, ServerStatus initial) {
        if (capacity < 1 || capacity > 4096) throw new IllegalArgumentException("Invalid history capacity");
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
        current = new StatusSnapshot(new ApiCursor(stream, 0), clock.millis(), initial);
    }

    /** Called by the server thread after reading game state. Unchanged values do not consume history. */
    public synchronized void publish(ServerStatus status) {
        requireOpen();
        if (current.status().equals(Objects.requireNonNull(status))) return;
        current = new StatusSnapshot(new ApiCursor(stream, Math.addExact(current.cursor().sequence(), 1)),
            clock.millis(), status);
        history.addLast(current);
        while (history.size() > capacity) history.removeFirst();
    }

    @Override public synchronized StatusSnapshot serverStatus() {
        requireOpen();
        return current;
    }

    @Override public synchronized StatusEvents serverStatusEvents(ApiCursor after, int limit) {
        requireOpen();
        if (after == null || limit < 1 || limit > 256)
            throw new ApiException(ApiException.Code.INVALID_ARGUMENT, "Cursor and limit 1..256 required");
        long last = current.cursor().sequence();
        long oldest = history.isEmpty() ? last : history.getFirst().cursor().sequence() - 1;
        if (!stream.equals(after.stream()) || after.sequence() < oldest || after.sequence() > last)
            throw new ApiException(ApiException.Code.RESYNC_REQUIRED, "Take a fresh server status snapshot");
        var changes = new ArrayList<StatusSnapshot>(Math.min(limit, history.size()));
        for (var item : history) {
            if (item.cursor().sequence() > after.sequence()) changes.add(item);
            if (changes.size() == limit) break;
        }
        ApiCursor next = changes.isEmpty() ? after : changes.getLast().cursor();
        return new StatusEvents(changes, next, next.sequence() < last);
    }

    @Override public synchronized void close() {
        closed = true;
        history.clear();
        current = null;
    }

    private void requireOpen() {
        if (closed) throw new ApiException(ApiException.Code.UNAVAILABLE, "Rivet server API stopped");
    }
}
