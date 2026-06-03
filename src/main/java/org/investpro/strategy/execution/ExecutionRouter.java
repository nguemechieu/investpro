package org.investpro.strategy.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes execution plans to the appropriate venue based on symbol type and
 * venue health.
 * Tracks venue error counts and adapts routing decisions accordingly.
 *
 * <p>
 * <strong>CRITICAL:</strong> This router selects a venue for the plan
 * but does NOT submit or execute the plan against any exchange.
 * The ExecutionEngine owns all order submission.
 * </p>
 */
@Slf4j
public class ExecutionRouter {

    private static volatile ExecutionRouter instance;

    private final ConcurrentHashMap<ExecutionVenue, AtomicInteger> venueErrorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ExecutionVenue, Boolean> venueHealthy = new ConcurrentHashMap<>();

    private static final String SOURCE = "ExecutionRouter";
    private static final int MAX_ERRORS_BEFORE_DEMOTION = 5;

    private ExecutionRouter() {
        for (ExecutionVenue venue : ExecutionVenue.values()) {
            venueErrorCounts.put(venue, new AtomicInteger(0));
            venueHealthy.put(venue, true);
        }
        log.info("ExecutionRouter initialised with {} venues", ExecutionVenue.values().length);
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton ExecutionRouter
     */
    public static ExecutionRouter getInstance() {
        ExecutionRouter local = instance;
        if (local == null) {
            synchronized (ExecutionRouter.class) {
                local = instance;
                if (local == null) {
                    local = new ExecutionRouter();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Selects the best execution venue for the plan and returns an updated plan
     * with venue set.
     *
     * @param plan   the execution plan to route
     * @param record the strategy lifecycle record
     * @return ExecutionPlan with the venue field set to the selected venue
     */
    public ExecutionPlan route(ExecutionPlan plan, StrategyLifecycleRecord record) {
        if (plan == null)
            throw new IllegalArgumentException("ExecutionPlan must not be null");

        ExecutionVenue selectedVenue = selectVenue(plan.getSymbol(), record);

        log.debug("Routing: assignment={} symbol={} -> venue={}",
                plan.getAssignmentId(), plan.getSymbol(), selectedVenue);

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.EXECUTION_ROUTE_SELECTED, SOURCE, selectedVenue));

        ExecutionPlan.ExecutionPlanBuilder builder = ExecutionPlan.builder()
                .planId(plan.getPlanId())
                .assignmentId(plan.getAssignmentId())
                .strategyId(plan.getStrategyId())
                .symbol(plan.getSymbol())
                .timeframe(plan.getTimeframe())
                .side(plan.getSide())
                .orderType(plan.getOrderType())
                .timeInForce(plan.getTimeInForce())
                .units(plan.getUnits())
                .notionalValue(plan.getNotionalValue())
                .entryPrice(plan.getEntryPrice())
                .stopLoss(plan.getStopLoss())
                .takeProfit(plan.getTakeProfit())
                .riskRewardRatio(plan.getRiskRewardRatio())
                .riskAmount(plan.getRiskAmount())
                .riskPercent(plan.getRiskPercent())
                .leverage(plan.getLeverage())
                .slippageTolerance(plan.getSlippageTolerance())
                .venue(selectedVenue)
                .aiApproved(plan.isAiApproved())
                .aiConfidence(plan.getAiConfidence())
                .aiReasoningSummary(plan.getAiReasoningSummary())
                .isValid(plan.isValid())
                .riskApproved(plan.isRiskApproved())
                .createdAt(plan.getCreatedAt())
                .planValidUntil(plan.getPlanValidUntil());

        if (plan.getValidationNotes() != null) {
            for (String note : plan.getValidationNotes()) {
                builder.validationNote(note);
            }
        }

        return builder.build();
    }

    /**
     * Records a venue error. After {@code MAX_ERRORS_BEFORE_DEMOTION} errors, marks
     * venue unhealthy.
     *
     * @param venue the venue that experienced an error
     */
    public void recordVenueError(ExecutionVenue venue) {
        if (venue == null)
            return;
        int errors = venueErrorCounts.computeIfAbsent(venue, v -> new AtomicInteger(0))
                .incrementAndGet();
        if (errors >= MAX_ERRORS_BEFORE_DEMOTION) {
            venueHealthy.put(venue, false);
            log.warn("Venue marked unhealthy after {} errors: {}", errors, venue);
        }
    }

    /**
     * Resets a venue's error count and marks it healthy.
     *
     * @param venue the venue to recover
     */
    public void recoverVenue(ExecutionVenue venue) {
        if (venue == null)
            return;
        venueErrorCounts.computeIfAbsent(venue, v -> new AtomicInteger(0)).set(0);
        venueHealthy.put(venue, true);
        log.info("Venue recovered: {}", venue);
    }

    /**
     * Returns whether a venue is currently healthy.
     *
     * @param venue the venue to check
     * @return true if healthy
     */
    public boolean isVenueHealthy(ExecutionVenue venue) {
        return venueHealthy.getOrDefault(venue, false);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ExecutionVenue selectVenue(String symbol, StrategyLifecycleRecord record) {
        if (symbol == null)
            return ExecutionVenue.PAPER_TRADE;

        // Use paper trading for non-live strategies
        if (record != null && !record.getLifecycleStatus().isLive()) {
            return ExecutionVenue.PAPER_TRADE;
        }

        String upper = symbol.toUpperCase();

        if (upper.contains("/SOL") || upper.startsWith("SOL/") || upper.contains("SOL")) {
            return pickHealthy(ExecutionVenue.SOLANA_DEX, ExecutionVenue.COINBASE_ADVANCED);
        }

        if (upper.contains("/XLM") || upper.startsWith("XLM/") || upper.contains("XLM")) {
            return pickHealthy(ExecutionVenue.STELLAR, ExecutionVenue.COINBASE_ADVANCED);
        }

        // Crypto symbols
        if (upper.contains("BTC") || upper.contains("ETH") || upper.contains("USDT")
                || upper.contains("BNB") || upper.contains("XRP")) {
            return pickHealthy(ExecutionVenue.COINBASE_ADVANCED, ExecutionVenue.BINANCE_SPOT);
        }

        // Forex pairs (contain _ separating currencies, e.g. EUR_USD)
        if (upper.contains("_") || isForexPair(upper)) {
            return pickHealthy(ExecutionVenue.OANDA_REST, ExecutionVenue.INTERACTIVE_BROKERS);
        }

        // Indices or equities
        if (upper.contains("SPX") || upper.contains("NDX") || upper.contains("US30")) {
            return pickHealthy(ExecutionVenue.INTERACTIVE_BROKERS, ExecutionVenue.OANDA_REST);
        }

        // Default fallback
        return pickHealthy(ExecutionVenue.OANDA_REST, ExecutionVenue.PAPER_TRADE);
    }

    private ExecutionVenue pickHealthy(ExecutionVenue preferred, ExecutionVenue fallback) {
        if (venueHealthy.getOrDefault(preferred, true))
            return preferred;
        if (venueHealthy.getOrDefault(fallback, true))
            return fallback;
        return ExecutionVenue.PAPER_TRADE;
    }

    private boolean isForexPair(String symbol) {
        String[] currencies = { "USD", "EUR", "GBP", "JPY", "AUD", "NZD", "CAD", "CHF" };
        int matches = 0;
        for (String c : currencies) {
            if (symbol.contains(c))
                matches++;
        }
        return matches >= 2;
    }
}
