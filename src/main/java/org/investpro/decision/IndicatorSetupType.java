package org.investpro.decision;

/**
 * Types of indicator composite setups used when no strategy reaches the minimum
 * fitness score.
 * Each type represents a different trading philosophy or indicator combination.
 */
public enum IndicatorSetupType {
    /**
     * Momentum-based composite: RSI + MACD + Volume
     * Suitable for: trending markets, breakouts, strong directional moves.
     * Entry: RSI > 50 (buy) / < 50 (sell), MACD bullish crossover, volume
     * confirmation.
     */
    MOMENTUM("Momentum-based: RSI + MACD + Volume"),

    /**
     * Mean-reversion composite: Bollinger Bands + Stochastic + RSI
     * Suitable for: range-bound markets, overbought/oversold conditions.
     * Entry: Price touches upper/lower band, Stochastic extreme, RSI divergence.
     */
    MEAN_REVERSION("Mean-reversion: Bollinger Bands + Stochastic + RSI"),

    /**
     * Trend-following composite: Moving averages + ADX + ATR
     * Suitable for: strong trending markets, directional conviction.
     * Entry: Price above/below MA cluster, ADX > 25, ATR widening.
     */
    TREND_FOLLOWING("Trend-following: MA + ADX + ATR"),

    /**
     * Volatility breakout composite: ATR + Bollinger Bands + Range
     * Suitable for: low volatility environments approaching breakouts.
     * Entry: ATR expanding, bands widening, price breaks range.
     */
    VOLATILITY_BREAKOUT("Volatility breakout: ATR + Bollinger Bands + Range"),

    /**
     * Divergence composite: Price action + RSI/MACD divergence + Volume
     * Suitable for: reversal setups, trend exhaustion.
     * Entry: Price makes new high/low but momentum doesn't confirm, divergence
     * appears.
     */
    DIVERGENCE("Divergence-based: Price action + RSI/MACD divergence"),

    /**
     * Support/resistance composite: Level proximity + Volume + Candlestick patterns
     * Suitable for: key level bounces, breakout confirmations.
     * Entry: Price near key level, candlestick pattern confirmation, volume
     * increase.
     */
    LEVEL_BREAKOUT("Level breakout: Support/Resistance + Volume + Pattern"),

    /**
     * No suitable indicator composite available.
     */
    NONE("No suitable indicator setup");

    public final String description;

    IndicatorSetupType(String description) {
        this.description = description;
    }
}
