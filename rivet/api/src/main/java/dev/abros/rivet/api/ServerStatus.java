package dev.abros.rivet.api;

import java.util.Objects;
import java.util.Set;

/** Explicit status projection. Contains no player identities, locations or private restart reason. */
public record ServerStatus(int onlinePlayers, int maximumPlayers, boolean maintenance,
                           long shutdownAtEpochMillis, Set<String> capabilities) {
    public ServerStatus {
        if (onlinePlayers < 0 || maximumPlayers < 0 || shutdownAtEpochMillis < 0)
            throw new IllegalArgumentException("Negative status value");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities));
    }
}
