package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.model.CircuitState;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Per-endpoint circuit breaker with adaptive exponential backoff.
 *
 * <p>Replaces the prior fixed 600-second pause with a graduated backoff schedule:
 * 1 min → 2 min → 5 min → 10 min → 15 min → 30 min
 *
 * <p>The breaker operates independently per {@link EndpointType}, so a failure
 * on the non-critical {@code ORDER_HISTORY} endpoint never blocks pricing or
 * execution endpoints.
 *
 * <p>State transitions:
 * <ul>
 *   <li>CLOSED → OPEN after {@code failureThreshold} consecutive failures</li>
 *   <li>OPEN → HALF_OPEN after the cooldown window expires</li>
 *   <li>HALF_OPEN → CLOSED on probe success</li>
 *   <li>HALF_OPEN → OPEN on probe failure (cooldown resets with next backoff step)</li>
 * </ul>
 */
@Slf4j
public final class ExchangeCircuitBreaker {

    /** Backoff steps (minutes) for successive OPEN periods. */
    private static final long[] BACKOFF_MINUTES = {1, 2, 5, 10, 15, 30};

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_PROBE_TIMEOUT_SECONDS = 10;

    private final String exchangeName;
    private final EndpointType endpoint;
    private final int failureThreshold;
    @Nullable
    private final AgentEventBus eventBus;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger backoffIndex = new AtomicInteger(0);
    private final AtomicLong openedAtNanos = new AtomicLong(0);
    private final AtomicLong cooldownNanos = new AtomicLong(Duration.ofMinutes(BACKOFF_MINUTES[0]).toNanos());
    private final AtomicLong totalTrips = new AtomicLong(0);
    private final AtomicLong totalProbeSuccesses = new AtomicLong(0);

