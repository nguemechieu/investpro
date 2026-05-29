package org.investpro.strategy.nocode;

/**
 * Technical indicators available to the no-code strategy builder.
 *
 * <p>Each value represents a computable indicator whose current value can be
 * compared inside a {@link NoCodeCondition}. The corresponding
 * {@link NoCodeIndicatorReference} carries the period and any extra parameters
 * needed to compute the indicator at runtime.</p>
 */
public enum NoCodeIndicatorType {

    // ── Moving Averages ─────────────────────────────────────────────────────
    /** Simple Moving Average of close prices. Period required. */
    SMA("SMA", "Simple Moving Average", true),

    /** Exponential Moving Average of close prices. Period required. */
    EMA("EMA", "Exponential Moving Average", true),

    // ── Oscillators ─────────────────────────────────────────────────────────
    /**
     * Relative Strength Index. Period required (default 14).
     * Values range 0–100.
     */
    RSI("RSI", "Relative Strength Index", true),

    /**
     * Moving Average Convergence Divergence.
     * Uses fast period (12), slow period (26), signal period (9).
     * The value returned is the MACD line.
     */
    MACD("MACD", "MACD Line", false),

    /** MACD Signal line. Companion to MACD. */
    MACD_SIGNAL("MACD Signal", "MACD Signal Line", false),

    // ── Volatility ──────────────────────────────────────────────────────────
    /** Bollinger Band upper band. Period required. */
    BOLLINGER_UPPER("BB Upper", "Bollinger Bands Upper Band", true),

    /** Bollinger Band lower band. Period required. */
    BOLLINGER_LOWER("BB Lower", "Bollinger Bands Lower Band", true),

    /** Bollinger Band middle band (= SMA). Period required. */
    BOLLINGER_MID("BB Mid", "Bollinger Bands Middle Band", true),

    /** Average True Range. Period required. */
    ATR("ATR", "Average True Range", true),

    // ── Volume ──────────────────────────────────────────────────────────────
    /** Current bar volume. */
    VOLUME("Volume", "Current Bar Volume", false),

    /** Volume moving average. Period required. */
    VOLUME_MA("Volume MA", "Volume Moving Average", true),

    // ── Price Action ────────────────────────────────────────────────────────
    /** Current bar close price. */
    PRICE_CLOSE("Close", "Current Close Price", false),

    /** Current bar open price. */
    PRICE_OPEN("Open", "Current Open Price", false),

    /** Current bar high price. */
    PRICE_HIGH("High", "Current High Price", false),

    /** Current bar low price. */
    PRICE_LOW("Low", "Current Low Price", false);

    /** Short display label used in UI dropdowns. */
    public final String displayName;

    /** Full description shown in tooltips. */
    public final String description;

    /** Whether this indicator requires a {@code period} parameter. */
    public final boolean requiresPeriod;

    NoCodeIndicatorType(String displayName, String description, boolean requiresPeriod) {
        this.displayName = displayName;
        this.description = description;
        this.requiresPeriod = requiresPeriod;
    }

    /** @return default period for this indicator (0 if period not applicable). */
    public int defaultPeriod() {
        return switch (this) {
            case RSI -> 14;
            case SMA, BOLLINGER_UPPER, BOLLINGER_LOWER, BOLLINGER_MID, ATR, VOLUME_MA -> 20;
            case EMA -> 20;
            case MACD, MACD_SIGNAL -> 0;
            default -> 0;
        };
    }

    /** Minimum warmup bars this indicator needs to produce a valid value. */
    public int minWarmupBars(int period) {
        return switch (this) {
            case MACD -> 35;  // slow(26) + signal(9)
            case MACD_SIGNAL -> 35;
            case ATR -> period + 1;
            default -> period > 0 ? period : 1;
        };
    }
}
