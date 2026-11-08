package dev.abros.rivet.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Game-thread owned menu negotiation and leases, scoped to a connection lifetime. */
public final class MenuSubscriptions<S> {
    public record View(String section, String id) {}
    private record Lease<S>(S session, View view, long expires, long resynced) {}
    private static final long LEASE_MILLIS = 30_000;
    private final Map<UUID, S> ready = new HashMap<>();
    private final Map<UUID, Lease<S>> leases = new HashMap<>();

    public void ready(UUID player, S session) { ready.put(player, Objects.requireNonNull(session)); }
    public boolean isReady(UUID player, S session) { return session != null && ready.get(player) == session; }

    /** Returns whether the caller must send a fresh subscription snapshot. */
    public boolean subscribe(UUID player, S session, String section, String id, long now) {
        Objects.requireNonNull(session);
        if (section.isEmpty()) {
            var lease = leases.get(player);
            if (lease != null && lease.session == session) leases.remove(player);
            return false;
        }
        var view = new View(section, id);
        var previous = leases.get(player);
        boolean resync = previous == null || previous.session != session || previous.expires <= now
                || !previous.view.equals(view) || now - previous.resynced >= LEASE_MILLIS;
        leases.put(player, new Lease<>(session, view, now + LEASE_MILLIS, resync ? now : previous.resynced));
        return resync;
    }

    public View current(UUID player, S session, long now) {
        var lease = leases.get(player);
        if (lease == null || lease.session != session) return null;
        if (lease.expires <= now) { leases.remove(player); return null; }
        return lease.view;
    }

    public void remove(UUID player) { ready.remove(player); leases.remove(player); }
    public void clear() { ready.clear(); leases.clear(); }
}
