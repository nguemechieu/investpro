package org.investpro.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.data.CandleData;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.MarketBehavior;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.*;
import org.investpro.enums.timeframe.Timeframe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * StrategyDecisionService bridges the trading engine to the strategy framework.
 *
 * Responsibilities:
 * - Accept market context (symbol, timeframe, candles, prices, volatility)
 * - Query assigned strategy via StrategySelectionService
 * - Validate assignment and strategy
 * - Build StrategyContext from market data
 * - Invoke strategy.generateSignal()
 * - Return wrapped StrategyDecisionResult with signal or rejection reason
 * - Log all decisions and errors
 *
 * Thread-safe for concurrent access.
 *
 * Usage:
 * 
 * <pre>
 * StrategyDecisionService service = new StrategyDecisionService();
 * StrategyDecisionResult result = service.generateDecision(
 *         symbol, timeframe, candles, bid, ask, current, volatility, volume, behavior);
 *
 * if (!result.isSuccess()) {
 *     log.info("Decision rejected: {}", result.getRejectionReason());
 *     return;
 * }
 *
 * if (!result.hasActionableSignal()) {
 *     log.info("No actionable signal (HOLD)");
 *     return;
 * }
 *
 * StrategySignal signal = result.getSignal();
 * // Pass to RiskManagementSystem
 * </pre>
 */
@Getter
@Slf4j
public class StrategyDecisionService {
    private final StrategySelectionService selectionService;
    private final StrategyRegistry strategyRegistry;

    public StrategyDecisionService() {
        this.selectionService = StrategySelectionService.getInstance();
        this.strategyRegistry = StrategyRegistry.getInstance();
    }

