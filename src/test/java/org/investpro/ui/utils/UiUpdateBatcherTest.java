package org.investpro.ui.utils;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UiUpdateBatcherTest {
    @Test void discreteEventsRemainInOrderWithoutOneUiCallbackPerEvent() {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var observed = new java.util.ArrayList<Integer>();
        try (var batcher = new UiUpdateBatcher(callbacks::add)) {
            for (int i = 0; i < 100; i++) { int event = i; batcher.event(() -> observed.add(event)); }
            batcher.flush(); callbacks.remove().run();
            assertEquals(64, observed.size());
            batcher.flush(); callbacks.remove().run();
            assertEquals(java.util.stream.IntStream.range(0, 100).boxed().toList(), observed);
        }
    }
    @Test void burstUsesOneUiCallbackAndRendersLatestValue() {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var value = new AtomicInteger();
        try (var batcher = new UiUpdateBatcher(callbacks::add)) {
            for (int i = 1; i <= 10_000; i++) {
                int latest = i;
                batcher.latest("quote", () -> value.set(latest));
            }
            batcher.flush();
            batcher.flush();
            assertEquals(1, callbacks.size());
            assertEquals(0, value.get());
            callbacks.remove().run();
            assertEquals(10_000, value.get());
        }
    }

    @Test void closeDiscardsAlreadyQueuedRendering() {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var value = new AtomicInteger();
        var batcher = new UiUpdateBatcher(callbacks::add);
        batcher.latest("quote", value::incrementAndGet);
        batcher.flush();
        batcher.close();
        callbacks.remove().run();
        assertEquals(0, value.get());
    }

    @Test void updateArrivingDuringRenderingSurvivesForNextPulse() {
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var value = new AtomicInteger();
        try (var batcher = new UiUpdateBatcher(callbacks::add)) {
            batcher.latest("quote", () -> { value.set(1); batcher.latest("quote", () -> value.set(2)); });
            batcher.flush(); callbacks.remove().run();
            batcher.flush(); callbacks.remove().run();
            assertEquals(2, value.get());
        }
    }
}
