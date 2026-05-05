package org.investpro.indicators;

/**
 * Enumeration of supported technical indicators
 */
public enum IndicatorType {
    NONE("None", "No indicator"),
    SMA("SMA", "Simple Moving Average"),
    EMA("EMA", "Exponential Moving Average"),
    RSI("RSI", "Relative Strength Index"),
    MACD("MACD", "Moving Average Convergence Divergence"),
    BOLLINGER("Bollinger", "Bollinger Bands"),
    STOCHASTIC("Stochastic", "Stochastic Oscillator");

    private final String displayName;
    private final String description;

    IndicatorType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
