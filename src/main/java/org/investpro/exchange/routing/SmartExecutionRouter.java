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
 */
@Slf4j
public class SmartExecutionRouter {

    private static final double WEIGHT_LATENCY   = 0.30;
    private static final double WEIGHT_SPREAD    = 0.30;
    private static final double WEIGHT_LIQUIDITY = 0.25;
    private static final double WEIGHT_FEES      = 0.15;

    private static final double EMA_ALPHA = 0.2;

    private static final double DEFAULT_LATENCY_MS   = 100.0;
    private static final double DEFAULT_LIQUIDITY    = 0.5;
    private static final double DEFAULT_SPREAD_BPS   = 5.0;
    private static final double DEFAULT_FEES_SCORE   = 0.5;

    private final ConcurrentHashMap<String, Double> exchangeLatencyMs      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> exchangeLiquidityScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> exchangeSpreadBps       = new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;

    public SmartExecutionRouter(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

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

    public void updateLatency(@NotNull String exchangeName, double latencyMs) {
        exchangeLatencyMs.merge(exchangeName, latencyMs,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    public void updateLiquidity(@NotNull String exchangeName, double liquidityScore) {
        exchangeLiquidityScores.merge(exchangeName, liquidityScore,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    public void updateSpread(@NotNull String exchangeName, double spreadBps) {
        exchangeSpreadBps.merge(exchangeName, spreadBps,
                (prev, next) -> prev * (1 - EMA_ALPHA) + next * EMA_ALPHA);
    }

    public @NotNull Map<String, Double> getRoutingScores() {
        Map<String, Double> scores = new HashMap<>();
        for (String exchange : exchangeLatencyMs.keySet()) {
            scores.put(exchange, computeCompositeScore(exchange));
        }
        return Collections.unmodifiableMap(scores);
    }

    private double computeCompositeScore(@NotNull String exchangeName) {
        double latency   = exchangeLatencyMs.getOrDefault(exchangeName, DEFAULT_LATENCY_MS);
        double liquidity = exchangeLiquidityScores.getOrDefault(exchangeName, DEFAULT_LIQUIDITY);
        double spread    = exchangeSpreadBps.getOrDefault(exchangeName, DEFAULT_SPREAD_BPS);

        double latencyScore   = Math.max(0.0, 1.0 - (latency / 500.0));
        double spreadScore    = Math.max(0.0, 1.0 - (spread / 20.0));
        double liquidityScore = Math.min(1.0, Math.max(0.0, liquidity));
        double feesScore      = DEFAULT_FEES_SCORE;

        return WEIGHT_LATENCY   * latencyScore
             + WEIGHT_SPREAD    * spreadScore
             + WEIGHT_LIQUIDITY * liquidityScore
             + WEIGHT_FEES      * feesScore;
    }

    private @NotNull Optional<String> selectBestExchange(@NotNull List<String> available) {
        return available.stream()
                .max((a, b) -> Double.compare(computeCompositeScore(a), computeCompositeScore(b)));
    }

    private @NotNull ExecutionRoute buildRoute(
            @NotNull ExecutionRequest request,
            @NotNull String exchangeName
    ) {
        double score     = computeCompositeScore(exchangeName);
        double latency   = exchangeLatencyMs.getOrDefault(exchangeName, DEFAULT_LATENCY_MS);
        double liquidity = exchangeLiquidityScores.getOrDefault(exchangeName, DEFAULT_LIQUIDITY);
        double spread    = exchangeSpreadBps.getOrDefault(exchangeName, DEFAULT_SPREAD_BPS);

        ExecutionVenue venue = request.preferredVenue();

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
        }
    }
}
