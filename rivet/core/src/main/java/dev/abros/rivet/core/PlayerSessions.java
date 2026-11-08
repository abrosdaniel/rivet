package dev.abros.rivet.core;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Connection lifetimes, distinct from persistent player identity and Auth credentials. */
public final class PlayerSessions<C> {
    public static final class Session<C> {
        private final UUID player;
        private final C connection;
        private final long startedAt = System.currentTimeMillis();
        private Session(UUID player, C connection) { this.player = player; this.connection = connection; }
        public UUID player() { return player; }
    }

    private final ConcurrentHashMap<UUID, Session<C>> active = new ConcurrentHashMap<>();

    public Session<C> open(UUID player, C connection) {
        var session = new Session<>(Objects.requireNonNull(player), Objects.requireNonNull(connection));
        active.put(player, session);
        return session;
    }

    public boolean current(Session<C> session) {
        return session != null && active.get(session.player) == session;
    }

    public boolean matches(UUID player, C connection) {
        var session = active.get(player);
        return session != null && session.connection == connection;
    }

    /** A delayed logout must not remove a newer connection with the same UUID. */
    public boolean close(UUID player, C connection) {
        var session = active.get(player);
        return session != null && session.connection == connection && active.remove(player, session);
    }

    public Map<UUID, Session<C>> snapshot() { return Map.copyOf(active); }
    public Session<C> current(UUID player) { return active.get(player); }
    public long startedAt(UUID player, long fallback) {
        var session = active.get(player);
        return session == null ? fallback : session.startedAt;
    }
    public void clear() { active.clear(); }
}
