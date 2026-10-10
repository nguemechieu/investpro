package org.investpro.core.concurrent;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AppExecutorsTest {
    @Test void overloadFailsWithoutRunningOnCallerAndCancellationReleasesCapacity() throws Exception {
        var pool = AppExecutors.pool("test", 1, 1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var active = AppExecutors.submit(pool, () -> { started.countDown(); release.await(); return 1; });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = AppExecutors.submit(pool, () -> 2);
            var executed = new AtomicReference<Thread>();
            var rejected = AppExecutors.submit(pool, () -> { executed.set(Thread.currentThread()); return 3; });
            assertThrows(CompletionException.class, rejected::join);
            assertNull(executed.get());
            assertTrue(queued.cancel(true));
            assertTrue(pool.getQueue().isEmpty());
            var replacement = AppExecutors.submit(pool, () -> 4);
            release.countDown();
            assertEquals(1, active.get(2, TimeUnit.SECONDS));
            assertEquals(4, replacement.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void orderedLanePreservesFifoAndRejectsOverflow() {
        var drains = new ArrayList<Runnable>();
        var lane = new OrderedExecutor(drains::add, 2);
        var observed = new ArrayList<Integer>();
        lane.execute(() -> observed.add(1));
        lane.execute(() -> observed.add(2));
        assertThrows(RejectedExecutionException.class, () -> lane.execute(() -> observed.add(3)));
        assertEquals(1, drains.size());
        drains.removeFirst().run();
        assertEquals(List.of(1, 2), observed);
        lane.execute(() -> observed.add(4));
        drains.removeFirst().run();
        assertEquals(List.of(1, 2, 4), observed);
    }

    @Test void orderedLaneRecoversFromWorkerRejection() {
        var submissions = new java.util.concurrent.atomic.AtomicInteger();
        var observed = new ArrayList<Integer>();
        var lane = new OrderedExecutor(task -> {
            if (submissions.getAndIncrement() == 0) throw new RejectedExecutionException();
            task.run();
        }, 2);
        assertThrows(RejectedExecutionException.class, () -> lane.execute(() -> observed.add(1)));
        lane.execute(() -> observed.add(2));
        assertEquals(List.of(2), observed);
    }
}
