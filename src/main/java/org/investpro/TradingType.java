package org.investpro;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public enum TradingType {

    SCALPING("Seconds to Minutes", "Fast executions, small profits", "High"),
    DAY_TRADING("Intraday", "Momentum, Breakouts", "High"),
    SWING_TRADING("Days to Weeks", "Trend Following", "Medium"),
    POSITION_TRADING("Weeks to Years", "Buy & Hold, Long-Term Trends", "Low"),
    TREND_TRADING("Varies", "Follows Market Trends", "Medium"),
    BREAKOUT_TRADING("Varies", "Entry on Support/Resistance Breaks", "Medium"),
    ARBITRAGE_TRADING("Milliseconds to Seconds", "Price Differences Across Markets", "Low"),
    ALGORITHMIC_TRADING("Automated", "AI, Quantitative Strategies", "Medium"),
    FOREX_TRADING("Varies", "Currencies (EUR/USD, GBP/JPY)", "Medium"),
    CRYPTO_TRADING("24/7", "Bitcoin, Altcoins", "High"),
    STOCK_TRADING("Varies", "Equities", "Medium"),
    COMMODITIES_TRADING("Varies", "Gold, Oil, Silver", "Medium"),
    HIGH_FREQUENCY_TRADING("Milliseconds", "Algo Trading, AI", "High");

    private final String timeframe;
    private final String strategyFocus;
    private final String riskLevel;

    TradingType(String timeframe, String strategyFocus, String riskLevel) {
        this.timeframe = timeframe;
        this.strategyFocus = strategyFocus;
        this.riskLevel = riskLevel;
    }

    @Override
    public @NotNull String toString() {
        return String.format("%s: Timeframe = %s, Strategy Focus = %s, Risk Level = %s",
                name(), timeframe, strategyFocus, riskLevel);
    }
}