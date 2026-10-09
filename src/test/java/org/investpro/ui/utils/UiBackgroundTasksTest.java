package org.investpro.ui.utils;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UiBackgroundTasksTest {
    private ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Test void saturationFailsInsteadOfBlockingOrExecutingOnCaller() throws Exception {
        var pool = executor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var active = UiBackgroundTasks.submit(pool, () -> { started.countDown(); release.await(); return 1; });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = UiBackgroundTasks.submit(pool, () -> 2);
            AtomicReference<Thread> ran = new AtomicReference<>();
            var rejected = UiBackgroundTasks.submit(pool, () -> { ran.set(Thread.currentThread()); return 3; });
            assertThrows(CompletionException.class, rejected::join);
            assertNull(ran.get());
            assertEquals(1, pool.getQueue().size());
            release.countDown();
            assertEquals(1, active.get(2, TimeUnit.SECONDS));
            assertEquals(2, queued.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void canceledQueuedLoadsReleaseCapacity() throws Exception {
        var pool = executor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            UiBackgroundTasks.submit(pool, () -> { started.countDown(); release.await(); return 1; });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = UiBackgroundTasks.submit(pool, () -> 2);
            queued.cancel(true);
            assertTrue(pool.getQueue().isEmpty());
            var replacement = UiBackgroundTasks.submit(pool, () -> 3);
            release.countDown();
            assertEquals(3, replacement.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void timeoutInterruptsAnInFlightRead() throws Exception {
        var pool = executor();
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        try {
            var result = UiBackgroundTasks.submit(pool, () -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { interrupted.countDown(); throw error; }
                return 1;
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            result.orTimeout(50, TimeUnit.MILLISECONDS);
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
