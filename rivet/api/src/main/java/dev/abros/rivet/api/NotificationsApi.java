package dev.abros.rivet.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent notifications owned by the installed calling mod. No commands or arbitrary routes. */
public interface NotificationsApi extends AutoCloseable {
    sealed interface Action permits OpenGroup, OpenReport, OpenOwnPublication {}
    /** Opens the group card after checking the recipient's current access. Does not join it. */
    record OpenGroup(UUID group) implements Action { public OpenGroup { java.util.Objects.requireNonNull(group); } }
    /** Opens an existing report belonging to the recipient; grants no staff access. */
    record OpenReport(UUID report) implements Action { public OpenReport { java.util.Objects.requireNonNull(report); } }
    /** Opens the recipient's own map publication; does not approve or publish it. */
    record OpenOwnPublication(UUID publication) implements Action { public OpenOwnPublication { java.util.Objects.requireNonNull(publication); } }
    record Translation(String title, String body) {}
    /** Fallback text plus explicit locale variants; no translation keys or executable markup. */
    record Message(String title, String body, java.util.Map<String, Translation> translations, long expiresAt, Action action) {
        public Message { translations = java.util.Map.copyOf(translations); }
        public Message(String title, String body, java.util.Map<String, Translation> translations, long expiresAt) { this(title, body, translations, expiresAt, null); }
        /** expiresAt is Unix milliseconds; zero keeps the ordinary history retention policy. */
        public Message(String title, String body, java.util.Map<String, Translation> translations) { this(title, body, translations, 0); }
        public Message(String title, String body) { this(title, body, java.util.Map.of(), 0); }
    }
    record Receipt(long id, long revision) {}

    /** Local type ID, namespaced by Rivet. Close registrations when the integration disables. */
    Type register(String type);
    /** Invalidates this handle and its registrations; saved player history is retained. */
    @Override void close();

    interface Type extends AutoCloseable {
        String id();
        /**
         * expectedRevision=0 creates. Repeating an identical create returns the existing receipt;
         * a different payload conflicts. Updates require the current revision and preserve read state.
         * Keys are scoped to this type and recipient. Deduplication lasts while the notice is retained and unexpired
         * (at most 200 notices per player). Works for offline UUIDs as well.
         */
        CompletableFuture<Receipt> put(UUID recipient, String key, Message message, long expectedRevision);
        /** Removes an owned notification only; missing keys are a successful no-op. */
        CompletableFuture<Void> revoke(UUID recipient, String key, long expectedRevision);
        @Override void close();
    }
}
