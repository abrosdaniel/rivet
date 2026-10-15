package dev.abros.rivet.api;

import java.util.Objects;
import java.util.UUID;

/** Opaque stream incarnation and sequence. A server restart creates a new stream. */
public record ApiCursor(UUID stream, long sequence) {
    public ApiCursor {
        Objects.requireNonNull(stream);
        if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
    }
}
