package org.investpro.strategy;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.execution.TradeExecutionCoordinator;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.TradePair;
import org.investpro.research.StrategyRankingEngine;
import org.investpro.risk.TradeRiskContext;
import org.investpro.spi.PluginRegistry;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.provider.StrategyProviderRegistry;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.HOLD;
import static org.investpro.utils.Side.SELL;

/**
 * StrategyEngine coordinates multi-strategy signal generation.
 *
 * Responsibilities:
 * - find compatible strategies
 * - generate normalized StrategySignal objects
 * - build consensus StrategySignal
 * - cache last strategy signals
 * - forward executable signals to the execution/risk pipeline
 */
@Slf4j
@Data
public class StrategyEngine {

    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private final StrategyRegistry strategyRegistry;
    private final PluginRegistry pluginRegistry;
    private final StrategyProviderRegistry providerRegistry;

    private final Map<String, StrategyContext> contextCache = new ConcurrentHashMap<>();
    private final Map<String, StrategySignal> lastSignalCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSignalTimestampCache = new ConcurrentHashMap<>();

    private StrategyRankingEngine strategyRankingEngine;

    public StrategyEngine(@NotNull TradeExecutionCoordinator tradeExecutionCoordinator) {
        this.tradeExecutionCoordinator = Objects.requireNonNull(
                tradeExecutionCoordinator,
                "tradeExecutionCoordinator must not be null");
        this.strategyRegistry = StrategyRegistry.getInstance();
        this.pluginRegistry = PluginRegistry.loadDefault();
        this.providerRegistry = StrategyProviderRegistry.getInstance();
        this.strategyRankingEngine = new StrategyRankingEngine();
        log.info("StrategyEngine initialized with {} lazy strategy descriptors and {} plugin strategy providers",
                providerRegistry.size(), pluginRegistry.strategyProviders().size());
    }

    // ============================================================================
    // Strategy Execution & Signal Generation
    // ============================================================================

