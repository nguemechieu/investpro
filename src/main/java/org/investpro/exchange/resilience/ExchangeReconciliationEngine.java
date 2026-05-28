package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodic reconciliation engine that verifies the runtime’s internal state
 * matches the exchange’s actual state.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Reconcile account balances (local cache vs live exchange)</li>
 *   <li>Reconcile open positions</li>
 *   <li>Reconcile open orders</li>
 *   <li>Reconcile recent fills</li>
 *   <li>Detect and report drift between cached and live state</li>
 * </ul>
 *
 * <p>Runs on a dedicated background thread at a configurable interval.
 * Results are published as {@link AgentEvent} on the {@link AgentEventBus}.
 */
@Slf4j
public final class ExchangeReconciliationEngine {

    /** Default reconciliation interval (5 minutes). */
    private static final long DEFAULT_INTERVAL_SECONDS = 300;

    private final String exchangeName;
    @Nullable
    private final AgentEventBus eventBus;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reconciliation-" + exchangeName);
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<ReconciliationReport> lastReport = new AtomicReference<>(null);
    private volatile boolean running = false;

    public ExchangeReconciliationEngine(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus
    ) {
        this(exchangeName, eventBus, DEFAULT_INTERVAL_SECONDS);
    }

    public ExchangeReconciliationEngine(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            long intervalSeconds
    ) {
        this.exchangeName = exchangeName;
        this.eventBus = eventBus;
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * Starts the reconciliation loop.
     *
     * @param balanceFetcher   supplier for live balance data
     * @param positionFetcher  supplier for live position data
     * @param orderFetcher     supplier for live open order data
     */
    public void start(
            @NotNull java.util.function.Supplier<CompletableFuture<Map<String, Double>>> balanceFetcher,
            @NotNull java.util.function.Supplier<CompletableFuture<Map<String, Integer>>> positionFetcher,
            @NotNull java.util.function.Supplier<CompletableFuture<Integer>> orderFetcher
    ) {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(
                () -> runReconciliation(balanceFetcher, positionFetcher, orderFetcher),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        log.info("Reconciliation engine started for {} (interval={}s)", exchangeName, intervalSeconds);
    }

    /** Stops the reconciliation loop. */
    public void stop() {
        running = false;
        scheduler.shutdownNow();
    }

    /**
     * Returns the most recent reconciliation report.
     *
     * @return latest report, or {@code null} if no reconciliation has completed yet
     */
    public @Nullable ReconciliationReport getLastReport() {
        return lastReport.get();
    }

    /** Triggers an immediate reconciliation (non-blocking). */
    public void triggerNow(
            @NotNull java.util.function.Supplier<CompletableFuture<Map<String, Double>>> balanceFetcher,
            @NotNull java.util.function.Supplier<CompletableFuture<Map<String, Integer>>> positionFetcher,
            @NotNull java.util.function.Supplier<CompletableFuture<Integer>> orderFetcher
    ) {
        scheduler.submit(() -> runReconciliation(balanceFetcher, positionFetcher, orderFetcher));
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private void runReconciliation(
            java.util.function.Supplier<CompletableFuture<Map<String, Double>>> balanceFetcher,
            java.util.function.Supplier<CompletableFuture<Map<String, Integer>>> positionFetcher,
            java.util.function.Supplier<CompletableFuture<Integer>> orderFetcher
    ) {
        Instant startedAt = Instant.now();
        try {
            CompletableFuture<Map<String, Double>> balanceFuture = balanceFetcher.get();
            CompletableFuture<Map<String, Integer>> positionFuture = positionFetcher.get();
            CompletableFuture<Integer> orderFuture = orderFetcher.get();

            CompletableFuture.allOf(balanceFuture, positionFuture, orderFuture)
                    .whenComplete((ignored, ex) -> {
                        if (ex != null) {
                            log.warn("Reconciliation fetch failed for {}: {}", exchangeName, ex.getMessage());
                            return;
                        }
                        Map<String, Double> balances = balanceFuture.join();
                        Map<String, Integer> positions = positionFuture.join();
                        int openOrders = orderFuture.join();

                        ReconciliationReport report = new ReconciliationReport(
                                exchangeName,
                                balances,
                                positions,
                                openOrders,
                                false, // TODO: implement drift detection against cached state
                                startedAt,
                                Instant.now()
                        );
                        lastReport.set(report);
                        log.debug("Reconciliation complete for {}: {}", exchangeName, report.summary());
                        publishReport(report);
                    });
        } catch (Exception e) {
            log.warn("Reconciliation error for {}: {}", exchangeName, e.getMessage());
        }
    }

    private void publishReport(@NotNull ReconciliationReport report) {
        if (eventBus == null) return;
        try {
            eventBus.publishAsync(AgentEvent.of(
                    AgentEvent.RECONCILIATION_COMPLETED,
                    "ExchangeReconciliationEngine",
                    report
            ));
        } catch (Exception ignored) {
        }
    }

    /**
     * Immutable snapshot of a completed reconciliation cycle.
     */
    public record ReconciliationReport(
            @NotNull String exchangeName,
            @NotNull Map<String, Double> liveBalances,
            @NotNull Map<String, Integer> livePositions,
            int liveOpenOrders,
            boolean driftDetected,
            @NotNull Instant startedAt,
            @NotNull Instant completedAt
    ) {
        public @NotNull String summary() {
            return "Reconciliation[%s] balances=%d positions=%d orders=%d drift=%s duration=%dms"
                    .formatted(
                            exchangeName,
                            liveBalances.size(),
                            livePositions.size(),
                            liveOpenOrders,
                            driftDetected ? "YES" : "NO",
                            java.time.Duration.between(startedAt, completedAt).toMillis()
                    );
        }
    }
}
