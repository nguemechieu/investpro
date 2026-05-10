package org.investpro.trading.validation;

/**
 * Validation rules and thresholds for pre-trade checks.
 * These are configurable parameters that drive the validation gate logic.
 *
 * @author InvestPro Trading System
 */
public class PreTradeValidationRules {

    // System Health Thresholds
    public static final int MAX_ERROR_COUNT_BEFORE_HALT = 5;
    public static final long MAX_HEARTBEAT_STALENESS_MS = 60_000; // 60 seconds

    // Data Quality
    public static final int MIN_REQUIRED_CANDLES = 300;
    public static final double MAX_MISSING_CANDLES_PERCENT = 0.01; // 1%
    public static final long MAX_QUOTE_AGE_MS = 5_000; // 5 seconds

    // Spread Limits (by asset class)
    public static final double MAX_SPREAD_FOREX_MAJOR = 0.001; // 0.1%
    public static final double MAX_SPREAD_CRYPTO_MAJOR = 0.005; // 0.5%
    public static final double MAX_SPREAD_CRYPTO_SMALL = 0.01; // 1%
    public static final double MAX_SPREAD_STOCK = 0.01; // 1%

    // Signal Confidence Thresholds (by trading mode)
    public static final double CONFIDENCE_THRESHOLD_PAPER = 0.55; // 55% for paper trading
    public static final double CONFIDENCE_THRESHOLD_LIVE = 0.70; // 70% for live trading
    public static final double CONFIDENCE_THRESHOLD_HIGH_RISK = 0.75; // 75% for high-risk assets

    // Position Sizing
    public static final double RISK_PERCENT_LOW_EQUITY = 0.005; // 0.5% for accounts < $100
    public static final double RISK_PERCENT_NORMAL = 0.01; // 1% for normal accounts
    public static final double RISK_PERCENT_AGGRESSIVE = 0.02; // 2% aggressive
    public static final double MAX_RISK_PERCENT = 0.05; // 5% absolute maximum

    // Stop Loss / Take Profit
    public static final double MIN_RISK_REWARD_RATIO = 1.5;
    public static final double PREFERRED_RISK_REWARD_RATIO = 2.0;

    // Portfolio Limits
    public static final int MAX_OPEN_POSITIONS = 5;
    public static final double MAX_PORTFOLIO_HEAT_PERCENT = 0.06; // 6% max total risk
    public static final double MAX_SAME_SYMBOL_EXPOSURE = 1.0; // 1 position per symbol
    public static final double MAX_CORRELATED_EXPOSURE_PERCENT = 0.03; // 3%

    // Account & Margin
    public static final double MIN_FREE_MARGIN_PERCENT = 0.20; // 20% min free margin
    public static final double DANGER_MARGIN_USAGE_PERCENT = 0.80; // 80% danger level
    public static final double MAX_DAILY_DRAWDOWN_PERCENT = 0.05; // 5% max daily loss

    // Trade Frequency
    public static final long COOLDOWN_AFTER_TRADE_MS = 60_000; // 60 seconds
    public static final int MAX_TRADES_PER_SYMBOL_PER_DAY = 5;
    public static final int MAX_TRADES_PER_DAY = 20;
    public static final int MAX_CONSECUTIVE_LOSSES = 3;
    public static final int RE_ENTRY_COOLDOWN_CANDLES = 5;

    // News & Events
    public static final long HIGH_IMPACT_NEWS_WINDOW_BEFORE_MS = 15 * 60_000; // 15 minutes before
    public static final long HIGH_IMPACT_NEWS_WINDOW_AFTER_MS = 15 * 60_000; // 15 minutes after

    // Multi-Strategy Consensus
    public static final double MIN_CONSENSUS_CONVICTION = 0.70;

    // Slippage & Market Impact
    public static final double MAX_SLIPPAGE_PERCENT = 0.005; // 0.5%
    public static final double MAX_MARKET_IMPACT_PERCENT = 0.02; // 2%

    // Small Account Special Rules
    public static final double SMALL_ACCOUNT_THRESHOLD = 100.0;
    public static final int SMALL_ACCOUNT_MAX_POSITIONS = 2;

    // AI Review
    public static final double MIN_AI_CONFIDENCE = 0.50; // 50% confidence needed from AI

    // Margin & Leverage
    public static final double MAX_LEVERAGE = 100.0; // Broker-dependent

    /**
     * Get appropriate confidence threshold based on trading mode and asset class.
     */
    public static double getConfidenceThreshold(String tradingMode, String assetClass) {
        if ("LIVE".equalsIgnoreCase(tradingMode)) {
            // High risk assets need higher confidence in live mode
            if ("CRYPTO_ASSET".equalsIgnoreCase(assetClass) || "DERIVATIVE".equalsIgnoreCase(assetClass)) {
                return CONFIDENCE_THRESHOLD_HIGH_RISK;
            }
            return CONFIDENCE_THRESHOLD_LIVE;
        }
        return CONFIDENCE_THRESHOLD_PAPER;
    }

    /**
     * Get appropriate risk percent based on account size.
     */
    public static double getRiskPercent(double accountEquity, boolean isAggressive) {
        if (accountEquity < SMALL_ACCOUNT_THRESHOLD) {
            return RISK_PERCENT_LOW_EQUITY;
        }
        return isAggressive ? RISK_PERCENT_AGGRESSIVE : RISK_PERCENT_NORMAL;
    }

    /**
     * Get max spread percent based on asset class and venue.
     */
    public static double getMaxSpreadPercent(String assetClass, String venue) {
        if ("CRYPTO_ASSET".equalsIgnoreCase(assetClass)) {
            // Major crypto venues have tighter spreads
            if ("BINANCE".equalsIgnoreCase(venue) || "COINBASE".equalsIgnoreCase(venue)) {
                return MAX_SPREAD_CRYPTO_MAJOR;
            }
            return MAX_SPREAD_CRYPTO_SMALL;
        }

        if ("FOREX".equalsIgnoreCase(venue) || "FOREX".equalsIgnoreCase(assetClass)) {
            return MAX_SPREAD_FOREX_MAJOR;
        }

        if ("EQUITY".equalsIgnoreCase(assetClass)) {
            return MAX_SPREAD_STOCK;
        }

        return 0.01; // default 1%
    }
}
