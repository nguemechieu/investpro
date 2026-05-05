package org.investpro.risk;

import lombok.Getter;

/**
 * Classifies current market conditions and behavior.
 * Affects position sizing, execution urgency, and risk adjustments.
 */
@Getter
public enum MarketBehavior {
    TRENDING_UP("Trending Up", "Strong upward momentum", "BUY_BIAS", 0.9),
    TRENDING_DOWN("Trending Down", "Strong downward momentum", "SELL_BIAS", 0.9),
    RANGING("Ranging", "Price oscillating in a band", "NEUTRAL", 1.0),
    HIGH_VOLATILITY("High Volatility", "Elevated price swings", "CAUTION", 0.7),
    LOW_VOLATILITY("Low Volatility", "Calm market conditions", "OPPORTUNITY", 1.1),
    BREAKOUT("Breakout", "Price breaking key resistance/support", "OPPORTUNITY", 0.85),
    REVERSAL("Reversal", "Potential trend reversal signal", "CAUTION", 0.8);

    private final String displayName;
    private final String description;
    private final String tradingBias;
    private final double riskMultiplier;

    MarketBehavior(String displayName, String description, String tradingBias, double riskMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.tradingBias = tradingBias;
        this.riskMultiplier = riskMultiplier;
    }

    public static MarketBehavior fromString(String value) {
        if (value == null || value.isEmpty()) return RANGING;
        try {
            return MarketBehavior.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return RANGING;
        }
    }
}
