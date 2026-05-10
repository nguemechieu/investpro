package org.investpro.decision;

/**
 * Asset market classification based on underlying instrument type.
 * Determines applicable strategies, risk parameters, and holding period
 * estimates.
 */
public enum AssetMarketType {
    /**
     * Forex currency pairs (EUR/USD, GBP/JPY, etc.)
     * Characteristics: 24/5 liquid, tight spreads, high leverage available, no gaps
     * on weekends.
     * Suitable strategies: scalping, carry trades, trend following.
     */
    FOREX("Foreign exchange currency pairs", true, false, false),

    /**
     * Spot crypto assets (BTC/USD, ETH/USDT, etc.)
     * Characteristics: 24/7 liquid, variable spreads, high volatility, rapid
     * news-driven moves.
     * Suitable strategies: momentum, swing trading, risk-on/off rotation.
     */
    CRYPTO_SPOT("Cryptocurrency spot trading", true, true, false),

    /**
     * Crypto derivatives (perpetual futures, options)
     * Characteristics: high leverage, funding rates, forced liquidations, mark
     * price mechanisms.
     * Suitable strategies: leveraged trend following, hedging, arbitrage.
     */
    CRYPTO_DERIVATIVES("Cryptocurrency derivatives/futures", true, true, true),

    /**
     * Stock indices and individual equities
     * Characteristics: market hours trading, dividend adjustments, earnings
     * catalysts, sector rotation.
     * Suitable strategies: momentum, mean reversion, earnings-based, sector
     * rotation.
     */
    EQUITIES("Stocks and indices", false, false, false),

    /**
     * Equity index derivatives (ES, NQ futures, index options)
     * Characteristics: leverage available, after-hours gaps, contract
     * specifications.
     * Suitable strategies: leveraged trend following, hedging, systematic.
     */
    EQUITY_DERIVATIVES("Equity derivatives and futures", false, false, true),

    /**
     * Commodities (oil, gold, agricultural)
     * Characteristics: supply/demand driven, weather dependent, seasonal patterns,
     * physical storage.
     * Suitable strategies: trend following, seasonal, carry trades, hedging.
     */
    COMMODITIES("Commodities and raw materials", false, false, true),

    /**
     * Bonds and fixed income
     * Characteristics: rate-sensitive, duration risk, credit events,
     * flight-to-quality.
     * Suitable strategies: carry trades, curve positioning, duration strategies.
     */
    FIXED_INCOME("Bonds and fixed income", false, false, false),

    /**
     * Asset class unknown or not yet determined
     */
    UNKNOWN("Unknown asset type", false, false, false);

    public final String description;
    public final boolean is24Hour; // trades 24 hours or near-continuous
    public final boolean isHighVolatility; // naturally high volatility asset class
    public final boolean supportsLeverage; // leverage/derivatives available

    AssetMarketType(String description, boolean is24Hour, boolean isHighVolatility, boolean supportsLeverage) {
        this.description = description;
        this.is24Hour = is24Hour;
        this.isHighVolatility = isHighVolatility;
        this.supportsLeverage = supportsLeverage;
    }

    public boolean isCrypto() {
        return this == CRYPTO_SPOT || this == CRYPTO_DERIVATIVES;
    }

    public boolean isDerivative() {
        return this == CRYPTO_DERIVATIVES || this == EQUITY_DERIVATIVES || this == COMMODITIES;
    }
}
