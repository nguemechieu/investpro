package org.investpro.indicators;

import lombok.Getter;

/**
 * Supported technical and derived indicators used by strategies,
 * backtesting, signal generation, market analysis, and AI reasoning.
 */
@Getter
public enum INDICATORS {

    // =========================================================
    // Price / Candle Base Data
    // =========================================================
    OPEN("Open", IndicatorCategory.PRICE, "Opening price"),
    HIGH("High", IndicatorCategory.PRICE, "Highest price"),
    LOW("Low", IndicatorCategory.PRICE, "Lowest price"),
    CLOSE("Close", IndicatorCategory.PRICE, "Closing price"),
    HL2("HL2", IndicatorCategory.PRICE, "Average of high and low"),
    HLC3("HLC3", IndicatorCategory.PRICE, "Average of high, low, and close"),
    OHLC4("OHLC4", IndicatorCategory.PRICE, "Average of open, high, low, and close"),
    TYPICAL_PRICE("Typical Price", IndicatorCategory.PRICE, "Typical price: (high + low + close) / 3"),

    // =========================================================
    // Moving Averages / Trend
    // =========================================================
    SMA("Simple Moving Average", IndicatorCategory.TREND, "Simple moving average"),
    EMA("Exponential Moving Average", IndicatorCategory.TREND, "Exponential moving average"),
    WMA("Weighted Moving Average", IndicatorCategory.TREND, "Weighted moving average"),
    HMA("Hull Moving Average", IndicatorCategory.TREND, "Hull moving average"),
    DEMA("Double EMA", IndicatorCategory.TREND, "Double exponential moving average"),
    TEMA("Triple EMA", IndicatorCategory.TREND, "Triple exponential moving average"),
    VWMA("Volume Weighted Moving Average", IndicatorCategory.TREND, "Volume-weighted moving average"),
    KAMA("Kaufman Adaptive Moving Average", IndicatorCategory.TREND, "Adaptive moving average"),
    ZLEMA("Zero Lag EMA", IndicatorCategory.TREND, "Zero-lag exponential moving average"),

    EMA_FAST("Fast EMA", IndicatorCategory.TREND, "Fast EMA used for trend crossover"),
    EMA_SLOW("Slow EMA", IndicatorCategory.TREND, "Slow EMA used for trend crossover"),
    MA_CROSSOVER("Moving Average Crossover", IndicatorCategory.TREND, "Fast/slow moving average crossover"),

    // =========================================================
    // Momentum Indicators
    // =========================================================
    RSI("Relative Strength Index", IndicatorCategory.MOMENTUM, "Measures overbought and oversold conditions"),
    STOCHASTIC("Stochastic Oscillator", IndicatorCategory.MOMENTUM, "Compares close to high-low range"),
    STOCHASTIC_K("Stochastic %K", IndicatorCategory.MOMENTUM, "Fast stochastic line"),
    STOCHASTIC_D("Stochastic %D", IndicatorCategory.MOMENTUM, "Slow stochastic signal line"),
    STOCH_RSI("Stochastic RSI", IndicatorCategory.MOMENTUM, "Stochastic calculation applied to RSI"),
    CCI("Commodity Channel Index", IndicatorCategory.MOMENTUM, "Measures price deviation from average"),
    MOMENTUM("Momentum", IndicatorCategory.MOMENTUM, "Rate of price change"),
    ROC("Rate of Change", IndicatorCategory.MOMENTUM, "Percentage rate of price change"),
    WILLIAMS_R("Williams %R", IndicatorCategory.MOMENTUM, "Momentum oscillator measuring overbought/oversold"),
    TRIX("TRIX", IndicatorCategory.MOMENTUM, "Triple-smoothed rate of change"),
    ULTIMATE_OSCILLATOR("Ultimate Oscillator", IndicatorCategory.MOMENTUM, "Multi-period momentum oscillator"),

    // =========================================================
    // MACD Family
    // =========================================================
    MACD("MACD", IndicatorCategory.MOMENTUM, "Moving Average Convergence Divergence"),
    MACD_LINE("MACD Line", IndicatorCategory.MOMENTUM, "MACD fast minus slow EMA"),
    MACD_SIGNAL("MACD Signal", IndicatorCategory.MOMENTUM, "MACD signal line"),
    MACD_HISTOGRAM("MACD Histogram", IndicatorCategory.MOMENTUM, "MACD line minus signal line"),

