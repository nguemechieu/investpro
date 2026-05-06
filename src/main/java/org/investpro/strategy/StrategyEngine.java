package org.investpro.strategy;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.investpro.timeframe.Timeframe;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.HOLD;
import static org.investpro.utils.Side.SELL;

/**
 * StrategyEngine coordinates multi-strategy signal generation and execution.
 *
 * Responsibilities:
 * - find compatible strategies
 * - generate normalized Side signals (BUY, SELL, HOLD)
 * - build consensus signals
 * - cache last signals
 * - forward executable signals to TradeExecutionCoordinator
 */
@Slf4j
public class StrategyEngine {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StrategyEngine.class);

    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private final StrategyRegistry strategyRegistry;

    private final Map<String, StrategyContext> contextCache = new ConcurrentHashMap<>();
    private final Map<String, Side> lastSignalCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSignalTimestampCache = new ConcurrentHashMap<>();

    public StrategyEngine(@NotNull TradeExecutionCoordinator tradeExecutionCoordinator) {
        this.tradeExecutionCoordinator = Objects.requireNonNull(
                tradeExecutionCoordinator,
                "tradeExecutionCoordinator must not be null");
        this.strategyRegistry = StrategyRegistry.getInstance();
    }

    // ============================================================================
    // Strategy Execution & Signal Generation
    // ============================================================================

    /**
     * Generate signals from all compatible strategies for the given context.
     *
     * @param context market context with current data
     * @return generated Side enum values (BUY, SELL, HOLD)
     */
    public @NotNull List<Side> generateSignalsForContext(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        List<TradingStrategy> compatibleStrategies = getCompatibleStrategies(context);
        List<Side> signals = new ArrayList<>();

        for (TradingStrategy strategy : compatibleStrategies) {
            try {
                @NotNull
                Side signal = generateSignalFromStrategy(strategy, context);

                signals.add(signal);
                cacheSignal(strategy.getMetadata().getStrategyId(), context, signal);

            } catch (Exception exception) {
                log.warn(
                        "StrategyEngine: Error generating signal from strategy {}: {}",
                        safeStrategyId(strategy),
                        exception.getMessage(),
                        exception);
            }
        }

        return signals;
    }

    /**
     * Generate a signal from a specific strategy ID.
     */
    public @Nullable Side generateSignalFromStrategyId(
            @NotNull String strategyId,
            @NotNull StrategyContext context) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        TradingStrategy strategy = strategyRegistry.getStrategy(strategyId);

        if (strategy == null) {
            log.warn("StrategyEngine: Strategy not found: {}", strategyId);
            return null;
        }

        return generateSignalFromStrategy(strategy, context);
    }

    /**
     * Generate a signal from a strategy object.
     */
    public @NotNull Side generateSignalFromStrategy(
            @NotNull TradingStrategy strategy,
            @NotNull StrategyContext context) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String strategyId = safeStrategyId(strategy);

        if (!isStrategyCompatible(strategy, context)) {
            log.debug("StrategyEngine: Strategy {} not compatible with context", strategyId);
            return HOLD;
        }

        if (!strategy.isEnabled()) {
            log.debug("StrategyEngine: Strategy {} is disabled", strategyId);
            return HOLD;
        }

        try {
            @NotNull
            Side signal = strategy.generateSignal(context);

            return signal;

        } catch (Exception exception) {
            log.error(
                    "StrategyEngine: Error executing strategy {}: {}",
                    strategyId,
                    exception.getMessage(),
                    exception);
            return HOLD;
        }
    }

    /**
     * Get all enabled strategies compatible with the context.
     */
    public @NotNull List<TradingStrategy> getCompatibleStrategies(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        return strategyRegistry.getEnabledStrategies()
                .stream()
                .filter(strategy -> isStrategyCompatible(strategy, context))
                .collect(Collectors.toList());
    }

    /**
     * Check if a strategy is compatible with the context.
     */
    public boolean isStrategyCompatible(
            @NotNull TradingStrategy strategy,
            @NotNull StrategyContext context) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (context.getSymbol() == null) {
            return false;
        }

        if (context.getTimeframe() == null) {
            return false;
        }

        if (context.getBarsAvailable() < strategy.requiredWarmupBars()) {
            return false;
        }

        AssetClass assetClass = context.getSymbol().getAssetClass();
        if (assetClass != null && !strategy.supportsAssetClass(assetClass)) {
            return false;
        }

        ContractType contractType = context.getSymbol().getContractType();
        if (contractType != null && !strategy.supportsContractType(contractType)) {
            return false;
        }

        if (!strategy.supportsTimeframe(context.getTimeframe())) {
            return false;
        }

        if (context.getMarketBehavior() != null
                && !strategy.supportsMarketBehavior(context.getMarketBehavior())) {
            return false;
        }

        return true;
    }

    // ============================================================================
    // Multi-Strategy Coordination
    // ============================================================================

    /**
     * Generate a consensus Side requiring multiple strategies to agree.
     *
     * @param context       market context
     * @param minAgreements minimum strategies that must agree on same side
     * @return consensus side (BUY, SELL, or HOLD)
     */
    public @NotNull Side generateConsensusSignal(
            @NotNull StrategyContext context,
            int minAgreements) {
        Objects.requireNonNull(context, "context must not be null");

        if (minAgreements <= 0) {
            minAgreements = 1;
        }

        List<Side> signals = generateSignalsForContext(context);

        long buySignals = signals.stream()
                .filter(signal -> signal == BUY)
                .count();

        long sellSignals = signals.stream()
                .filter(signal -> signal == SELL)
                .count();

        if (buySignals >= minAgreements) {
            return BUY;
        }

        if (sellSignals >= minAgreements) {
            return SELL;
        }

        log.info(
                "StrategyEngine: No consensus. BUY={}, SELL={}, required={}",
                buySignals,
                sellSignals,
                minAgreements);

        return HOLD;
    }

    /**
     * Process a generated signal through the execution pipeline.
     */
    public void onSignalGenerated(
            @NotNull Side signal,
            @NotNull TradeRiskContext riskContext) {
        Objects.requireNonNull(signal, "signal must not be null");
        Objects.requireNonNull(riskContext, "riskContext must not be null");

        if (signal == HOLD) {
            log.debug("StrategyEngine: Signal is HOLD, skipping execution.");
            return;
        }

        log.info("StrategyEngine: Signal generated: {}. Processing through execution coordinator.", signal);
    }

    // ============================================================================
    // Caching & State Management
    // ============================================================================

    public void cacheSignal(
            @NotNull String strategyId,
            @NotNull StrategyContext context,
            @NotNull Side signal) {
        String cacheKey = buildCacheKey(strategyId, context.getSymbol(), context.getTimeframe());

        lastSignalCache.put(cacheKey, signal);
        lastSignalTimestampCache.put(cacheKey, System.currentTimeMillis());
    }

    public @Nullable Side getLastCachedSignal(
            @NotNull String strategyId,
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe) {
        String cacheKey = buildCacheKey(strategyId, symbol, timeframe);
        return lastSignalCache.get(cacheKey);
    }

    public @Nullable Long getLastSignalTimestamp(
            @NotNull String strategyId,
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe) {
        String cacheKey = buildCacheKey(strategyId, symbol, timeframe);
        return lastSignalTimestampCache.get(cacheKey);
    }

    public @Nullable StrategyContext getCachedContext(
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe) {
        String cacheKey = buildContextCacheKey(symbol, timeframe);
        return contextCache.get(cacheKey);
    }

    public void cacheContext(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (context.getSymbol() == null || context.getTimeframe() == null) {
            log.warn("StrategyEngine: Cannot cache context with missing symbol/timeframe.");
            return;
        }

        String cacheKey = buildContextCacheKey(context.getSymbol(), context.getTimeframe());
        contextCache.put(cacheKey, context);
    }

    public void clearContextCache(@Nullable TradePair symbol) {
        if (symbol == null) {
            contextCache.clear();
            return;
        }

        contextCache.entrySet()
                .removeIf(entry -> entry.getKey().startsWith("context:" + symbol.getSymbol()));
    }

    public void clearAllCaches() {
        contextCache.clear();
        lastSignalCache.clear();
        lastSignalTimestampCache.clear();
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    private @NotNull Side holdSignal(
            @Nullable TradingStrategy strategy,
            @NotNull StrategyContext context,
            @NotNull String reason) {
        return HOLD;
    }

    private String buildCacheKey(
            @NotNull String strategyId,
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe) {
        return String.format("signal:%s:%s:%s", strategyId, symbol.getSymbol(), timeframe);
    }

    private String buildContextCacheKey(
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe) {
        return String.format("context:%s:%s", symbol.getSymbol(), timeframe);
    }

    private String safeStrategyId(@Nullable TradingStrategy strategy) {
        if (strategy == null || strategy.getMetadata() == null) {
            return "UNKNOWN";
        }

        return strategy.getMetadata().getStrategyId();
    }
}
