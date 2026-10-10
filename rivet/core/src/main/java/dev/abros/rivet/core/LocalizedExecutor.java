package dev.abros.rivet.core;

import java.util.concurrent.*;

/** Bounded worker pool which preserves the originating request's language. */
public final class LocalizedExecutor extends ThreadPoolExecutor {
    public LocalizedExecutor(int core, int maximum, long keepAlive, TimeUnit unit,
                             BlockingQueue<Runnable> queue, ThreadFactory threads,
                             RejectedExecutionHandler rejection) {
        super(core, maximum, keepAlive, unit, queue, threads, rejection);
    }

    @Override public void execute(Runnable command) {
        // Submitted futures must remain visible to purge(), cancellation and shutdownNow().
        super.execute(command instanceof LocalizedFuture<?> ? command : Messages.capture(command));
    }

    @Override protected <T> RunnableFuture<T> newTaskFor(Callable<T> action) {
        return new LocalizedFuture<>(action);
    }

    @Override protected <T> RunnableFuture<T> newTaskFor(Runnable action, T value) {
        return new LocalizedFuture<>(Executors.callable(action,value));
    }

    private static final class LocalizedFuture<T> extends FutureTask<T> {
        private final Runnable localized;
        LocalizedFuture(Callable<T> action) { super(action); localized = Messages.capture(super::run); }
        @Override public void run() { localized.run(); }
    }
}
