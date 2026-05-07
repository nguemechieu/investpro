package org.investpro.strategy;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.utils.Side;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Normalized strategy signal returned by every TradingStrategy.
 *
 * This is the PRIMARY signal class that should be used throughout the system.
 * StrategySignal provides complete trade context beyond just side direction:
 * - Entry/exit prices and stop loss/take profit levels
 * - Risk/reward ratios
 * - Confidence and expected value metrics
 * - Win/loss probabilities
 *
 * Side = direction only (use strategy signal instead).
 * StrategySignal = full signal context with all trading parameters.
 */
@Value
@Builder(toBuilder = true)
public class StrategySignal {

    // =========================================================================
    // Identity
    // =========================================================================

    /**
     * Trading symbol, for example BTC/USD, EUR/USD, AAPL, XLM/USDC.
     */
    String symbol;

    /**
     * Timeframe, for example 1m, 5m, 15m, 1h, 4h, 1d.
     */
    String timeframe;

    /**
     * Strategy identifier that generated this signal.
     */
    String strategyId;

    /**
     * Optional human-readable strategy name.
     */
    String strategyName;

    // =========================================================================
    // Signal decision
    // =========================================================================

    /**
     * BUY, SELL, or HOLD.
     */
    Side side;

    /**
     * Confidence between 0.0 and 1.0.
     */
    double confidence;

    /**
     * Entry price suggested by the strategy.
     */
    double entryPrice;

    /**
     * Suggested stop loss price.
     */
    double stopLossPrice;

    /**
     * Suggested take profit price.
     */
    double takeProfitPrice;

    /**
     * Risk/reward ratio.
     * <p>
     * Example:
     * risk = entry - stop
     * reward = takeProfit - entry
     * riskRewardRatio = reward / risk
     */
    double riskRewardRatio;

    /**
     * Expected value estimate.
     */
    double expectedValue;

    /**
     * Strategy-estimated win probability.
     */
    double winProbability;

    /**
     * Strategy-estimated loss probability.
     */
    double lossProbability;

    // =========================================================================
    // Context
    // =========================================================================

    /**
     * Current market behavior/regime.
     */
    MarketBehavior marketBehavior;

    /**
     * Trading session status for the symbol when this signal was created.
     */
    TradingSessionStatus sessionStatus;

    /**
     * Human-readable notes about the symbol's trading session.
     */
    String sessionNotes;

    /**
     * Whether this signal is valid until a specific time.
     */
    LocalDateTime validUntil;

    /**
     * Signal creation time.
     */
    LocalDateTime createdAt;

    /**
     * Extra metadata for strategy-specific values.
     */
    @Singular("metadata")
    Map<String, Object> metadata;

    /**
     * Reasons supporting the signal.
     */
    @Singular("reason")
    List<String> reasons;

    /**
     * Warnings or concerns about the signal.
     */
    @Singular("warning")
    List<String> warnings;

    /**
     * Tags for classification.
     *
     * Examples:
     * breakout, trend, reversal, scalping, high-volatility.
     */
    @Singular("tag")
    List<String> tags;

    // =========================================================================
    // Convenience methods
    // =========================================================================

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isSell() {
        return side == Side.SELL;
    }

    public boolean isHold() {
        return side == Side.HOLD;
    }

    public boolean isActionable() {
        return side != null && side != Side.HOLD && confidence > 0.0;
    }

    public boolean hasStopLoss() {
        return stopLossPrice > 0.0;
    }

    public boolean hasTakeProfit() {
        return takeProfitPrice > 0.0;
    }

    public boolean hasValidEntryPrice() {
        return entryPrice > 0.0;
    }

    public boolean hasValidRiskReward() {
        return riskRewardRatio > 0.0 && Double.isFinite(riskRewardRatio);
    }

    public boolean isExpired() {
        return validUntil != null && LocalDateTime.now().isAfter(validUntil);
    }

    public double getRiskDistance() {
        if (!hasValidEntryPrice() || !hasStopLoss()) {
            return 0.0;
        }

        return Math.abs(entryPrice - stopLossPrice);
    }

    public double getRewardDistance() {
        if (!hasValidEntryPrice() || !hasTakeProfit()) {
            return 0.0;
        }

        return Math.abs(takeProfitPrice - entryPrice);
    }

    public double calculateRiskRewardRatio() {
        double risk = getRiskDistance();
        double reward = getRewardDistance();

        if (risk <= 0.0) {
            return 0.0;
        }

        return reward / risk;
    }

    public StrategySignal withSide(Side newSide) {
        return this.toBuilder()
                .side(newSide)
                .build();
    }

    public StrategySignal withConfidence(double newConfidence) {
        return this.toBuilder()
                .confidence(clamp(newConfidence, 0.0, 1.0))
                .build();
    }

    public StrategySignal withWarning(String warning) {
        return this.toBuilder()
                .warning(warning)
                .build();
    }

    public StrategySignal withReason(String reason) {
        return this.toBuilder()
                .reason(reason)
                .build();
    }

    public StrategySignal normalized() {
        double safeConfidence = clamp(confidence, 0.0, 1.0);

        double safeRiskReward = riskRewardRatio;
        if (safeRiskReward <= 0.0 && hasValidEntryPrice() && hasStopLoss() && hasTakeProfit()) {
            safeRiskReward = calculateRiskRewardRatio();
        }

        LocalDateTime safeCreatedAt = createdAt == null ? LocalDateTime.now() : createdAt;

        return this.toBuilder()
                .side(side == null ? Side.HOLD : side)
                .confidence(safeConfidence)
                .riskRewardRatio(safeRiskReward)
                .createdAt(safeCreatedAt)
                .build();
    }

    public static StrategySignal hold(String symbol, String timeframe, String strategyId, String reason) {
        return StrategySignal.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyId)
                .side(Side.HOLD)
                .confidence(0.0)
                .reason(reason == null || reason.isBlank() ? "No trade setup." : reason)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static StrategySignal buy(
            String symbol,
            String timeframe,
            String strategyId,
            double confidence,
            double entryPrice,
            double stopLossPrice,
            double takeProfitPrice,
            String reason) {
        return StrategySignal.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyId)
                .side(Side.BUY)
                .confidence(clamp(confidence, 0.0, 1.0))
                .entryPrice(entryPrice)
                .stopLossPrice(stopLossPrice)
                .takeProfitPrice(takeProfitPrice)
                .riskRewardRatio(calculateRatio(entryPrice, stopLossPrice, takeProfitPrice))
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static StrategySignal sell(
            String symbol,
            String timeframe,
            String strategyId,
            double confidence,
            double entryPrice,
            double stopLossPrice,
            double takeProfitPrice,
            String reason) {
        return StrategySignal.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyId)
                .side(Side.SELL)
                .confidence(clamp(confidence, 0.0, 1.0))
                .entryPrice(entryPrice)
                .stopLossPrice(stopLossPrice)
                .takeProfitPrice(takeProfitPrice)
                .riskRewardRatio(calculateRatio(entryPrice, stopLossPrice, takeProfitPrice))
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static double calculateRatio(double entryPrice, double stopLossPrice, double takeProfitPrice) {
        if (entryPrice <= 0.0 || stopLossPrice <= 0.0 || takeProfitPrice <= 0.0) {
            return 0.0;
        }

        double risk = Math.abs(entryPrice - stopLossPrice);
        double reward = Math.abs(takeProfitPrice - entryPrice);

        if (risk <= 0.0) {
            return 0.0;
        }

        return reward / risk;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }

    public String getReason() {
        return reasons.stream().reduce("", (s1, s2) -> s1 + s2);
    }
}