    // =========================================================
    // Volatility Indicators
    // =========================================================
    ATR("Average True Range", IndicatorCategory.VOLATILITY, "Measures average true range"),
    ATR_PERCENT("ATR Percent", IndicatorCategory.VOLATILITY, "ATR as percentage of price"),
    TRUE_RANGE("True Range", IndicatorCategory.VOLATILITY, "Current true range"),
    BOLLINGER_BANDS("Bollinger Bands", IndicatorCategory.VOLATILITY, "Volatility bands around moving average"),
    BOLLINGER_UPPER("Bollinger Upper Band", IndicatorCategory.VOLATILITY, "Upper Bollinger Band"),
    BOLLINGER_MIDDLE("Bollinger Middle Band", IndicatorCategory.VOLATILITY, "Middle Bollinger moving average"),
    BOLLINGER_LOWER("Bollinger Lower Band", IndicatorCategory.VOLATILITY, "Lower Bollinger Band"),
    BOLLINGER_WIDTH("Bollinger Band Width", IndicatorCategory.VOLATILITY, "Distance between upper and lower bands"),
    BOLLINGER_PERCENT_B("Bollinger %B", IndicatorCategory.VOLATILITY, "Position of price inside Bollinger Bands"),
    KELTNER_CHANNEL("Keltner Channel", IndicatorCategory.VOLATILITY, "ATR-based volatility channel"),
    KELTNER_UPPER("Keltner Upper Channel", IndicatorCategory.VOLATILITY, "Upper Keltner Channel"),
    KELTNER_LOWER("Keltner Lower Channel", IndicatorCategory.VOLATILITY, "Lower Keltner Channel"),
    DONCHIAN_CHANNEL("Donchian Channel", IndicatorCategory.VOLATILITY, "Highest high and lowest low channel"),
    DONCHIAN_HIGH("Donchian High", IndicatorCategory.VOLATILITY, "Highest high over lookback"),
    DONCHIAN_LOW("Donchian Low", IndicatorCategory.VOLATILITY, "Lowest low over lookback"),
    STANDARD_DEVIATION("Standard Deviation", IndicatorCategory.VOLATILITY, "Volatility based on standard deviation"),
    HISTORICAL_VOLATILITY("Historical Volatility", IndicatorCategory.VOLATILITY, "Realized historical volatility"),

    // =========================================================
    // Volume Indicators
    // =========================================================
    VOLUME("Volume", IndicatorCategory.VOLUME, "Trade volume"),
    VOLUME_SMA("Volume SMA", IndicatorCategory.VOLUME, "Average volume"),
    VOLUME_RATIO("Volume Ratio", IndicatorCategory.VOLUME, "Current volume relative to average volume"),
    OBV("On Balance Volume", IndicatorCategory.VOLUME, "Cumulative volume flow"),
    VWAP("VWAP", IndicatorCategory.VOLUME, "Volume weighted average price"),
    MFI("Money Flow Index", IndicatorCategory.VOLUME, "Volume-weighted RSI-like oscillator"),
    CMF("Chaikin Money Flow", IndicatorCategory.VOLUME, "Accumulation/distribution pressure"),
    ADL("Accumulation Distribution Line", IndicatorCategory.VOLUME, "Cumulative buying/selling pressure"),
    VOLUME_PROFILE("Volume Profile", IndicatorCategory.VOLUME, "Volume distribution by price"),
    POC("Point of Control", IndicatorCategory.VOLUME, "Price level with highest traded volume"),
    VOLUME_SPIKE("Volume Spike", IndicatorCategory.VOLUME, "Abnormally high volume event"),

    // =========================================================
    // Trend Strength / Direction
    // =========================================================
    ADX("Average Directional Index", IndicatorCategory.TREND_STRENGTH, "Measures trend strength"),
    PLUS_DI("+DI", IndicatorCategory.TREND_STRENGTH, "Positive directional indicator"),
    MINUS_DI("-DI", IndicatorCategory.TREND_STRENGTH, "Negative directional indicator"),
    AROON("Aroon", IndicatorCategory.TREND_STRENGTH, "Measures trend direction and strength"),
    AROON_UP("Aroon Up", IndicatorCategory.TREND_STRENGTH, "Aroon bullish line"),
    AROON_DOWN("Aroon Down", IndicatorCategory.TREND_STRENGTH, "Aroon bearish line"),
    PSAR("Parabolic SAR", IndicatorCategory.TREND, "Trend-following stop and reverse indicator"),
    SUPERTREND("Supertrend", IndicatorCategory.TREND, "ATR-based trend-following indicator"),
    ICHIMOKU("Ichimoku Cloud", IndicatorCategory.TREND, "Multi-line trend and support/resistance system"),
    ICHIMOKU_TENKAN("Ichimoku Tenkan-sen", IndicatorCategory.TREND, "Ichimoku conversion line"),
    ICHIMOKU_KIJUN("Ichimoku Kijun-sen", IndicatorCategory.TREND, "Ichimoku base line"),
    ICHIMOKU_SENKOU_A("Ichimoku Senkou Span A", IndicatorCategory.TREND, "Ichimoku leading span A"),
    ICHIMOKU_SENKOU_B("Ichimoku Senkou Span B", IndicatorCategory.TREND, "Ichimoku leading span B"),
    ICHIMOKU_CHIKOU("Ichimoku Chikou Span", IndicatorCategory.TREND, "Ichimoku lagging span"),