    /**
     * Generate a strategy decision for given market context.
     *
     * @param symbol        Trading symbol (e.g., BTC/USD)
     * @param timeframe     Timeframe (e.g., 1h, 4h)
     * @param candles       List of CandleData (oldest to newest)
     * @param bid           Current bid price
     * @param ask           Current ask price
     * @param currentPrice  Current market price
     * @param volatility    Recent volatility measure
     * @param averageVolume Average trading volume
     * @param behavior      Detected market behavior
     * @param tradePair     TradePair object (optional, for additional context)
     * @return StrategyDecisionResult with signal or rejection reason
     */
    public StrategyDecisionResult generateDecision(
            String symbol,
            String timeframe,
            List<CandleData> candles,
            double bid,
            double ask,
            double currentPrice,
            double volatility,
            double averageVolume,
            MarketBehavior behavior,
            TradePair tradePair) {

        List<String> warnings = new ArrayList<>();

        try {
            Timeframe parsedTimeframe = parseTimeframe(timeframe);
            TradePair resolvedTradePair = resolveTradePair(symbol, tradePair);
            MarketSnapshot market = normalizeMarketSnapshot(candles, bid, ask, currentPrice, volatility, averageVolume);

            // ===== Validation: Strategy Assignment =====
            StrategyAssignment assignment = selectionService.getCurrentAssignment(symbol, parsedTimeframe);
            if (assignment == null) {
                assignment = bestSymbolAssignment(symbol);
                if (assignment != null) {
                    parsedTimeframe = assignment.getTimeframe();
                    warnings.add("Using best evaluated assignment for " + symbol + " on "
                            + parsedTimeframe.getCode());
                }
            }

            if (assignment == null) {
                assignment = isCompatibilityFallbackEnabled()
                        ? autoAssignFallbackStrategy(symbol, parsedTimeframe, candles, resolvedTradePair)
                        : null;
                if (assignment == null) {
                    String reason = String.format(
                            "No evaluated strategy assignment found for %s %s",
                            symbol, timeframe);
                    log.debug(reason);
                    return StrategyDecisionResult.rejected(reason, warnings);
                }
                warnings.add("No evaluated assignment was found; compatibility fallback selected "
                        + assignment.getStrategyId());
            }

            if (!assignment.isValid()) {
                String reason = String.format(
                        "Assignment for %s %s is invalid",
                        symbol, timeframe);
                log.debug(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            if (assignment.isDisabled()) {
                String reason = String.format(
                        "Strategy disabled for %s %s: %s",
                        symbol, timeframe, assignment.getDisableReason());
                log.debug(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Validation: Strategy Registration =====
            String strategyId = assignment.getStrategyId();
            TradingStrategy strategy = strategyRegistry.getStrategy(strategyId);

            if (strategy == null) {
                String reason = String.format(
                        "Assigned strategy '%s' not found in registry for %s %s",
                        strategyId, symbol, timeframe);
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            if (resolvedTradePair != null && resolvedTradePair.getTradingSession() != null
                    && !resolvedTradePair.isTradableNow()) {
                String reason = "Trading session is not open: " + resolvedTradePair.getTradingSessionStatus();
                log.debug("Rejecting strategy decision for {} {}: {}", symbol, timeframe, reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Validation: Market Data =====
            if (candles == null || candles.isEmpty()) {
                warnings.add("No historical candles available");
            }

            if (market.bid() >= market.ask()) {
                String reason = String.format(
                        "Invalid bid/ask spread: bid=%.8f, ask=%.8f",
                        market.bid(), market.ask());
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            if (market.bid() <= 0 || market.ask() <= 0 || market.currentPrice() <= 0) {
                String reason = "Invalid price values (must be > 0)";
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Build StrategyContext =====
            StrategyContext context = StrategyContext.builder()
                    .symbol(resolvedTradePair)
                    .timeframe(parsedTimeframe)
                    .candles(candles != null ? candles : List.of())
                    .currentPrice(market.currentPrice())
                    .bid(market.bid())
                    .ask(market.ask())
                    .marketBehavior(behavior == null ? MarketBehavior.RANGING : behavior)
                    .volatility(market.volatility())
                    .averageVolume(market.averageVolume())
                    .tradingSession(resolvedTradePair == null ? null : resolvedTradePair.getTradingSession())
                    .tradingSessionStatus(
                            resolvedTradePair == null ? null : resolvedTradePair.getTradingSessionStatus())
                    .timestamp(Instant.now())
                    .barsAvailable(candles != null ? candles.size() : 0)
                    .build();

            // ===== Generate Signal =====
            log.debug("Generating signal: strategy={}, symbol={}, timeframe={}",
                    strategyId, symbol, timeframe);

            StrategySignal signal = strategy.generateSignal(context);

            if (signal == null) {
                String reason = String.format(
                        "Strategy '%s' returned null signal for %s %s",
                        strategyId, symbol, timeframe);
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Return Success =====
            log.info("Signal generated: {} {} {} (confidence={}%)",
                    signal.getSide(), symbol, timeframe,
                    String.format("%.2f", signal.getConfidence() * 100));

            return StrategyDecisionResult.success(strategyId, assignment, signal, warnings);

        } catch (Exception e) {
            String reason = "Exception during strategy decision: " + e.getMessage();
            log.error(reason, e);
            return StrategyDecisionResult.rejected(reason, warnings);
        }
    }

    private StrategyAssignment bestSymbolAssignment(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return org.investpro.persistence.repository.StrategyAssignmentRepository.getInstance()
                .getForSymbolAllTimeframes(symbol)
                .stream()
                .filter(StrategyAssignment::isValid)
                .filter(assignment -> !assignment.isExpired())
                .filter(assignment -> !assignment.isDisabled())
                .max(java.util.Comparator.comparingDouble(StrategyAssignment::getScoreAtAssignment))
                .orElse(null);
    }

    private TradePair resolveTradePair(String symbol, TradePair tradePair) {
        if (tradePair != null) {
            return tradePair;
        }

        String value = symbol == null ? "" : symbol.trim();
        if (value.isBlank()) {
            return null;
        }

        String[] parts = value.contains("/")
                ? value.split("/")
                : value.split("-");
        if (parts.length != 2) {
            return null;
        }

        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to build TradePair from symbol '{}': {}", symbol, exception.getMessage());
            return null;
        }
    }

    private StrategyAssignment autoAssignFallbackStrategy(
            String symbol,
            Timeframe timeframe,
            List<CandleData> candles,
            TradePair tradePair) {
        int bars = candles == null ? 0 : candles.size();
        AssetClass assetClass = tradePair == null ? null : tradePair.getAssetClass();
        ContractType contractType = tradePair == null ? null : tradePair.getContractType();

        TradingStrategy selected = strategyRegistry.getEnabledStrategies()
                .stream()
                .filter(strategy -> strategy.supportsTimeframe(timeframe))
                .filter(strategy -> assetClass == null || strategy.supportsAssetClass(assetClass))
                .filter(strategy -> contractType == null || strategy.supportsContractType(contractType))
                .filter(strategy -> bars >= strategy.requiredWarmupBars())
                .min(java.util.Comparator.comparingInt(TradingStrategy::requiredWarmupBars))
                .orElseGet(() -> strategyRegistry.getEnabledStrategies()
                        .stream()
                        .filter(strategy -> strategy.supportsTimeframe(timeframe))
                        .filter(strategy -> assetClass == null || strategy.supportsAssetClass(assetClass))
                        .filter(strategy -> contractType == null || strategy.supportsContractType(contractType))
                        .min(java.util.Comparator.comparingInt(TradingStrategy::requiredWarmupBars))
                        .orElse(null));

        if (selected == null) {
            return null;
        }

        return selectionService.manuallyAssign(
                symbol,
                timeframe,
                selected.getId().toString(),
                false,
                "Auto-selected first compatible strategy for live signal generation");
    }

    private boolean isCompatibilityFallbackEnabled() {
        return AppConfig.getBoolean("investpro.strategy.allowCompatibilityFallback", true);
    }

    private Timeframe parseTimeframe(String timeframe) {
        String value = timeframe == null ? "" : timeframe.trim();
        if (value.isBlank()) {
            return Timeframe.H1;
        }

        for (Timeframe candidate : Timeframe.values()) {
            if (candidate.name().equalsIgnoreCase(value) || candidate.getCode().equalsIgnoreCase(value)) {
                return candidate;
            }
        }

        log.warn("Unknown timeframe '{}', defaulting to H1", timeframe);
        return Timeframe.H1;
    }

    private MarketSnapshot normalizeMarketSnapshot(
            List<CandleData> candles,
            double bid,
            double ask,
            double currentPrice,
            double volatility,
            double averageVolume) {
        CandleData latest = candles == null || candles.isEmpty() ? null : candles.get(candles.size() - 1);
        double resolvedCurrent = currentPrice > 0.0
                ? currentPrice
                : latest == null ? 0.0 : latest.closePrice();
        double resolvedBid = bid;
        double resolvedAsk = ask;

        if (resolvedCurrent > 0.0 && (resolvedBid <= 0.0 || resolvedAsk <= 0.0 || resolvedBid >= resolvedAsk)) {
            double syntheticSpread = Math.max(resolvedCurrent * 0.0001, 0.00000001);
            resolvedBid = resolvedCurrent - syntheticSpread / 2.0;
            resolvedAsk = resolvedCurrent + syntheticSpread / 2.0;
        }

        double resolvedVolume = averageVolume > 0.0
                ? averageVolume
                : latest == null ? 0.0 : latest.volume();

        return new MarketSnapshot(
                resolvedBid,
                resolvedAsk,
                resolvedCurrent,
                Math.max(0.0, volatility),
                Math.max(0.0, resolvedVolume));
    }

    private record MarketSnapshot(
            double bid,
            double ask,
            double currentPrice,
            double volatility,
            double averageVolume) {
    }

    /**
     * Simplified overload: Generate decision with minimal parameters.
     * MarketBehavior and tradePair are assumed null.
     */
    public StrategyDecisionResult generateDecision(
            String symbol,
            String timeframe,
            List<CandleData> candles,
            double bid,
            double ask,
            double currentPrice,
            double volatility,
            double averageVolume) {

        return generateDecision(
                symbol, timeframe, candles, bid, ask, currentPrice,
                volatility, averageVolume, null, null);
    }
}
