package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Priority-based REST request queue that preserves execution quality
 * during periods of elevated load or circuit pressure.
 *
 * <p>Priority levels (lower value = higher priority):
 * <ol>
 *   <li>EXECUTION (priority 1) — order submission</li>
 *   <li>PRICING (priority 1) — live prices</li>
 *   <li>ACCOUNT / BALANCES (priority 2–3) — account data</li>
 *   <li>POSITIONS (priority 2) — position data</li>
 *   <li>ORDER_HISTORY (priority 4) — non-critical history</li>
 *   <li>ANALYTICS / TRANSACTIONS (priority 5–6) — analytics</li>
 * </ol>
 *
 * <p>During pressure (queue depth &gt; high-water mark), low-priority requests
 * (priority &gt; {@code LOW_PRIORITY_THRESHOLD}) are suspended automatically.
 * Critical requests always proceed.
 */
@Slf4j
public final class RequestPrioritizationQueue {

    private static final int HIGH_WATER_MARK = 100;
    private static final int LOW_PRIORITY_THRESHOLD = 3; // suspends priority > 3 under pressure

    private final PriorityBlockingQueue<PrioritizedRequest<?>> queue =
            new PriorityBlockingQueue<>(256, Comparator.comparingInt(r -> r.priority));

    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "request-prio-dispatcher");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean pressureMode = new AtomicBoolean(false);
    private final AtomicLong accepted = new AtomicLong(0);
    private final AtomicLong suspended = new AtomicLong(0);
    private final AtomicLong completed = new AtomicLong(0);

    private volatile boolean running = true;

    public RequestPrioritizationQueue() {
        dispatcher.submit(this::drainLoop);
    }

    /**
     * Submits a request to the prioritized queue.
     *
     * <p>During pressure mode, requests with priority &gt; {@code LOW_PRIORITY_THRESHOLD}
     * are dropped and the returned future fails immediately.
     *
     * @param endpoint the target endpoint (determines priority)
     * @param supplier the actual HTTP request supplier
     * @param <T>      response type
     * @return a future that resolves when the request is dequeued and executed
     */
    public <T> @NotNull CompletableFuture<T> submit(
            @NotNull EndpointType endpoint,
            @NotNull Supplier<CompletableFuture<T>> supplier
    ) {
        int priority = endpoint.priority;

        if (pressureMode.get() && priority > LOW_PRIORITY_THRESHOLD) {
            suspended.incrementAndGet();
            log.debug("Request suspended (pressure mode): {}", endpoint);
            return CompletableFuture.failedFuture(
                    new RequestSuspendedException(endpoint, "Queue pressure mode active"));
        }

        @SuppressWarnings("unchecked")
        PrioritizedRequest<T> request = new PrioritizedRequest<>(priority, supplier, new CompletableFuture<>());
        queue.offer(request);
        accepted.incrementAndGet();
        updatePressureMode();

        @SuppressWarnings("unchecked")
        CompletableFuture<T> future = (CompletableFuture<T>) request.resultFuture;
        return future;
    }

    /** Returns {@code true} if the queue is currently in pressure (high-load) mode. */
    public boolean isPressureMode() {
        return pressureMode.get();
    }

    /** Returns the current number of requests waiting in the queue. */
    public int queueDepth() {
        return queue.size();
    }

    /** Returns total accepted requests since startup. */
    public long acceptedCount() { return accepted.get(); }

    /** Returns total suspended (dropped) requests since startup. */
    public long suspendedCount() { return suspended.get(); }

    /** Returns total completed requests since startup. */
    public long completedCount() { return completed.get(); }

    /** Shuts down the dispatcher thread. */
    public void shutdown() {
        running = false;
        dispatcher.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void drainLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                PrioritizedRequest<?> request = queue.poll(100, TimeUnit.MILLISECONDS);
                if (request == null) continue;

                request.supplier.get().whenComplete((result, ex) -> {
                    completed.incrementAndGet();
                    if (ex != null) {
                        ((CompletableFuture<Object>) request.resultFuture).completeExceptionally(ex);
                    } else {
                        ((CompletableFuture<Object>) request.resultFuture).complete(result);
                    }
                    updatePressureMode();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void updatePressureMode() {
        boolean newMode = queue.size() > HIGH_WATER_MARK;
        boolean prev = pressureMode.getAndSet(newMode);
        if (prev != newMode) {
            log.warn("Request queue pressure mode changed: {} (depth={})", newMode ? "ON" : "OFF", queue.size());
        }
    }

    private record PrioritizedRequest<T>(
            int priority,
            @NotNull Supplier<CompletableFuture<T>> supplier,
            @NotNull CompletableFuture<T> resultFuture
    ) {}

    /** Thrown when a request is suspended due to queue pressure. */
    public static final class RequestSuspendedException extends RuntimeException {
        private final EndpointType endpoint;
        public RequestSuspendedException(@NotNull EndpointType endpoint, @Nullable String reason) {
            super("Request suspended for endpoint %s: %s".formatted(endpoint.name(), reason));
            this.endpoint = endpoint;
        }
        public EndpointType getEndpoint() { return endpoint; }
    }
}