    /** Prevents concurrent probe requests when HALF_OPEN. */
    private final Semaphore probeSemaphore = new Semaphore(1);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "circuit-breaker-%s-%s".formatted(exchangeName, endpoint.name()));
        t.setDaemon(true);
        return t;
    });

    public ExchangeCircuitBreaker(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint,
            @Nullable AgentEventBus eventBus
    ) {
        this(exchangeName, endpoint, DEFAULT_FAILURE_THRESHOLD, eventBus);
    }

    public ExchangeCircuitBreaker(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint,
            int failureThreshold,
            @Nullable AgentEventBus eventBus
    ) {
        this.exchangeName = exchangeName;
        this.endpoint = endpoint;
        this.failureThreshold = failureThreshold;
        this.eventBus = eventBus;
    }

    /**
     * Attempts to call {@code supplier} through the circuit breaker.
     *
     * <ul>
     *   <li>CLOSED: calls supplier normally.</li>
     *   <li>OPEN (cooldown active): throws {@link CircuitOpenException} immediately.</li>
     *   <li>OPEN (cooldown expired): transitions to HALF_OPEN and allows one probe.</li>
     *   <li>HALF_OPEN: allows only one concurrent probe; others throw immediately.</li>
     * </ul>
     *
     * @param <T> return type of the supplier
     * @param supplier the operation to attempt
     * @return a CompletableFuture with the supplier result
     */
    public <T> CompletableFuture<T> call(@NotNull Supplier<CompletableFuture<T>> supplier) {
        CircuitState current = state.get();

        switch (current) {
            case CLOSED -> {
                return executeAndMonitor(supplier);
            }
            case OPEN -> {
                if (shouldAttemptProbe()) {
                    return attemptProbe(supplier);
                }
                log.debug("Circuit OPEN for {}/{} — blocking request", exchangeName, endpoint);
                return CompletableFuture.failedFuture(
                        new CircuitOpenException(exchangeName, endpoint, remainingCooldown()));
            }
            case HALF_OPEN -> {
                if (probeSemaphore.tryAcquire()) {
                    return attemptProbe(supplier);
                }
                return CompletableFuture.failedFuture(
                        new CircuitOpenException(exchangeName, endpoint, Duration.ZERO));
            }
            default -> throw new IllegalStateException("Unknown circuit state: " + current);
        }
    }

    /** Records a successful call and resets the failure counter. */
    public void recordSuccess() {
        consecutiveFailures.set(0);

        if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
            backoffIndex.set(0);
            totalProbeSuccesses.incrementAndGet();
            probeSemaphore.release();
            log.info("Circuit RECOVERED for {}/{}", exchangeName, endpoint);
            publishEvent(AgentEvent.CIRCUIT_RECOVERED);
        }
    }

    /**
     * Records a failed call.
     *
     * <p>If consecutive failures reach the threshold and the circuit is CLOSED,
     * it trips to OPEN immediately.
     *
     * @param cause the exception that caused the failure
     */
    public void recordFailure(@NotNull Throwable cause) {
        int failures = consecutiveFailures.incrementAndGet();

        if (state.get() == CircuitState.HALF_OPEN) {
            // Probe failed — reopen with next backoff step
            tripOpen();
            probeSemaphore.release();
            return;
        }

        if (state.get() == CircuitState.CLOSED && failures >= failureThreshold) {
            tripOpen();
        }
    }

    /** Returns the current circuit state. */
    public CircuitState getState() {
        return state.get();
    }

    /** Returns whether requests are currently permitted. */
    public boolean isRequestAllowed() {
        return state.get().isRequestAllowed() || shouldAttemptProbe();
    }

    /** Returns how long until the next probe attempt is permitted. */
    public Duration remainingCooldown() {
        long openedAt = openedAtNanos.get();
        if (openedAt == 0) return Duration.ZERO;
        long elapsed = System.nanoTime() - openedAt;
        long remaining = cooldownNanos.get() - elapsed;
        return remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO;
    }

    /** Returns total number of times this circuit has tripped. */
    public long getTotalTrips() {
        return totalTrips.get();
    }

    /** Shuts down background resources. */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private <T> CompletableFuture<T> executeAndMonitor(@NotNull Supplier<CompletableFuture<T>> supplier) {
        try {
            return supplier.get().whenComplete((result, ex) -> {
                if (ex != null) {
                    recordFailure(ex);
                } else {
                    recordSuccess();
                }
            });
        } catch (Exception e) {
            recordFailure(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private <T> CompletableFuture<T> attemptProbe(@NotNull Supplier<CompletableFuture<T>> supplier) {
        state.set(CircuitState.HALF_OPEN);
        log.info("Circuit HALF_OPEN probe for {}/{}", exchangeName, endpoint);
        return executeAndMonitor(supplier);
    }

    private boolean shouldAttemptProbe() {
        if (state.get() != CircuitState.OPEN) return false;
        long elapsed = System.nanoTime() - openedAtNanos.get();
        return elapsed >= cooldownNanos.get();
    }

    private void tripOpen() {
        int idx = Math.min(backoffIndex.getAndIncrement(), BACKOFF_MINUTES.length - 1);
        long cooldownMs = Duration.ofMinutes(BACKOFF_MINUTES[idx]).toNanos();
        cooldownNanos.set(cooldownMs);
        openedAtNanos.set(System.nanoTime());
        state.set(CircuitState.OPEN);
        totalTrips.incrementAndGet();

        log.warn("Circuit OPEN for {}/{} — cooldown={}min consecutive-failures={}",
                exchangeName, endpoint, BACKOFF_MINUTES[idx], consecutiveFailures.get());
        publishEvent(AgentEvent.CIRCUIT_OPENED);
    }

    private void publishEvent(@NotNull String eventType) {
        if (eventBus == null) return;
        try {
            Map<String, Object> meta = Map.of(
                    "exchangeName", exchangeName,
                    "endpoint", endpoint.name(),
                    "state", state.get().name(),
                    "backoffIndex", backoffIndex.get(),
                    "cooldownRemaining", remainingCooldown().toSeconds()
            );
            eventBus.publishAsync(AgentEvent.of(eventType, "ExchangeCircuitBreaker", endpoint, meta));
        } catch (Exception ignored) {
            // Event bus failures must not affect circuit breaker operation
        }
    }

    // ── Inner exception ────────────────────────────────────────────────────────────────────────

    /**
     * Thrown when a request is blocked by an open circuit.
     * Callers should catch this to activate stale-cache fallback.
     */
    public static final class CircuitOpenException extends RuntimeException {

        private final EndpointType endpoint;
        private final Duration remainingCooldown;

        public CircuitOpenException(
                @NotNull String exchange,
                @NotNull EndpointType endpoint,
                @NotNull Duration remainingCooldown
        ) {
            super("Circuit OPEN for %s/%s — remaining cooldown %ds"
                    .formatted(exchange, endpoint.name(), remainingCooldown.toSeconds()));
            this.endpoint = endpoint;
            this.remainingCooldown = remainingCooldown;
        }

        public EndpointType getEndpoint() { return endpoint; }
        public Duration getRemainingCooldown() { return remainingCooldown; }
    }
}