    // =========================================================
    // Support / Resistance / Structure
    // =========================================================
    SUPPORT("Support", IndicatorCategory.MARKET_STRUCTURE, "Detected support level"),
    RESISTANCE("Resistance", IndicatorCategory.MARKET_STRUCTURE, "Detected resistance level"),
    PIVOT_POINT("Pivot Point", IndicatorCategory.MARKET_STRUCTURE, "Classic pivot point"),
    PIVOT_R1("Pivot R1", IndicatorCategory.MARKET_STRUCTURE, "First resistance pivot"),
    PIVOT_R2("Pivot R2", IndicatorCategory.MARKET_STRUCTURE, "Second resistance pivot"),
    PIVOT_R3("Pivot R3", IndicatorCategory.MARKET_STRUCTURE, "Third resistance pivot"),
    PIVOT_S1("Pivot S1", IndicatorCategory.MARKET_STRUCTURE, "First support pivot"),
    PIVOT_S2("Pivot S2", IndicatorCategory.MARKET_STRUCTURE, "Second support pivot"),
    PIVOT_S3("Pivot S3", IndicatorCategory.MARKET_STRUCTURE, "Third support pivot"),
    SWING_HIGH("Swing High", IndicatorCategory.MARKET_STRUCTURE, "Local high pivot"),
    SWING_LOW("Swing Low", IndicatorCategory.MARKET_STRUCTURE, "Local low pivot"),
    HIGHER_HIGH("Higher High", IndicatorCategory.MARKET_STRUCTURE, "Bullish structure event"),
    HIGHER_LOW("Higher Low", IndicatorCategory.MARKET_STRUCTURE, "Bullish pullback structure"),
    LOWER_HIGH("Lower High", IndicatorCategory.MARKET_STRUCTURE, "Bearish pullback structure"),
    LOWER_LOW("Lower Low", IndicatorCategory.MARKET_STRUCTURE, "Bearish structure event"),
    TRENDLINE("Trendline", IndicatorCategory.MARKET_STRUCTURE, "Detected trendline"),
    BREAKOUT_LEVEL("Breakout Level", IndicatorCategory.MARKET_STRUCTURE, "Key breakout level"),
    BREAKDOWN_LEVEL("Breakdown Level", IndicatorCategory.MARKET_STRUCTURE, "Key breakdown level"),

    // =========================================================
    // Fibonacci
    // =========================================================
    FIBONACCI_RETRACEMENT("Fibonacci Retracement", IndicatorCategory.FIBONACCI, "Fibonacci retracement levels"),
    FIB_236("Fib 23.6%", IndicatorCategory.FIBONACCI, "23.6% retracement"),
    FIB_382("Fib 38.2%", IndicatorCategory.FIBONACCI, "38.2% retracement"),
    FIB_500("Fib 50.0%", IndicatorCategory.FIBONACCI, "50.0% retracement"),
    FIB_618("Fib 61.8%", IndicatorCategory.FIBONACCI, "61.8% retracement"),
    FIB_786("Fib 78.6%", IndicatorCategory.FIBONACCI, "78.6% retracement"),
    FIBONACCI_EXTENSION("Fibonacci Extension", IndicatorCategory.FIBONACCI, "Fibonacci extension levels"),

