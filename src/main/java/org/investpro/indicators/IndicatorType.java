package org.investpro.indicators;

import lombok.Getter;

/**
 * Enumeration of supported technical indicators
 */
@Getter
public enum IndicatorType {
    NONE("None", "No indicator"),
    SMA("SMA", "Simple Moving Average"),
    EMA("EMA", "Exponential Moving Average"),
    RSI("RSI", "Relative Strength Index"),
    MACD("MACD", "Moving Average Convergence Divergence"),
    BOLLINGER("Bollinger", "Bollinger Bands"),
    STOCHASTIC("Stochastic", "Stochastic Oscillator"),
    ATR("ATR", "Average True Range"),
    ADX("ADX", "Average Directional Index"),
    CCI("CCI", "Commodity Channel Index"),
    OBV("OBV", "On Balance Volume"),
    VWAP("VWAP", "Volume Weighted Average Price"),
    WILLIAMS_R("Williams %R", "Momentum oscillator over high-low range"),
    MOMENTUM("Momentum", "Price change over lookback period"),
    DONCHIAN("Donchian Channel", "Highest high and lowest low channel");

    private final String displayName;
    private final String description;

    IndicatorType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
