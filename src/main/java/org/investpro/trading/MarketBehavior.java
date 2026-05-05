package org.investpro.trading;

/**
 * Enum for market behavior classifications - identifies current market conditions.
 */
public enum MarketBehavior {
    TRENDING_UP("Uptrend", "Prices rising, higher highs and higher lows", "BUY_BIAS"),
    TRENDING_DOWN("Downtrend", "Prices falling, lower highs and lower lows", "SELL_BIAS"),
    RANGING("Range-Bound", "Oscillating between support/resistance", "NEUTRAL"),
    HIGH_VOLATILITY("High Volatility", "Large price swings, elevated uncertainty", "CAUTION"),
    LOW_VOLATILITY("Low Volatility", "Small price swings, consolidation phase", "PATIENCE"),
    BREAKOUT("Breakout", "Breaking above/below key levels", "OPPORTUNITY"),
    REVERSAL("Reversal", "Trend change signals emerging", "CAUTION");

    private final String displayName;
    private final String description;
    private final String tradingBias;  // Suggested trading bias for this condition

    MarketBehavior(String displayName, String description, String tradingBias) {
        this.displayName = displayName;
        this.description = description;
        this.tradingBias = tradingBias;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getTradingBias() { return tradingBias; }
}
