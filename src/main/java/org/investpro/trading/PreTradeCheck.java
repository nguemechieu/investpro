package org.investpro.trading;

import lombok.Getter;


/**
 * Enumeration of all pre-trade validation checks.
 * Each gate must pass before a signal can become an executable order.
 *
 * @author  noel nguemechieu
 */
@Getter
public enum PreTradeCheck {
    // Gate 1: System Health
    SYSTEM_HEALTH("System Health Gate", "Verify InvestPro system state, heartbeat, and critical services"),
    SYSTEM_STATE("System State", "System must be in READY, PAPER_TRADING, or LIVE_TRADING state"),
    KILL_SWITCH("Kill Switch", "Kill switch must not be triggered"),
    AUTO_TRADING_ENABLED("Auto Trading", "Auto trading must be enabled"),

    // Gate 2: Broker & Venue
    BROKER_CONNECTION("Broker Connection", "Broker connection must be established and healthy"),
    VENUE_VALIDATION("Venue Validation", "Venue type must be valid (Spot/Forex/Futures/CFD/Equity)"),
    INSTRUMENT_REGISTRY("Instrument Registry", "Instrument must be registered in InstrumentRegistry"),
    EXCHANGE_SYMBOL_RESOLVED("Exchange Symbol", "Exchange symbol must be resolved for the broker"),
    ASSET_CLASS_KNOWN("Asset Class", "Asset class must be known (not UNKNOWN)"),
    CONTRACT_TYPE_KNOWN("Contract Type", "Contract type must be known (not UNKNOWN)"),
    ACCOUNT_TRADING_ENABLED("Account Permission", "Account must have trading permission enabled"),

    // Gate 3: Market Session
    MARKET_SESSION("Market Session Gate", "Verify market is open and trading is allowed"),
    MARKET_OPEN("Market Open", "Market must be open or in active trading session"),
    SESSION_STATUS("Session Status", "Session status must be OPEN or ACTIVE"),
    ROLLOVER_WINDOW("Rollover Window", "Not inside rollover protection window"),
    HOLIDAY_CALENDAR("Holiday Calendar", "Market must not be closed for holidays"),
    TRADING_HALT("Trading Halt", "No trading halt detected for this instrument"),

    // Gate 4: Data Quality
    DATA_QUALITY("Data Quality Gate", "Verify sufficient clean market data is available"),
    QUOTE_EXISTS("Quote Exists", "Bid/ask or last price must be available"),
    QUOTE_FRESHNESS("Quote Freshness", "Quote must be fresh (within max age threshold)"),
    CANDLE_HISTORY("Candle History", "Sufficient candle history must be loaded"),
    INDICATOR_WARMUP("Indicator Warmup", "Technical indicators must be warmed up"),
    MISSING_CANDLES("Missing Candles", "Missing candles must be below threshold"),
    ORDER_BOOK("Order Book", "Order book must be available or synthetic fallback accepted"),

    // Gate 5: Price & Spread
    PRICE_SPREAD("Price & Spread Gate", "Verify execution cost is acceptable"),
    SPREAD_VALID("Spread Valid", "Bid < Ask and spread is within limits"),
    SPREAD_ABSOLUTE("Spread Absolute", "Absolute spread must be below max"),
    SPREAD_PERCENT("Spread Percent", "Spread percent must be below max"),
    LIQUIDITY_PROFILE("Liquidity Profile", "Instrument must be tradable with acceptable liquidity"),
    MARKET_IMPACT("Market Impact", "Estimated market impact must be acceptable"),

    // Gate 6: News & Event Risk
    NEWS_EVENT_RISK("News & Event Risk Gate", "Verify no dangerous news/event windows nearby"),
    HIGH_IMPACT_NEWS("High Impact News", "No high-impact news event nearby (or strategy allows it)"),
    EARNINGS_EVENT("Earnings Event", "Avoid earnings events unless strategy allows it"),
    BROKER_MAINTENANCE("Broker Maintenance", "Broker must not be in maintenance window"),
    ECONOMIC_CALENDAR("Economic Calendar", "Economic calendar must be loaded for sensitive symbols"),

    // Gate 7: Strategy Signal
    STRATEGY_SIGNAL("Strategy Signal Gate", "Validate the strategy signal itself"),
    SIGNAL_EXISTS("Signal Exists", "Valid BUY/SELL signal must exist"),
    SIGNAL_NOT_HOLD("Signal Not Hold", "Signal must not be HOLD"),
    SIGNAL_CONFIDENCE("Signal Confidence", "Confidence must be above threshold for market type"),
    SIGNAL_FRESHNESS("Signal Freshness", "Signal must not be stale"),
    STRATEGY_ENABLED("Strategy Enabled", "Strategy must be enabled for trading"),
    STRATEGY_ASSIGNED("Strategy Assigned", "Strategy must be assigned to this symbol"),
    STRATEGY_REGIME_COMPATIBLE("Strategy Regime", "Strategy must be compatible with current market regime"),
    NO_DUPLICATE_SIGNAL("No Duplicate Signal", "No duplicate signal for this symbol"),

    // Gate 8: Multi-Strategy Consensus
    STRATEGY_CONSENSUS("Strategy Consensus Gate", "Ensure multi-strategy consensus on trade"),
    PRIMARY_STRATEGY_SUPPORT("Primary Strategy", "Primary strategy must support the trade"),
    CONFIRMATION_STRATEGY("Confirmation Strategy", "Confirmation strategy must agree or be neutral"),
    TREND_FILTER("Trend Filter", "Higher timeframe trend must not be opposite"),
    REGIME_MODEL("Regime Model", "Market regime model must be compatible"),

