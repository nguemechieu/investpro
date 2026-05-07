package org.investpro.enums;

import lombok.Getter;

/**
 * Classifies the current market regime/behavior.
 *
 * Used by strategies, risk management, signal filtering, and UI labels
 * to understand whether the market is trending, ranging, volatile, breaking out,
 * or reversing.
 */
@Getter
public enum MarketBehavior {

    TRENDING_UP(
            "Uptrend",
            "Prices are rising with higher highs and higher lows.",
            TradingBias.BUY_BIAS,
            true,
            false,
            false,
            1.00
    ),

    TRENDING_DOWN(
            "Downtrend",
            "Prices are falling with lower highs and lower lows.",
            TradingBias.SELL_BIAS,
            true,
            false,
            false,
            1.00
    ),

    RANGING(
            "Range-Bound",
            "Price is oscillating between support and resistance.",
            TradingBias.NEUTRAL,
            false,
            true,
            false,
            0.85
    ),

    HIGH_VOLATILITY(
            "High Volatility",
            "Market has large price swings and elevated uncertainty.",
            TradingBias.CAUTION,
            false,
            false,
            true,
            0.60
    ),

    LOW_VOLATILITY(
            "Low Volatility",
            "Market has small price swings, compression, or consolidation.",
            TradingBias.PATIENCE,
            false,
            true,
            false,
            0.75
    ),

    BREAKOUT(
            "Breakout",
            "Price is breaking above or below key levels.",
            TradingBias.OPPORTUNITY,
            true,
            false,
            true,
            0.90
    ),

    REVERSAL(
            "Reversal",
            "Possible trend-change signals are emerging.",
            TradingBias.CAUTION,
            false,
            false,
            true,
            0.55
    ),

    UNKNOWN(
            "Unknown",
            "Market behavior could not be confidently classified.",
            TradingBias.NEUTRAL,
            false,
            false,
            false,
            0.50
    );

    private final String displayName;
    private final String description;
    private final TradingBias tradingBias;

    /**
     * True when trend-following strategies may be useful.
     */
    private final boolean trendFriendly;

    /**
     * True when mean-reversion strategies may be useful.
     */
    private final boolean meanReversionFriendly;

    /**
     * True when breakout/volatility strategies may be useful.
     */
    private final boolean volatilityFriendly;

    /**
     * Risk multiplier used by the risk engine.
     * <p>
     * 1.00 = normal size
     * 0.75 = reduce size to 75%
     * 0.50 = reduce size to 50%
     */
    private final double riskMultiplier;

    MarketBehavior(
            String displayName,
            String description,
            TradingBias tradingBias,
            boolean trendFriendly,
            boolean meanReversionFriendly,
            boolean volatilityFriendly,
            double riskMultiplier
    ) {
        this.displayName = displayName;
        this.description = description;
        this.tradingBias = tradingBias;
        this.trendFriendly = trendFriendly;
        this.meanReversionFriendly = meanReversionFriendly;
        this.volatilityFriendly = volatilityFriendly;
        this.riskMultiplier = riskMultiplier;
    }

    public boolean isBullish() {
        return this == TRENDING_UP || tradingBias == TradingBias.BUY_BIAS;
    }

    public boolean isBearish() {
        return this == TRENDING_DOWN || tradingBias == TradingBias.SELL_BIAS;
    }

    public boolean isTrending() {
        return this == TRENDING_UP || this == TRENDING_DOWN || this == BREAKOUT;
    }

    public boolean isRanging() {
        return this == RANGING || this == LOW_VOLATILITY;
    }

    public boolean isVolatile() {
        return this == HIGH_VOLATILITY || this == BREAKOUT || this == REVERSAL;
    }

    public boolean requiresCaution() {
        return tradingBias == TradingBias.CAUTION || this == UNKNOWN;
    }

    public boolean isStrategyFriendlyFor(StrategyCategory category) {
        if (category == null) {
            return false;
        }

        return switch (category) {
            case TREND_FOLLOWING -> trendFriendly;
            case MEAN_REVERSION -> meanReversionFriendly;
            case BREAKOUT -> volatilityFriendly || this == BREAKOUT;
            default -> false;
        };
    }

    public double adjustPositionSize(double requestedSize) {
        if (requestedSize <= 0) {
            return 0.0;
        }

        return requestedSize * riskMultiplier;
    }

    public String getRiskLabel() {
        if (riskMultiplier >= 1.0) {
            return "Normal";
        }

        if (riskMultiplier >= 0.85) {
            return "Slightly Reduced";
        }

        if (riskMultiplier >= 0.60) {
            return "Reduced";
        }

        return "Defensive";
    }

    public enum TradingBias {
        BUY_BIAS,
        SELL_BIAS,
        NEUTRAL,
        CAUTION,
        PATIENCE,
        OPPORTUNITY
    }
}