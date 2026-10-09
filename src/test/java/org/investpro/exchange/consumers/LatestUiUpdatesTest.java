package org.investpro.exchange.consumers;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LatestUiUpdatesTest {
    @Test void largeQuoteBurstQueuesOneCallbackAndDisplaysTheLastSnapshot() {
        Queue<Runnable> queue = new ArrayDeque<>();
        LatestUiUpdates updates = new LatestUiUpdates(queue::add);
        AtomicInteger displayed = new AtomicInteger(-1);
        for (int i = 0; i < 50_000; i++) {
            int value = i;
            updates.submit("ticker:coinbase:BTC-USD", () -> displayed.set(value));
        }
        assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals(49_999, displayed.get());
        assertTrue(queue.isEmpty());
    }

    @Test void largeSymbolUniverseYieldsBetweenBatchesAndRetainsEverySymbol() {
        Queue<Runnable> queue = new ArrayDeque<>();
        LatestUiUpdates updates = new LatestUiUpdates(queue::add);
        Set<Integer> displayed = new HashSet<>();
        for (int i = 0; i < 1000; i++) { int symbol = i; updates.submit("symbol:" + i, () -> displayed.add(symbol)); }
        queue.remove().run();
        assertEquals(64, displayed.size());
        assertEquals(1, queue.size());
        while (!queue.isEmpty()) queue.remove().run();
        assertEquals(1000, displayed.size());
    }

    @Test void concurrentVenuesRetainLatestValuesAndOnlyOnePendingDispatch() throws Exception {
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        LatestUiUpdates updates = new LatestUiUpdates(queue::add);
        Map<Integer, Integer> displayed = new HashMap<>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int venue = i;
                jobs.add(workers.submit(() -> {
                    for (int n = 0; n < 5000; n++) { int value = n; updates.submit("venue:" + venue, () -> displayed.put(venue, value)); }
                }));
            }
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, queue.size());
        while (!queue.isEmpty()) queue.remove().run();
        assertEquals(8, displayed.size());
        assertTrue(displayed.values().stream().allMatch(value -> value == 4999));
    }

    @Test void updatesArrivingDuringRenderingAreNotLost() {
        Queue<Runnable> queue = new ArrayDeque<>();
        LatestUiUpdates updates = new LatestUiUpdates(queue::add);
        AtomicInteger displayed = new AtomicInteger();
        updates.submit("symbol", () -> { displayed.set(1); updates.submit("symbol", () -> displayed.set(2)); });
        while (!queue.isEmpty()) queue.remove().run();
        assertEquals(2, displayed.get());
    }
}