    /**
     * Generates StrategySignal objects from all compatible strategies.
     */
    public @NotNull List<StrategySignal> generateSignalsForContext(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        cacheContext(context);

        List<TradingStrategy> compatibleStrategies = getCompatibleStrategies(context);
        List<StrategySignal> signals = new ArrayList<>();

        for (TradingStrategy strategy : compatibleStrategies) {
            try {
                StrategySignal signal = generateSignalFromStrategy(strategy, context);

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
     * Generates a StrategySignal from a specific strategy ID.
     */
    public @NotNull StrategySignal generateSignalFromStrategyId(
            @NotNull String strategyId,
            @NotNull StrategyContext context) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        TradingStrategy strategy = strategyRegistry.getStrategy(strategyId);
        if (strategy == null) {
            strategy = providerRegistry.resolve(strategyId).orElse(null);
        }

        if (strategy == null) {
            log.warn("StrategyEngine: Strategy not found: {}", strategyId);
            return holdSignal(null, context, "Strategy not found: " + strategyId);
        }

        return generateSignalFromStrategy(strategy, context);
    }

    /**
     * Generates a StrategySignal from a strategy object.
     */
    public @NotNull StrategySignal generateSignalFromStrategy(
            @NotNull TradingStrategy strategy,
            @NotNull StrategyContext context) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String strategyId = safeStrategyId(strategy);

        if (!isStrategyCompatible(strategy, context)) {
            log.debug("StrategyEngine: Strategy {} not compatible with context", strategyId);
            return holdSignal(strategy, context, "Strategy is not compatible with this context");
        }

        if (!strategy.isEnabled()) {
            log.debug("StrategyEngine: Strategy {} is disabled", strategyId);
            return holdSignal(strategy, context, "Strategy is disabled");
        }

        try {
            StrategySignal signal = strategy.generateSignal(context);

            if (signal == null) {
                return holdSignal(strategy, context, "Strategy returned null signal");
            }

            return signal;

        } catch (Exception exception) {
            log.error(
                    "StrategyEngine: Error executing strategy {}: {}",
                    strategyId,
                    exception.getMessage(),
                    exception);

            return holdSignal(strategy, context, "Strategy execution error: " + exception.getMessage());
        }
    }

    /**
     * Gets all enabled strategies compatible with the context.
     */
    public @NotNull List<TradingStrategy> getCompatibleStrategies(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        return strategyRegistry.getEnabledStrategies()
                .stream()
                .filter(strategy -> isStrategyCompatible(strategy, context))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether a strategy supports the current symbol, timeframe, asset type,
     * contract type, warmup bars, and market behavior.
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

        TradingSessionStatus sessionStatus = context.getTradingSessionStatus();
        if (sessionStatus == null && context.getSymbol().getTradingSession() != null) {
            sessionStatus = context.getSymbol().getTradingSessionStatus();
        }
        if (sessionStatus != null && !sessionStatus.isTradable()) {
            log.debug(
                    "StrategyEngine: Strategy {} incompatible for {} because trading session is {}",
                    safeStrategyId(strategy),
                    context.getSymbol(),
                    sessionStatus);
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

        return context.getMarketBehavior() == null
                || strategy.supportsMarketBehavior(context.getMarketBehavior());
    }

    // ============================================================================
    // Multi-Strategy Coordination
    // ============================================================================

    /**
     * Generates a consensus StrategySignal requiring multiple strategies to agree.
     *
     * If no consensus exists, returns a HOLD StrategySignal.
     */
    public @NotNull StrategySignal generateConsensusSignal(
            @NotNull StrategyContext context,
            int minAgreements) {
        Objects.requireNonNull(context, "context must not be null");

        if (minAgreements <= 0) {
            minAgreements = 1;
        }

        List<StrategySignal> signals = generateSignalsForContext(context);

        List<StrategySignal> buySignals = signals.stream()
                .filter(signal -> signal.getSide() == BUY)
                .toList();

        List<StrategySignal> sellSignals = signals.stream()
                .filter(signal -> signal.getSide() == SELL)
                .toList();

        if (buySignals.size() >= minAgreements) {
            return buildConsensusFromSignals(context, BUY, buySignals);
        }

        if (sellSignals.size() >= minAgreements) {
            return buildConsensusFromSignals(context, SELL, sellSignals);
        }

        log.info(
                "StrategyEngine: No consensus. BUY={}, SELL={}, required={}",
                buySignals.size(),
                sellSignals.size(),
                minAgreements);

        return holdSignal(null, context, "No strategy consensus");
    }

    /**
     * Selects the best actionable signal from all compatible strategies.
     *
     * Useful when you do not require consensus but want the highest-confidence
     * valid BUY/SELL signal.
     */
    public @NotNull StrategySignal generateBestSignal(@NotNull StrategyContext context) {
        Objects.requireNonNull(context, "context must not be null");

        return generateSignalsForContext(context)
                .stream()
                .filter(signal -> signal.getSide() == BUY || signal.getSide() == SELL)
                .max(Comparator.comparingDouble(StrategySignal::getConfidence))
                .orElseGet(() -> holdSignal(null, context, "No actionable strategy signal"));
    }

    /**
     * Process a generated StrategySignal through the execution pipeline.
     *
     * Important:
     * This should still go through risk management before real execution.
     */
    public void onSignalGenerated(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext) {
        Objects.requireNonNull(signal, "signal must not be null");
        Objects.requireNonNull(riskContext, "riskContext must not be null");

        if (signal.getSide() == HOLD) {
            log.debug("StrategyEngine: Signal is HOLD, skipping execution. reason={}", signalReason(signal));
            return;
        }

        log.info(
                "StrategyEngine: StrategySignal generated: strategyId={}, symbol={}, side={}, confidence={}",
                signal.getStrategyId(),
                signal.getSymbol(),
                signal.getSide(),
                signal.getConfidence());

        // Do not execute directly here unless your TradeExecutionCoordinator already
        // performs risk approval internally.
        //
        // Preferred architecture:
        // StrategySignal -> RiskEngine.evaluateSignal(...) -> ExecutionCoordinator
    }

    // ============================================================================
    // Caching & State Management
    // ============================================================================

    public void cacheSignal(
            @NotNull String strategyId,
            @NotNull StrategyContext context,
            @NotNull StrategySignal signal) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(signal, "signal must not be null");

        if (context.getSymbol() == null || context.getTimeframe() == null) {
            log.warn("StrategyEngine: Cannot cache signal with missing symbol/timeframe.");
            return;
        }

        String cacheKey = buildCacheKey(strategyId, context.getSymbol(), context.getTimeframe());

        lastSignalCache.put(cacheKey, signal);
        lastSignalTimestampCache.put(cacheKey, System.currentTimeMillis());
    }

    public @Nullable StrategySignal getLastCachedSignal(
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

    private @NotNull StrategySignal holdSignal(
            @Nullable TradingStrategy strategy,
            @NotNull StrategyContext context,
            @NotNull String reason) {
        String strategyId = strategy == null ? "strategy-engine" : safeStrategyId(strategy);
        String strategyName = strategy == null ? "StrategyEngine" : safeStrategyName(strategy);

        return StrategySignal.builder()
                .strategyId(strategyId)
                .strategyName(strategyName)
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(HOLD)
                .confidence(0.0)
                .entryPrice(context.getCurrentPrice())
                .stopLossPrice(0.0)
                .takeProfitPrice(0.0)
                .riskRewardRatio(0.0)
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private @NotNull StrategySignal buildConsensusFromSignals(
            @NotNull StrategyContext context,
            @NotNull Side side,
            @NotNull List<StrategySignal> agreeingSignals) {
        double avgConfidence = agreeingSignals.stream()
                .mapToDouble(StrategySignal::getConfidence)
                .average()
                .orElse(0.0);

        double avgEntry = agreeingSignals.stream()
                .mapToDouble(StrategySignal::getEntryPrice)
                .filter(value -> value > 0)
                .average()
                .orElse(context.getCurrentPrice());

        double avgStopLoss = agreeingSignals.stream()
                .mapToDouble(StrategySignal::getStopLossPrice)
                .filter(value -> value > 0)
                .average()
                .orElse(0.0);

        double avgTakeProfit = agreeingSignals.stream()
                .mapToDouble(StrategySignal::getTakeProfitPrice)
                .filter(value -> value > 0)
                .average()
                .orElse(0.0);

        double riskRewardRatio = calculateRiskRewardRatio(avgEntry, avgStopLoss, avgTakeProfit);

        String strategyIds = agreeingSignals.stream()
                .map(StrategySignal::getStrategyId)
                .collect(Collectors.joining(","));

        String strategyNames = agreeingSignals.stream()
                .map(StrategySignal::getStrategyName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));

        return StrategySignal.builder()
                .strategyId("consensus:" + strategyIds)
                .strategyName(strategyNames.isEmpty() ? "Consensus" : strategyNames)
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(side)
                .confidence(avgConfidence)
                .entryPrice(avgEntry)
                .stopLossPrice(avgStopLoss)
                .takeProfitPrice(avgTakeProfit)
                .riskRewardRatio(riskRewardRatio)
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason("Consensus signal from strategies: " + strategyIds)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private double calculateRiskRewardRatio(double entry, double stopLoss, double takeProfit) {
        double risk = Math.abs(entry - stopLoss);
        double reward = Math.abs(takeProfit - entry);

        if (risk <= 0) {
            return 0.0;
        }

        return reward / risk;
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

    private String safeStrategyName(@Nullable TradingStrategy strategy) {
        if (strategy == null || strategy.getMetadata() == null) {
            return "UNKNOWN";
        }

        String displayName = strategy.getMetadata().getDisplayName();
        return displayName != null && !displayName.isBlank() ? displayName : "UNKNOWN";
    }

    private String signalReason(@NotNull StrategySignal signal) {
        try {
            if (signal.getReason() != null) {
                return signal.getReason();
            }
        } catch (Exception ignored) {
            // Some StrategySignal versions may use reasons instead of reason.
            log.error(ignored.getMessage(), ignored);
        }

        return "";
    }
}
