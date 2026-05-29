package org.investpro.strategy.position;

/**
 * Supported position sizing methods in the InvestPro execution pipeline.
 *
 * <p>
 * <strong>IMPORTANT:</strong> The RiskEngine has final authority over all
 * position sizing decisions. Values produced by {@link PositionSizingEngine}
 * must be reviewed and approved by the RiskEngine before any order is
 * submitted.
 * </p>
 */
public enum PositionSizingMethod {
    // Legacy names retained for compatibility with existing engines/pipeline.
    RISK_PERCENT("Risk a fixed percentage of account equity per trade"),
    FIXED_LOT("Trade a fixed lot size"),
    VOLATILITY_ADJUSTED("Adjust position size inversely to current volatility"),
    EQUAL_WEIGHT("Equal weight across active positions"),
    MAX_LOSS("Size by maximum allowed loss"),

    FIXED_RISK_PERCENT("Risk a fixed percentage of account equity per trade"),
    ATR_BASED("Size position based on Average True Range volatility"),
    VOLATILITY_BASED("Adjust position size inversely to current volatility"),
    KELLY_CRITERION("Use Kelly Criterion formula for optimal sizing"),
    RISK_PARITY("Equal risk contribution across all positions"),
    DRAWDOWN_SCALING("Scale position size based on current drawdown level"),
    FIXED_UNITS("Trade a fixed number of units regardless of volatility");

    /** Human-readable description of this sizing method. */
    public final String description;

    PositionSizingMethod(String description) {
        this.description = description;
    }
}
