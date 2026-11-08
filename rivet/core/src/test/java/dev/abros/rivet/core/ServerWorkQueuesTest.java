package dev.abros.rivet.core;

import dev.abros.rivet.core.modules.ModuleRuntime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static dev.abros.rivet.core.ServerWorkQueues.Lane.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerWorkQueuesTest {
    private static final Duration WAIT = Duration.ofSeconds(3);

    @Test void fullWriteQueueDoesNotBlockReadsOrControlOrRunOnCaller() throws Exception {
        var queues = new ServerWorkQueues();
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var rejectedRan = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            for (int i = 0; i < 4; i++) queues.execute(WRITE, () -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < 256; i++) queues.submit(WRITE, () -> {});
            assertEquals(256, queues.queued(WRITE));
            assertThrows(RejectedExecutionException.class, () -> queues.execute(WRITE, () -> rejectedRan.set(true)));
            assertFalse(rejectedRan.get());
            queues.submit(READ, () -> {}).get(3, TimeUnit.SECONDS);
            queues.submit(CONTROL, () -> {}).get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); assertTrue(queues.stop(WAIT, WAIT)); }
    }

    @Test void forcedStopCancelsQueuedFuturesAndFreshLifetimeAcceptsWork() throws Exception {
        var queues = new ServerWorkQueues();
        var entered = new CountDownLatch(1);
        try {
            queues.execute(CONTROL, () -> {
                entered.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var pending = queues.submit(CONTROL, () -> fail("Queued task must not run"));
            assertTrue(queues.stop(Duration.ZERO, WAIT));
            assertTrue(pending.isCancelled());
            assertThrows(CancellationException.class, () -> pending.get(1, TimeUnit.SECONDS));
            for (var lane : ServerWorkQueues.Lane.values())
                assertThrows(RejectedExecutionException.class, () -> queues.submit(lane, () -> {}));
            var fresh = new ServerWorkQueues();
            try { fresh.submit(CONTROL, () -> {}).get(3, TimeUnit.SECONDS); }
            finally { assertTrue(fresh.stop(WAIT, WAIT)); }
        } finally { queues.stop(Duration.ZERO, WAIT); }
    }

    @Test void failedModuleStartStopsPartiallyStartedQueues() {
        var queues = new AtomicReference<ServerWorkQueues>();
        var runtime = new ModuleRuntime<Object>(List.of(new ModuleRuntime.Module<Object>("data", List.of(), c -> true,
                c -> { queues.set(new ServerWorkQueues()); queues.get().execute(READ, () -> {}); throw new IllegalStateException("Startup failed"); },
                c -> assertTrue(queues.get().stop(WAIT, WAIT)))));
        assertThrows(IllegalStateException.class, () -> runtime.start(new Object()));
        assertTrue(runtime.active().isEmpty());
        assertThrows(RejectedExecutionException.class, () -> queues.get().execute(READ, () -> {}));
    }
}
