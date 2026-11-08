package dev.abros.rivet.core;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.*;

/** One server lifetime owns all bounded data queues; callers cannot shut down individual lanes. */
public final class ServerWorkQueues {
    public enum Lane { WRITE, CONTROL, READ }
    private final EnumMap<Lane, LocalizedExecutor> pools = new EnumMap<>(Lane.class);

    public ServerWorkQueues() {
        pools.put(Lane.WRITE, pool(4, 256, "Rivet storage"));
        pools.put(Lane.CONTROL, pool(1, 64, "Rivet server changes"));
        pools.put(Lane.READ, pool(2, 64, "Rivet menu reads"));
    }

    private static LocalizedExecutor pool(int threads, int capacity, String name) {
        return new LocalizedExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    var thread = new Thread(task, name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void execute(Lane lane, Runnable task) { pools.get(lane).execute(task); }
    public Future<?> submit(Lane lane, Runnable task) { return pools.get(lane).submit(task); }
    public int queued(Lane lane) { return pools.get(lane).getQueue().size(); }

    public boolean stop(Duration grace, Duration forced) {
        return WorkerShutdown.stop(grace, forced,
                pools.get(Lane.CONTROL), pools.get(Lane.READ), pools.get(Lane.WRITE));
    }
}