    // Gate 9: Portfolio Exposure
    PORTFOLIO_EXPOSURE("Portfolio Exposure Gate", "Check account exposure limits"),
    SAME_SYMBOL_POSITION("Same Symbol Position", "Existing position limit not exceeded"),
    OPPOSITE_POSITION("Opposite Position", "No conflicting opposite position"),
    TOTAL_OPEN_POSITIONS("Total Positions", "Total open positions below max"),
    ASSET_CLASS_EXPOSURE("Asset Class Exposure", "Asset class exposure within limit"),
    CORRELATED_EXPOSURE("Correlated Exposure", "Correlated exposure within limit"),
    PORTFOLIO_HEAT("Portfolio Heat", "Portfolio heat (total risk) below max"),

    // Gate 10: Account & Margin
    ACCOUNT_MARGIN("Account & Margin Gate", "Verify account can support the trade"),
    BALANCE_AVAILABLE("Balance Available", "Sufficient balance available"),
    EQUITY_VALID("Equity Valid", "Account equity is valid"),
    FREE_MARGIN("Free Margin", "Sufficient free margin available"),
    MARGIN_USAGE("Margin Usage", "Margin usage below safe limit"),
    MARGIN_CLOSEOUT_RISK("Margin Closeout Risk", "Margin closeout risk below danger level"),
    DRAWDOWN_LIMIT("Drawdown Limit", "Daily drawdown within acceptable limits"),

    // Gate 11: Position Sizing
    POSITION_SIZING("Position Sizing Gate", "Calculate and validate position size"),
    STOP_LOSS_EXISTS("Stop Loss Exists", "Stop loss must be defined"),
    RISK_PER_TRADE("Risk Per Trade", "Risk amount within allowed range"),
    VOLATILITY_ADJUSTMENT("Volatility Adjustment", "Position size adjusted for volatility"),
    LIQUIDITY_ADJUSTMENT("Liquidity Adjustment", "Position size adjusted for liquidity"),
    BROKER_SIZE_LIMITS("Broker Size Limits", "Position size respects broker min/max"),
    FINAL_SIZE_VALID("Final Size Valid", "Calculated size is greater than minimum"),

    // Gate 12: Stop-Loss & Take-Profit
    STOP_TAKE_PROFIT("Stop-Loss & Take-Profit Gate", "Validate SL/TP levels and ratios"),
    STOP_LOSS_REQUIRED("Stop Loss Required", "Stop loss must be defined"),
    TAKE_PROFIT_REQUIRED("Take Profit Required", "Take profit must be defined or strategy-defined"),
    RISK_REWARD_RATIO("Risk/Reward Ratio", "R:R ratio must be above minimum (1.5+)"),
    STOP_DISTANCE_VALID("Stop Distance Valid", "Stop distance is not too tight or too wide"),
    TAKE_PROFIT_REALISTIC("Take Profit Realistic", "Take profit level is realistic"),
    ATR_CHECK("ATR Check", "Stop distance validated against ATR"),
    SUPPORT_RESISTANCE_CHECK("Support/Resistance", "Stop/TP validated against support/resistance"),

    // Gate 13: Trade Frequency
    TRADE_FREQUENCY("Trade Frequency Gate", "Prevent overtrading"),
    COOLDOWN_AFTER_TRADE("Cooldown After Trade", "Sufficient cooldown since last trade"),
    MAX_TRADES_PER_SYMBOL("Max Trades Per Symbol", "Max trades per symbol not exceeded"),
    MAX_TRADES_PER_DAY("Max Trades Per Day", "Max trades per day not exceeded"),
    MAX_LOSSES_IN_ROW("Max Losses in Row", "Max consecutive losses not exceeded"),
    RE_ENTRY_COOLDOWN("Re-entry Cooldown", "Sufficient cooldown since last exit"),

    // Gate 14: AI/Reasoning
    AI_REVIEW("AI Review Gate", "AI reasoning gate (if enabled)"),
    AI_PROVIDER_AVAILABLE("AI Provider Available", "AI provider must be available or fallback allowed"),
    AI_CONFIDENCE("AI Confidence", "AI confidence must be above threshold"),
    AI_DECISION("AI Decision", "AI must approve (not hold or reject)"),

    // Gate 15: Execution Plan
    EXECUTION_PLAN("Execution Plan Gate", "Define execution strategy"),
    ORDER_TYPE_SELECTED("Order Type", "Order type must be selected (Market/Limit/Stop/Bracket)"),
    TIME_IN_FORCE("Time in Force", "Time in force must be set"),
    REDUCE_ONLY_SET("Reduce Only", "Reduce-only flag set when closing position"),
    SLIPPAGE_PROTECTION("Slippage Protection", "Slippage protection must be enabled"),
    RETRY_POLICY_SET("Retry Policy", "Order retry policy must be configured"),
    IDEMPOTENCY_KEY("Idempotency Key", "Unique idempotency key must be generated"),

    // Gate 16: Final Risk
    FINAL_RISK("Final Risk Gate", "Final approval before execution"),

    // Gate 17: Order Submission
    ORDER_SUBMISSION("Order Submission Gate", "Final submission checks"),

    // Gate 18: Post-Trade Monitoring
    POST_TRADE_MONITORING("Post-Trade Monitoring", "Order and position tracking");

    private final String displayName;
    private final String description;

    PreTradeCheck(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns true if this check is a gate (parent category).
     */
    public boolean isGate() {
        return displayName.endsWith("Gate");
    }
}
