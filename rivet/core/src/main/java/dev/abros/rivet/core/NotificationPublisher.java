package dev.abros.rivet.core;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.api.NotificationsApi;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Bounded Java publisher. Registrations are transient; history belongs to the recipient. */
public final class NotificationPublisher implements AutoCloseable {
    private final NotificationStore store;
    private final PgDatabase database;
    private final Map<String, Registration> types = new HashMap<>();
    private final ThreadPoolExecutor worker;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public NotificationPublisher(PgDatabase database) {
        this.database=database;store = new NotificationStore(database);
        worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), task -> {
            var thread = new Thread(task, "Rivet notifications API"); thread.setDaemon(true); return thread;
        });
        worker.allowCoreThreadTimeOut(true);
    }

    public synchronized NotificationsApi owner(String mod) {
        active();
        if (mod == null || !mod.matches("[a-z][a-z0-9_]{1,63}")) throw fail(INVALID_ARGUMENT, "Invalid owner");
        return new Owner(mod);
    }
    private void active() { if (closed) throw fail(UNAVAILABLE, "Notifications stopped"); }

    private final class Owner implements NotificationsApi {
        final String mod;
        volatile boolean released;
        Owner(String mod) { this.mod = mod; }
        void valid() { active(); if (released) throw fail(UNAVAILABLE, "Owner handle closed"); }
        public Type register(String type) {
            synchronized (NotificationPublisher.this) {
                valid(); localId(type);
                String id = mod + ":" + type;
                if (types.containsKey(id)) throw fail(CONFLICT, "Notification type already registered");
                if (types.size() >= 256 || types.values().stream().filter(r -> r.owner.mod.equals(mod)).count() >= 16)
                    throw fail(BUSY, "Notification type capacity exceeded");
                var result = new Registration(this, id); types.put(id, result); return result;
            }
        }
        public void close() {
            synchronized (NotificationPublisher.this) {
                released = true;
                types.values().removeIf(type -> type.owner == this);
            }
        }
    }

    private final class Registration implements NotificationsApi.Type {
        final Owner owner;
        final String id;
        boolean released;
        Registration(Owner owner, String id) { this.owner = owner; this.id = id; }
        public String id() { return id; }
        void valid() {
            synchronized (NotificationPublisher.this) {
                owner.valid();
                if (released || types.get(id) != this) throw fail(UNAVAILABLE, "Notification type closed");
            }
        }
        public CompletableFuture<NotificationsApi.Receipt> put(UUID recipient, String key, NotificationsApi.Message message, long revision) {return putGuarded(recipient,key,message,revision,()->null);}
        CompletableFuture<NotificationsApi.Receipt> putGuarded(UUID recipient,String key,NotificationsApi.Message message,long revision,Callable<Void> guard) {
            return submit(() -> {
                valid(); arguments(recipient, key, revision);
                if (message == null) throw fail(INVALID_ARGUMENT, "Message required");
                if (message.expiresAt() < 0 || message.expiresAt() != 0 && message.expiresAt() <= System.currentTimeMillis())
                    throw fail(INVALID_ARGUMENT, "Notification expiry must be in the future");
                text(message.title(), 300, false); text(message.body(), 1000, true);
                if (message.translations().size() > 8) throw fail(INVALID_ARGUMENT, "Too many translations");
                for (var entry : message.translations().entrySet()) {
                    if (!entry.getKey().matches("[a-z]{2,3}_[a-z]{2}")) throw fail(INVALID_ARGUMENT, "Invalid locale");
                    text(entry.getValue().title(), 300, false); text(entry.getValue().body(), 1000, true);
                }
                return database.communityTransaction(()->{guard.call();return store.publish(recipient.toString(), id, key, message, revision, false);});
            });
        }
        public CompletableFuture<Void> revoke(UUID recipient, String key, long revision) {return revokeGuarded(recipient,key,revision,()->null);}
        CompletableFuture<Void> revokeGuarded(UUID recipient,String key,long revision,Callable<Void> guard) {
            return submit(() -> {
                valid(); arguments(recipient, key, revision);
                if (revision == 0) throw fail(INVALID_ARGUMENT, "Revision required");
                return database.communityTransaction(()->{guard.call();store.publish(recipient.toString(), id, key, null, revision, true);return null;});
            });
        }
        public void close() {
            synchronized (NotificationPublisher.this) { released = true; types.remove(id, this); }
        }
    }

    /** Internal transport entry: authorization runs inside the publication transaction on the bounded worker. */
    public CompletableFuture<NotificationsApi.Receipt> putAuthorized(NotificationsApi.Type type,UUID recipient,String key,NotificationsApi.Message message,long revision,Callable<Void> authorization) {
        return registration(type).putGuarded(recipient,key,message,revision,Objects.requireNonNull(authorization));
    }
    public CompletableFuture<Void> revokeAuthorized(NotificationsApi.Type type,UUID recipient,String key,long revision,Callable<Void> authorization) {
        return registration(type).revokeGuarded(recipient,key,revision,Objects.requireNonNull(authorization));
    }
    private synchronized Registration registration(NotificationsApi.Type type) {
        active();var registered=types.get(type.id());
        if(registered!=type)throw fail(UNAVAILABLE,"Notification type unavailable");
        return registered;
    }

    /** Runtime maintenance, on the same bounded worker as publications; no game-thread SQL. */
    public CompletableFuture<Integer> maintain() { return submit(store::expire); }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        var result = new CompletableFuture<T>();
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(fail(UNAVAILABLE, "Notifications stopped"));
            pending.add(result);
            result.whenComplete((value, error) -> pending.remove(result));
            try {
                worker.execute(() -> {
                    if (result.isDone()) return;
                    try { active(); result.complete(task.call()); }
                    catch (ApiException error) { result.completeExceptionally(error); }
                    catch (Exception error) { result.completeExceptionally(fail(UNAVAILABLE, "Notification storage unavailable")); }
                });
            } catch (RejectedExecutionException full) { result.completeExceptionally(fail(BUSY, "Notification queue full")); }
        }
        return result;
    }

    /** Accepted in-flight writes may finish; queued calls are rejected and saved history is retained. */
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true; types.clear();
        }
        WorkerShutdown.stop(Duration.ofSeconds(5), Duration.ofSeconds(5), worker);
        for (var result : pending) result.completeExceptionally(fail(UNAVAILABLE, "Notifications stopped"));
    }

    private static void arguments(UUID recipient, String key, long revision) {
        if (recipient == null || revision < 0 || revision == Long.MAX_VALUE) throw fail(INVALID_ARGUMENT, "Invalid recipient or revision");
        localId(key);
    }
    private static void localId(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}")) throw fail(INVALID_ARGUMENT, "Invalid local ID");
    }
    private static void text(String value, int limit, boolean empty) {
        if (value == null || value.length() > limit || (!empty && value.isBlank())
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n'))
            throw fail(INVALID_ARGUMENT, "Invalid notification text");
    }
    private static ApiException fail(ApiException.Code code, String message) { return new ApiException(code, message); }
}
