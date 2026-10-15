package dev.abros.rivet.api;

import java.util.Objects;

/** Stable, machine-readable failure. Messages are diagnostic, not a wire contract. */
public final class ApiException extends RuntimeException {
    public enum Code { UNAVAILABLE, MODULE_DISABLED, INVALID_ARGUMENT, RESYNC_REQUIRED,
        UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, BUSY }
    private final Code code;
    public ApiException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code);
    }
    public Code code() { return code; }
}
