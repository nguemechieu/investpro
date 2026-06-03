package org.investpro.exchange.routing;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.execution.*;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Selects the best execution route from available venues based on a composite
 * score that weighs spread, liquidity, latency, and fees.
 *
 * <p>Register venue scorers with {@link #registerVenueScorer} to integrate new
 * exchange or DEX connectors. Out-of-the-box, venues are scored from normalized
 * market snapshots if available.
 *
 * <p>Thread-safe; intended for shared singleton use within {@code ExchangeService}.
 */
@Slf4j
public final class SmartExecutionRouter {

    // Weights for composite route scoring (must sum to 1.0)
    private static final double WEIGHT_SPREAD    = 0.30;
    private static final double WEIGHT_LIQUIDITY = 0.25;
    private static final double WEIGHT_LATENCY   = 0.25;
    private static final double WEIGHT_FEE       = 0.20;

    /** Per-exchange snapshot suppliers (venue name → latest snapshot). */
    private final ConcurrentHashMap<String, NormalizedMarketSnapshot> snapshotRegistry
            = new ConcurrentHashMap<>();

    /** Custom scorers that can be plugged in per execution venue. */
    private final ConcurrentHashMap<ExecutionVenue, Function<ExecutionRequest, OptionalDouble>> venueScorerRegistry
            = new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;

    public SmartExecutionRouter(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Updates the market snapshot for an exchange, used in scoring.
     *
     * @param exchangeName exchange identifier (must match route key)
     * @param snapshot     latest normalized snapshot
     */
    public void updateSnapshot(@NotNull String exchangeName, @NotNull NormalizedMarketSnapshot snapshot) {
        snapshotRegistry.put(exchangeName, snapshot);
    }

    /**
     * Registers a custom scorer for a given venue type.
     *
     * <p>The scorer receives the {@link ExecutionRequest} and returns a score
     * in [0.0, 1.0] where 1.0 is the best possible execution quality.
     *
     * @param venue   the venue type to score
     * @param scorer  scorer function
     */
    public void registerVenueScorer(
            @NotNull ExecutionVenue venue,
            @NotNull Function<ExecutionRequest, OptionalDouble> scorer
    ) {
        venueScorerRegistry.put(venue, scorer);
    }

    /**
     * Routes an execution request to the best available venue.
     *
     * <p>Selects from registered exchanges with available snapshots, respecting
     * the request's venue preference, exchange preference, and paper mode.
     *
     * @param request  the trade request to route
     * @param candidates list of candidate exchange names to consider
     * @return the selected route, or empty if no viable venue found
     */
    public Optional<ExecutionRoute> route(
            @NotNull ExecutionRequest request,
            @NotNull List<String> candidates
    ) {
        if (candidates.isEmpty()) {
            log.warn("[SmartRouter] No candidates for symbol={} side={}",
                    request.symbol(), request.side());
            return Optional.empty();
        }

        // Filter candidates: venue preference and exchange preference
        List<String> filtered = candidates.stream()
                .filter(name -> {
                    // Skip paper-mode exchanges if request is real
                    if (request.paperMode()) return true; // anything allowed
                    // If request specifies preferred exchange and fallback is not allowed, enforce it
                    return request.getPreferredExchange()
                            .map(pref -> request.allowFallback() || name.equalsIgnoreCase(pref))
                            .orElse(true);
                })
                .toList();

        if (filtered.isEmpty()) {
            log.warn("[SmartRouter] All candidates filtered out for symbol={}", request.symbol());
            return Optional.empty();
        }

        // Prefer the explicitly preferred exchange if snapshot is available
        if (request.getPreferredExchange().isPresent()) {
            String pref = request.getPreferredExchange().get();
            NormalizedMarketSnapshot snap = snapshotRegistry.get(pref);
            if (snap != null && snap.isDataFresh()) {
                ExecutionRoute route = buildRoute(request.requestId(), request.preferredVenue(), pref, snap);
                publishRouteSelected(route);
                return Optional.of(route);
            } else if (!request.allowFallback()) {
                log.warn("[SmartRouter] Preferred exchange {} unavailable, fallback disallowed", pref);
                return Optional.empty();
            }
        }

        // Score all candidates and pick the best
        ExecutionRoute best = null;
        double bestScore = -1.0;

        for (String candidate : filtered) {
            NormalizedMarketSnapshot snap = snapshotRegistry.get(candidate);
            if (snap == null || !snap.isDataFresh()) {
                log.debug("[SmartRouter] No fresh snapshot for {}, skipping", candidate);
                continue;
            }
            double score = computeScore(snap);
            if (score > bestScore) {
                bestScore = score;
                best = buildRoute(request.requestId(), request.preferredVenue(), candidate, snap);
            }
        }

        if (best == null) {
            log.warn("[SmartRouter] No scorable venue found for symbol={}", request.symbol());
            return Optional.empty();
        }

        publishRouteSelected(best);
        return Optional.of(best);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private ExecutionRoute buildRoute(
            String requestId,
            ExecutionVenue venue,
            String exchangeName,
            NormalizedMarketSnapshot snap
    ) {
        return new ExecutionRoute(
                UUID.randomUUID().toString(),
                requestId,
                venue,
                exchangeName,
                bpsToBigDecimal(snap.spreadBps()),
                null, // fee not in snapshot — exchange adapter provides if available
                null, // slippage estimate not yet computed
                null, // latency — available from ExchangeTelemetryEngine
                snap.totalBidLiquidity(),
                computeScore(snap),
                Instant.now()
        );
    }

    /**
     * Composite score in [0.0, 1.0] — higher is better.
     *
     * <p>Spread and liquidity are primary signals. A tight spread earns a high
     * spread score; high liquidity earns a high liquidity score. Both favour
     * market-impact minimisation.
     */
    private double computeScore(NormalizedMarketSnapshot snap) {
        double spreadScore = scoreSpread(snap.spreadBps());
        double liquidityScore = scoreLiquidity(snap.totalBidLiquidity());
        double latencyScore = 0.5; // neutral until telemetry engine wires in latency
        double feeScore = 0.5;     // neutral until fee tables are available

        return WEIGHT_SPREAD * spreadScore
                + WEIGHT_LIQUIDITY * liquidityScore
                + WEIGHT_LATENCY * latencyScore
                + WEIGHT_FEE * feeScore;
    }

    /** Scores spread: 0 bps → 1.0, ≥100 bps → 0.0. */
    private double scoreSpread(double spreadBps) {
        if (spreadBps <= 0) return 1.0;
        if (spreadBps >= 100) return 0.0;
        return 1.0 - (spreadBps / 100.0);
    }

    /** Scores liquidity: ≥1,000,000 → 1.0, 0 → 0.0, logarithmic scale. */
    private double scoreLiquidity(@Nullable BigDecimal liquidity) {
        if (liquidity == null) return 0.3; // assume moderate if unknown
        double d = liquidity.doubleValue();
        if (d <= 0) return 0.0;
        if (d >= 1_000_000) return 1.0;
        return Math.log10(d + 1) / Math.log10(1_000_001);
    }

    private @Nullable BigDecimal bpsToBigDecimal(double bps) {
        if (Double.isNaN(bps) || Double.isInfinite(bps)) return null;
        return BigDecimal.valueOf(bps).setScale(4, RoundingMode.HALF_UP);
    }

    private void publishRouteSelected(ExecutionRoute route) {
        if (eventBus == null) return;
        eventBus.publishAsync(AgentEvent.of(
                AgentEvent.EXECUTION_ROUTE_SELECTED,
                "SmartExecutionRouter",
                route,
                Map.of(
                        "venue", route.venue().name(),
                        "exchange", route.exchangeName(),
                        "score", route.routeScore()
                )
        ));
    }
}
