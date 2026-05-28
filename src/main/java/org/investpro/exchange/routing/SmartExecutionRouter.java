package org.investpro.exchange.routing;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionRoute;
import org.investpro.exchange.execution.ExecutionVenue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes execution requests to the best available exchange/venue by scoring
 * candidates across four weighted dimensions:
 * <ul>
 *   <li>Latency  — 30% weight (lower is better)</li>
 *   <li>Spread   — 30% weight (lower is better)</li>
 *   <li>Liquidity — 25% weight (higher is better)</li>
 *   <li>Fees     — 15% weight (lower is better)</li>
 * </ul>
 *
 * <p>Scores are maintained as exponential moving averages (EMA) to ensure
 * recent observations are weighted more heavily than historical ones.
 *
 * <p>If no exchanges are available (list is empty) the router returns a
 * {@link ExecutionVenue#PAPER} route to allow safe graceful degradation.
 *
 * <p>Routing decisions are published to the {@link AgentEventBus} when one
 * is provided, enabling downstream monitoring and alerting.
 */
@Slf4j
public class SmartExecutionRouter {

    // ── Scoring weights (must sum to 1.0) ─────────────────────────────────────
    private static final double WEIGHT_LATENCY   = 0.30;
    private static final double WEIGHT_SPREAD    = 0.30;
    private static final double WEIGHT_LIQUIDITY = 0.25;
    private static final double WEIGHT_FEES      = 0.15;

    // ── EMA smoothing factor ──────────────────────────────────────────────────
    private static final double EMA_ALPHA = 0.2;

    // ── Default values for unknown metrics ────────────────────────────────────
    private static final double DEFAULT_LATENCY_MS   = 100.0;
    private static final double DEFAULT_LIQUIDITY    = 0.5;
    private static final double DEFAULT_SPREAD_BPS   = 5.0;
    private static final double DEFAULT_FEES_SCORE   = 0.5;  // neutral fee score

    // ── Internal state ────────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, Double> exchangeLatencyMs      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> exchangeLiquidityScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> exchangeSpreadBps       = new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;

    /**
     * Constructs the router with an optional event bus for telemetry.
     *
     * @param eventBus optional event bus; {@code null} disables telemetry publishing
     */
    public SmartExecutionRouter(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Selects the best execution route for the given request from the list of
     * available exchanges.
     *
     * <p>If {@code availableExchanges} is empty, returns a PAPER route to allow
     * the calling system to fail gracefully.
     *
     * @param request            the execution request to route
     * @param availableExchanges exchange names eligible for routing
     * @return a future resolving to the selected {@link ExecutionRoute}
     */
    public @NotNull CompletableFuture<ExecutionRoute> route(
            @NotNull ExecutionRequest request,
            @NotNull List<String> availableExchanges
    ) {
        if (availableExchanges.isEmpty()) {
            log.warn("No available exchanges — returning PAPER route for request {}", request.requestId());
            ExecutionRoute paperRoute = new ExecutionRoute(
                    UUID.randomUUID().toString(),
                    request.requestId(),
                    ExecutionVenue.PAPER,
                    "PAPER",
                    null,
                    0.0,
                    0.0,
                    0.0,
                    0L,
                    0.0,
                    "No exchanges available — degraded to paper trading",
                    Instant.now()
            );
            return CompletableFuture.completedFuture(paperRoute);
        }

        Optional<String> best = selectBestExchange(availableExchanges);
        String selectedExchange = best.orElse(availableExchanges.get(0));
        ExecutionRoute route = buildRoute(request, selectedExchange);

        log.info("Route selected: {} → {} [{}]", request.requestId(), selectedExchange, route.routingReason());
        publishRouteEvent(route, request);

        return CompletableFuture.completedFuture(route);
    }

    /**
     * Updates the EMA latency for the given exchange.
     *
     * @param exchangeName the exchange to update
     * @param latencyMs    observed latency in milliseconds
     */
    public void updateLatency(@NotNull String exchangeName, double latencyMs) {
        exchangeLatencyMs.merge(exchangeName, latencyMs,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    /**
     * Updates the liquidity score for the given exchange.
     *
     * @param exchangeName   the exchange to update
     * @param liquidityScore observed liquidity score in [0.0, 1.0]
     */
    public void updateLiquidity(@NotNull String exchangeName, double liquidityScore) {
        exchangeLiquidityScores.merge(exchangeName, liquidityScore,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    /**
     * Updates the spread estimate for the given exchange.
     *
     * @param exchangeName the exchange to update
     * @param spreadBps    observed spread in basis points
     */
    public void updateSpread(@NotNull String exchangeName, double spreadBps) {
        exchangeSpreadBps.merge(exchangeName, spreadBps,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    /**
     * Returns a snapshot of composite routing scores per exchange.
     * Useful for dashboard display and diagnostics.
     *
     * @return map of exchange name → composite score in [0.0, 1.0]
     */
    public @NotNull Map<String, Double> getRoutingScores() {
        Map<String, Double> scores = new HashMap<>();
        for (String exchange : exchangeLatencyMs.keySet()) {
            scores.put(exchange, computeCompositeScore(exchange));
        }
        return Collections.unmodifiableMap(scores);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes a composite score for {@code exchangeName} in [0.0, 1.0].
     * Higher is better.
     */
    private double computeCompositeScore(@NotNull String exchangeName) {
        double latency   = exchangeLatencyMs.getOrDefault(exchangeName, DEFAULT_LATENCY_MS);
        double liquidity = exchangeLiquidityScores.getOrDefault(exchangeName, DEFAULT_LIQUIDITY);
        double spread    = exchangeSpreadBps.getOrDefault(exchangeName, DEFAULT_SPREAD_BPS);

        // Normalise: lower latency → higher score (cap at 500ms for normalisation)
        double latencyScore   = Math.max(0.0, 1.0 - (latency / 500.0));
        // Lower spread → higher score (cap at 20bps)
        double spreadScore    = Math.max(0.0, 1.0 - (spread / 20.0));
        // Liquidity is already in [0,1] — higher is better
        double liquidityScore = Math.min(1.0, Math.max(0.0, liquidity));
        // Fees score is constant for now; can be per-exchange in future
        double feesScore      = DEFAULT_FEES_SCORE;

        return WEIGHT_LATENCY   * latencyScore
             + WEIGHT_SPREAD    * spreadScore
             + WEIGHT_LIQUIDITY * liquidityScore
             + WEIGHT_FEES      * feesScore;
    }

    /** Selects the exchange with the highest composite score. */
    private @NotNull Optional<String> selectBestExchange(@NotNull List<String> available) {
        return available.stream()
                .max((a, b) -> Double.compare(computeCompositeScore(a), computeCompositeScore(b)));
    }

    /** Builds an {@link ExecutionRoute} for the selected exchange. */
    private @NotNull ExecutionRoute buildRoute(
            @NotNull ExecutionRequest request,
            @NotNull String exchangeName
    ) {
        double score     = computeCompositeScore(exchangeName);
        double latency   = exchangeLatencyMs.getOrDefault(exchangeName, DEFAULT_LATENCY_MS);
        double liquidity = exchangeLiquidityScores.getOrDefault(exchangeName, DEFAULT_LIQUIDITY);
        double spread    = exchangeSpreadBps.getOrDefault(exchangeName, DEFAULT_SPREAD_BPS);

        ExecutionVenue venue = request.allowVenueSwitch()
                ? request.preferredVenue()
                : request.preferredVenue();

        return new ExecutionRoute(
                UUID.randomUUID().toString(),
                request.requestId(),
                venue,
                exchangeName,
                null,
                0.0,
                0.0,
                spread,
                (long) latency,
                liquidity,
                "Composite score=%.3f (lat=%.1fms spread=%.1fbps liq=%.2f)"
                        .formatted(score, latency, spread, liquidity),
                Instant.now()
        );
    }

    /** Publishes a route-selected event to the event bus if available. */
    private void publishRouteEvent(
            @NotNull ExecutionRoute route,
            @NotNull ExecutionRequest request
    ) {
        if (eventBus == null) return;
        try {
            Map<String, Object> meta = Map.of(
                    "requestId",    request.requestId(),
                    "routeId",      route.routeId(),
                    "exchange",     route.exchangeName(),
                    "venue",        route.venue().name(),
                    "latencyMs",    route.estimatedLatencyMs(),
                    "liquidity",    route.liquidityScore(),
                    "reason",       route.routingReason()
            );
            eventBus.publishAsync(
                    AgentEvent.of("EXECUTION_ROUTE_SELECTED", "SmartExecutionRouter", route.routeId(), meta));
        } catch (Exception ignored) {
            // event bus failures must not affect routing
        }
    }
}