    // =========================================================
    // Market Regime / Behavior
    // =========================================================
    MARKET_REGIME("Market Regime", IndicatorCategory.REGIME, "Current market regime"),
    MARKET_BEHAVIOR("Market Behavior", IndicatorCategory.REGIME, "Trending, ranging, volatile, breakout, reversal"),
    TRENDING_SCORE("Trending Score", IndicatorCategory.REGIME, "Strength of trend regime"),
    RANGING_SCORE("Ranging Score", IndicatorCategory.REGIME, "Strength of ranging regime"),
    VOLATILITY_SCORE("Volatility Score", IndicatorCategory.REGIME, "Current volatility regime score"),
    BREAKOUT_SCORE("Breakout Score", IndicatorCategory.REGIME, "Breakout probability score"),
    REVERSAL_SCORE("Reversal Score", IndicatorCategory.REGIME, "Reversal probability score"),
    REGIME_CONFIDENCE("Regime Confidence", IndicatorCategory.REGIME, "Confidence in regime classification"),

    // =========================================================
    // Order Book / Microstructure
    // =========================================================
    BID_PRICE("Bid Price", IndicatorCategory.MICROSTRUCTURE, "Best bid price"),
    ASK_PRICE("Ask Price", IndicatorCategory.MICROSTRUCTURE, "Best ask price"),
    BID_ASK_SPREAD("Bid Ask Spread", IndicatorCategory.MICROSTRUCTURE, "Difference between ask and bid"),
    SPREAD_PERCENT("Spread Percent", IndicatorCategory.MICROSTRUCTURE, "Spread as percentage of mid price"),
    MID_PRICE("Mid Price", IndicatorCategory.MICROSTRUCTURE, "Average of bid and ask"),
    ORDER_BOOK_IMBALANCE("Order Book Imbalance", IndicatorCategory.MICROSTRUCTURE, "Bid/ask depth imbalance"),
    BID_DEPTH("Bid Depth", IndicatorCategory.MICROSTRUCTURE, "Total bid-side depth"),
    ASK_DEPTH("Ask Depth", IndicatorCategory.MICROSTRUCTURE, "Total ask-side depth"),
    LIQUIDITY_SCORE("Liquidity Score", IndicatorCategory.MICROSTRUCTURE, "Estimated liquidity quality"),
    SLIPPAGE_ESTIMATE("Slippage Estimate", IndicatorCategory.MICROSTRUCTURE, "Estimated execution slippage"),
    FILL_PROBABILITY("Fill Probability", IndicatorCategory.MICROSTRUCTURE, "Estimated fill probability"),

    // =========================================================
    // Risk / Position Sizing
    // =========================================================
    RISK_REWARD_RATIO("Risk Reward Ratio", IndicatorCategory.RISK, "Reward divided by risk"),
    POSITION_SIZE("Position Size", IndicatorCategory.RISK, "Recommended position size"),
    STOP_LOSS_PRICE("Stop Loss Price", IndicatorCategory.RISK, "Recommended stop-loss price"),
    TAKE_PROFIT_PRICE("Take Profit Price", IndicatorCategory.RISK, "Recommended take-profit price"),
    TRAILING_STOP("Trailing Stop", IndicatorCategory.RISK, "Trailing stop value"),
    PORTFOLIO_HEAT("Portfolio Heat", IndicatorCategory.RISK, "Total portfolio risk exposure"),
    DRAWDOWN("Drawdown", IndicatorCategory.RISK, "Peak-to-trough decline"),
    MAX_DRAWDOWN("Max Drawdown", IndicatorCategory.RISK, "Maximum historical drawdown"),
    VALUE_AT_RISK("Value at Risk", IndicatorCategory.RISK, "Estimated loss threshold"),
    EXPECTED_VALUE("Expected Value", IndicatorCategory.RISK, "Expected trade value"),

    // =========================================================
    // Performance / Backtesting Metrics
    // =========================================================
    WIN_RATE("Win Rate", IndicatorCategory.PERFORMANCE, "Percentage of winning trades"),
    PROFIT_FACTOR("Profit Factor", IndicatorCategory.PERFORMANCE, "Gross profit divided by gross loss"),
    SHARPE_RATIO("Sharpe Ratio", IndicatorCategory.PERFORMANCE, "Risk-adjusted return"),
    SORTINO_RATIO("Sortino Ratio", IndicatorCategory.PERFORMANCE, "Downside-risk-adjusted return"),
    CALMAR_RATIO("Calmar Ratio", IndicatorCategory.PERFORMANCE, "Return divided by max drawdown"),
    RECOVERY_FACTOR("Recovery Factor", IndicatorCategory.PERFORMANCE, "Net profit divided by max drawdown"),
    AVERAGE_WIN("Average Win", IndicatorCategory.PERFORMANCE, "Average winning trade"),
    AVERAGE_LOSS("Average Loss", IndicatorCategory.PERFORMANCE, "Average losing trade"),
    EXPECTANCY("Expectancy", IndicatorCategory.PERFORMANCE, "Average expected return per trade"),
    TOTAL_RETURN("Total Return", IndicatorCategory.PERFORMANCE, "Total strategy return"),
    NET_PROFIT("Net Profit", IndicatorCategory.PERFORMANCE, "Total net profit"),
    TRADE_COUNT("Trade Count", IndicatorCategory.PERFORMANCE, "Number of trades"),

