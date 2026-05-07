package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Computed market features for strategy decision-making.
 *
 * Contains the latest technical indicators, trend measurements, and market
 * regime
 * for a given candle series. Produced by FeaturePipeline and consumed by
 * UnifiedStrategy strategy logic methods.
 */
@Getter
@Builder
public class FeatureRow {
    // =========================================================================
    // Price & Volume
    // =========================================================================
    private final double close;
    private final double previousClose;

    // =========================================================================
    // Trend Indicators
    // =========================================================================
    private final double emaFast;
    private final double emaSlow;
    private final double trendStrength;

    // =========================================================================
    // Volatility Indicators
    // =========================================================================
    private final double atr;
    private final double atrPct;

    // =========================================================================
    // Momentum Indicators
    // =========================================================================
    private final double rsi;
    private final double momentum;

    // =========================================================================
    // Oscillators
    // =========================================================================
    private final double macdLine;
    private final double macdSignal;

    // =========================================================================
    // Bollinger Bands
    // =========================================================================
    private final double upperBand;
    private final double lowerBand;
    private final double bandPosition;

    // =========================================================================
    // Breakout Levels
    // =========================================================================
    private final double breakoutHigh;
    private final double breakoutLow;

    // =========================================================================
    // Volume & Pullback
    // =========================================================================
    private final double volume;
    private final double volumeRatio;
    private final double pullbackGap;

    // =========================================================================
    // Market Regime
    // =========================================================================
    private final String regime; // high_volatility, trending, ranging

    // =========================================================================
    // Convenience Methods
    // =========================================================================

    /**
     * Check if price is above fast EMA (bullish signal).
     */
    public boolean trendUp() {
        return close > emaFast;
    }

    /**
     * Check if price is below fast EMA (bearish signal).
     */
    public boolean trendDown() {
        return close < emaFast;
    }

    /**
     * Check if fast EMA is above slow EMA (bullish alignment).
     */
    public boolean emasAlignedBullish() {
        return emaFast > emaSlow;
    }

    /**
     * Check if fast EMA is below slow EMA (bearish alignment).
     */
    public boolean emasAlignedBearish() {
        return emaFast < emaSlow;
    }

    /**
     * Check if in high volatility regime.
     */
    public boolean isHighVolatility() {
        return "high_volatility".equalsIgnoreCase(regime);
    }

    /**
     * Check if in trending regime.
     */
    public boolean isTrending() {
        return "trending".equalsIgnoreCase(regime);
    }

    /**
     * Check if in ranging regime.
     */
    public boolean isRanging() {
        return "ranging".equalsIgnoreCase(regime);
    }

    /**
     * Get price change percent from previous close.
     */
    public double priceChangePercent() {
        if (previousClose <= 0)
            return 0;
        return ((close - previousClose) / previousClose) * 100;
    }

    @Override
    public @NotNull String toString() {
        return "FeatureRow{" +
                "close=" + close +
                ", emaFast=" + emaFast +
                ", emaSlow=" + emaSlow +
                ", rsi=" + rsi +
                ", atr=" + atr +
                ", atrPct=" + String.format("%.2f%%", atrPct * 100) +
                ", regime='" + regime + '\'' +
                ", bandPosition=" + String.format("%.2f", bandPosition) +
                '}';
    }
}
