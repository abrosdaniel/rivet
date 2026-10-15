package dev.abros.rivet.api;

import java.util.Objects;
import java.util.Optional;

/** One coalescing slot, no consumer callback on Rivet threads. Close when the consumer stops. */
public interface DomainSubscription<T> extends AutoCloseable {
 sealed interface Event<T> permits Snapshot,Closed {}
 /** Replace the previous value. Sequence gaps mean intermediate states were coalesced. */
 record Snapshot<T>(ApiCursor cursor,T value) implements Event<T> {
  public Snapshot{Objects.requireNonNull(cursor);Objects.requireNonNull(value);}
 }
 /** Empty error means explicit cancellation; otherwise discard cached data and handle the error. */
 record Closed<T>(Optional<ApiException.Code> error) implements Event<T> {
  public Closed{Objects.requireNonNull(error);}
 }
 /** Nonblocking, no IO. Returns the newest pending event at most once. Safe on the game thread. */
 Optional<Event<T>> poll();
 boolean isClosed();
 /** Idempotent. Removes pending private data and cancels an outstanding read. */
 @Override void close();
}