    // =========================================================
    // AI / ML / Derived Features
    // =========================================================
    AI_CONFIDENCE("AI Confidence", IndicatorCategory.AI, "AI model confidence"),
    ML_PROBABILITY("ML Probability", IndicatorCategory.AI, "Machine learning probability output"),
    SENTIMENT_SCORE("Sentiment Score", IndicatorCategory.AI, "News/social sentiment score"),
    NEWS_IMPACT_SCORE("News Impact Score", IndicatorCategory.AI, "Estimated market impact from news"),
    SIGNAL_CONFIDENCE("Signal Confidence", IndicatorCategory.AI, "Final signal confidence"),
    CONSENSUS_SCORE("Consensus Score", IndicatorCategory.AI, "Strategy voting consensus score"),
    FEATURE_IMPORTANCE("Feature Importance", IndicatorCategory.AI, "Model feature importance"),
    ANOMALY_SCORE("Anomaly Score", IndicatorCategory.AI, "Unusual market condition score"),

    // =========================================================
    // Session / Time-Based Features
    // =========================================================
    TRADING_SESSION("Trading Session", IndicatorCategory.TIME, "Current trading session"),
    SESSION_OPEN("Session Open", IndicatorCategory.TIME, "Whether the symbol session is open"),
    SESSION_VOLUME("Session Volume", IndicatorCategory.TIME, "Volume during current session"),
    TIME_OF_DAY("Time of Day", IndicatorCategory.TIME, "Current trading time bucket"),
    DAY_OF_WEEK("Day of Week", IndicatorCategory.TIME, "Current weekday feature"),
    MARKET_HOURS_SCORE("Market Hours Score", IndicatorCategory.TIME, "Quality score for current trading time"),

    // =========================================================
    // Generic / Custom
    // =========================================================
    CUSTOM("Custom", IndicatorCategory.CUSTOM, "Custom user-defined indicator"),
    UNKNOWN("Unknown", IndicatorCategory.CUSTOM, "Unknown indicator");

    private final String displayName;
    private final IndicatorCategory category;
    private final String description;

    INDICATORS(String displayName, IndicatorCategory category, String description) {
        this.displayName = displayName;
        this.category = category;
        this.description = description;
    }

    public boolean isTrendIndicator() {
        return category == IndicatorCategory.TREND || category == IndicatorCategory.TREND_STRENGTH;
    }

    public boolean isMomentumIndicator() {
        return category == IndicatorCategory.MOMENTUM;
    }

    public boolean isVolatilityIndicator() {
        return category == IndicatorCategory.VOLATILITY;
    }

    public boolean isVolumeIndicator() {
        return category == IndicatorCategory.VOLUME;
    }

    public boolean isRiskIndicator() {
        return category == IndicatorCategory.RISK;
    }

    public boolean isPerformanceMetric() {
        return category == IndicatorCategory.PERFORMANCE;
    }

    public boolean isAiDerived() {
        return category == IndicatorCategory.AI;
    }

    public boolean isMarketStructureIndicator() {
        return category == IndicatorCategory.MARKET_STRUCTURE;
    }

    public static INDICATORS fromName(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String normalized = normalize(value);

        for (INDICATORS indicator : values()) {
            if (normalize(indicator.name()).equals(normalized)
                    || normalize(indicator.displayName).equals(normalized)) {
                return indicator;
            }
        }

        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .replace("-", "_")
                .replace(" ", "_")
                .replace("/", "_")
                .toUpperCase();
    }

    public enum IndicatorCategory {
        PRICE,
        TREND,
        TREND_STRENGTH,
        MOMENTUM,
        VOLATILITY,
        VOLUME,
        MARKET_STRUCTURE,
        FIBONACCI,
        REGIME,
        MICROSTRUCTURE,
        RISK,
        PERFORMANCE,
        AI,
        TIME,
        CUSTOM
    }
}
