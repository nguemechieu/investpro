package org.investpro.enums;

import lombok.Getter;

/**
 * Enumeration of trading strategy categories.
 * Used for classifying and filtering strategies.
 */
@Getter
public enum StrategyCategory {
    TREND_FOLLOWING("Trend Following", "Strategies that follow established market trends"),
    MEAN_REVERSION("Mean Reversion", "Strategies that trade reversions to mean/average"),
    BREAKOUT("Breakout", "Strategies that trade breakouts from support/resistance"),
    MOMENTUM("Momentum", "Strategies based on momentum and acceleration"),
    VOLATILITY("Volatility", "Strategies that exploit volatility expansion/contraction"),
    SCALPING("Scalping", "High-frequency short-term strategies"),
    SWING("Swing", "Strategies that capture multi-day swings"),
    ARBITRAGE("Arbitrage", "Statistical arbitrage and pair trading"),
    MARKET_MAKING("Market Making", "Strategies that provide liquidity"),
    NEWS_EVENT("News/Event", "Strategies triggered by news or events"),
    AI_ASSISTED("AI Assisted", "Strategies using AI/ML components"),
    HYBRID("Hybrid", "Multi-indicator or multi-component strategies");

    private final String displayName;
    private final String description;

    StrategyCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
