package com.investpro.examples.strategy;

import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.data.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Simple Exponential Moving Average (EMA) Crossover User Strategy.
 *
 * This is a basic example strategy that demonstrates:
 * - How to implement the UserStrategy interface
 * - How to access market data from StrategyContext
 * - How to generate trading signals
 * - How to use StrategySignal.Builder for customization
 *
 * Strategy Logic:
 * - Calculate fast EMA (12 periods) and slow EMA (26 periods)
 * - BUY signal when fast EMA crosses above slow EMA
 * - SELL signal when fast EMA crosses below slow EMA
 * - HOLD if not enough data or during consolidation
 *
 * Safety:
 * - This strategy is READ-ONLY and never modifies platform state
 * - All API calls are safe for user code
 * - No access to exchange, order execution, or credentials
 */
public class SimpleEmaUserStrategy implements UserStrategy {

    private static final int FAST_PERIOD = 12;
    private static final int SLOW_PERIOD = 26;

    @Override
    public @NotNull String getId() {
        return "simple-ema-crossover";
    }

    @Override
    public @NotNull String getName() {
        return "Simple EMA Crossover";
    }

    @Override
    public @NotNull String getDescription() {
        return "Exponential Moving Average crossover strategy. " +
                "BUY when fast EMA (12) crosses above slow EMA (26), " +
                "SELL when fast EMA crosses below slow EMA.";
    }

    @Override
    public int requiredWarmupBars() {
        // Need at least SLOW_PERIOD bars to calculate both EMAs
        return SLOW_PERIOD + 5; // +5 for safety margin
    }

    @Override
    public @NotNull StrategySignal generateSignal(@NotNull StrategyContext context) {
        // Extract candle data from context
        List<CandleData> candles = context.getCandles();

        // Safety: check if we have enough data
        if (candles == null || candles.size() < requiredWarmupBars()) {
            return StrategySignal.hold(
                    context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                    context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                    getId(),
                    "Insufficient data for signal generation");
        }

        try {
            // Calculate EMAs
            double fastEma = calculateEma(candles, FAST_PERIOD);
            double slowEma = calculateEma(candles, SLOW_PERIOD);

            // Get previous candle for crossover detection
            double prevFastEma = calculateEma(sublist(candles, 0, candles.size() - 1), FAST_PERIOD);
            double prevSlowEma = calculateEma(sublist(candles, 0, candles.size() - 1), SLOW_PERIOD);

            // Safety check for NaN values
            if (Double.isNaN(fastEma) || Double.isNaN(slowEma) ||
                    Double.isNaN(prevFastEma) || Double.isNaN(prevSlowEma)) {
                return StrategySignal.hold(
                        context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                        context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                        getId(),
                        "NaN in EMA calculation");
            }

            // Detect crossover
            boolean fastCrossedAbove = (prevFastEma <= prevSlowEma) && (fastEma > slowEma);
            boolean fastCrossedBelow = (prevFastEma >= prevSlowEma) && (fastEma < slowEma);

            if (fastCrossedAbove) {
                // BUY signal: fast EMA crossed above slow EMA
                return StrategySignal.builder()
                        .symbol(context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN")
                        .timeframe(context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN")
                        .side(StrategySignal.Side.BUY)
                        .confidence(0.65) // 65% confidence
                        .amount(0.5) // Trade 50% of available funds
                        .reason("Fast EMA (" + String.format("%.2f", fastEma) +
                                ") crossed above Slow EMA (" + String.format("%.2f", slowEma) + ")")
                        .generatingStrategy(getId())
                        .build();
            }

            if (fastCrossedBelow) {
                // SELL signal: fast EMA crossed below slow EMA
                return StrategySignal.builder()
                        .symbol(context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN")
                        .timeframe(context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN")
                        .side(StrategySignal.Side.SELL)
                        .confidence(0.60) // 60% confidence
                        .amount(1.0) // Exit full position
                        .reason("Fast EMA (" + String.format("%.2f", fastEma) +
                                ") crossed below Slow EMA (" + String.format("%.2f", slowEma) + ")")
                        .generatingStrategy(getId())
                        .build();
            }

            // No signal: EMAs are aligned or not crossed
            return StrategySignal.hold(
                    context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                    context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                    getId(),
                    "No crossover detected");

        } catch (Exception e) {
            // Safety: catch any calculation errors and return HOLD
            return StrategySignal.hold(
                    context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                    context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                    getId(),
                    "Error in signal generation: " + e.getMessage());
        }
    }

    /**
     * Calculate Exponential Moving Average for the given period.
     * Uses the standard EMA formula: EMA = (Close - EMA_prev) * multiplier +
     * EMA_prev
     */
    private double calculateEma(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return Double.NaN;
        }

        double multiplier = 2.0 / (period + 1.0);
        double sma = calculateSma(candles, period);

        if (Double.isNaN(sma)) {
            return Double.NaN;
        }

        double ema = sma;

        // Start from period onwards for EMA calculation
        for (int i = period; i < candles.size(); i++) {
            double close = candles.get(i).getClose();
            ema = (close - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * Calculate Simple Moving Average for the given period.
     */
    private double calculateSma(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return Double.NaN;
        }

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }

        return sum / period;
    }

    /**
     * Create a sublist of candles from index 0 to end (exclusive).
     */
    private List<CandleData> sublist(List<CandleData> candles, int fromIndex, int toIndex) {
        if (fromIndex < 0)
            fromIndex = 0;
        if (toIndex > candles.size())
            toIndex = candles.size();
        if (fromIndex >= toIndex)
            return List.of();
        return candles.subList(fromIndex, toIndex);
    }
}
