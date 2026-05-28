package org.investpro.decision;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Market regime classification based on price action, volatility, and trend
 * characteristics.
 * Used to select appropriate strategies and adjust risk parameters.
 */
@Getter
@ToString
public enum MarketRegime {
    /**
     * Strong uptrend with minimal pullbacks. Price making higher highs and higher
     * lows.
     * Higher volatility (ATR > median). Volume confirmation present.
     * Best for: trend-following, momentum strategies.
     */
    STRONG_UPTREND("Strong uptrend with sustained momentum", true, false),

    /**
     * Strong downtrend with consistent lower lows. Selling pressure dominant.
     * Best for: short strategies, bearish signals.
     */
    STRONG_DOWNTREND("Strong downtrend with selling pressure", false, false),

    /**
     * Weak uptrend. Price making higher lows but struggle with resistance.
     * Slower momentum, fewer confirmations.
     */
    WEAK_UPTREND("Weak uptrend with limited momentum", true, true),

    /**
     * Weak downtrend. Slower selling, potential reversal signals.
     */
    WEAK_DOWNTREND("Weak downtrend with limited selling", false, true),

    /**
     * Sideways/range-bound market. Price oscillates between clear
     * support/resistance.
     * Lower volatility (ATR < median). Mean-reversion strategies suitable.
     * Best for: oscillators, range-bound strategies.
     */
    RANGE_BOUND("Sideways movement within defined range", null, true),

    /**
     * Highly volatile market with rapid directional changes.
     * ATR extremely elevated. Widening Bollinger Bands. Uncertain direction.
     * Higher risk of whipsaws.
     */
    HIGH_VOLATILITY("High volatility with uncertain direction", null, false),

    /**
     * Low volatility market. ATR compressed. Markets approaching breakout
     * potential.
     * or exhausted after recent move. Bollinger Bands compressed.
     * Best for: breakout strategies, reversal signals.
     */
    LOW_VOLATILITY("Low volatility with range compression", null, true),

    /**
     * Market transitioning between regimes. Unclear structure.
     * High uncertainty. Breakout direction not yet determined.
     */
    TRANSITIONAL("Transitional market between regimes", null, true),

    /**
     * Insufficient data to classify market regime.
     * Not enough candles for reliable analysis.
     */
    UNKNOWN("Unknown or insufficient data", null, true);

    public final String description;
    public final Boolean bullish; // null = neutral/range-bound, true = bullish, false = bearish
    public final boolean isLowRiskRegime; // true if regime is generally lower risk (weak trends, range, low vol,
                                          // transitional)

    MarketRegime(String description, Boolean bullish, boolean isLowRiskRegime) {
        this.description = description;
        this.bullish = bullish;
        this.isLowRiskRegime = isLowRiskRegime;
    }

    public boolean hasDefinedDirection() {
        return bullish != null;
    }

    public boolean isTrendingRegime() {
        return this == STRONG_UPTREND || this == STRONG_DOWNTREND ||
                this == WEAK_UPTREND || this == WEAK_DOWNTREND;
    }

    public boolean isRangeRegime() {
        return this == RANGE_BOUND;
    }

    public boolean isVolatilityRegime() {
        return this == HIGH_VOLATILITY || this == LOW_VOLATILITY;
    }


}
