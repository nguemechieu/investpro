package org.investpro.strategy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.models.trading.TradePair;

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
            // ===== Validation: Strategy Assignment =====
            StrategyAssignment assignment = selectionService.getCurrentAssignment(symbol, timeframe);

            if (assignment == null) {
                String reason = String.format(
                        "No strategy assignment found for %s %s",
                        symbol, timeframe);
                log.debug(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
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

            if (tradePair != null && tradePair.getTradingSession() != null && !tradePair.isTradableNow()) {
                String reason = "Trading session is not open: " + tradePair.getTradingSessionStatus();
                log.debug("Rejecting strategy decision for {} {}: {}", symbol, timeframe, reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Validation: Market Data =====
            if (candles == null || candles.isEmpty()) {
                warnings.add("No historical candles available");
            }

            if (bid >= ask) {
                String reason = String.format(
                        "Invalid bid/ask spread: bid=%.8f, ask=%.8f",
                        bid, ask);
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            if (bid <= 0 || ask <= 0 || currentPrice <= 0) {
                String reason = "Invalid price values (must be > 0)";
                log.warn(reason);
                return StrategyDecisionResult.rejected(reason, warnings);
            }

            // ===== Build StrategyContext =====
            StrategyContext context = StrategyContext.builder()
                    .symbol(tradePair)
                    .candles(candles != null ? candles : List.of())
                    .currentPrice(currentPrice)
                    .bid(bid)
                    .ask(ask)
                    .marketBehavior(behavior)
                    .volatility(volatility)
                    .averageVolume(averageVolume)
                    .tradingSession(tradePair.getTradingSession())
                    .tradingSessionStatus(tradePair.getTradingSessionStatus())
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
            log.info("Signal generated: {} {} {} (confidence={:.2f}%)",
                    signal.getSide(), symbol, timeframe,
                    signal.getConfidence() * 100);

            return StrategyDecisionResult.success(strategyId, assignment, signal, warnings);

        } catch (Exception e) {
            String reason = "Exception during strategy decision: " + e.getMessage();
            log.error(reason, e);
            return StrategyDecisionResult.rejected(reason, warnings);
        }
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
