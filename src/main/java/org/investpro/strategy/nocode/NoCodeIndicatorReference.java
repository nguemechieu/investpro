package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * A reference to a technical indicator used on the left or right side of a
 * no-code {@link NoCodeCondition}.
 *
 * <p>Example: {@code EMA(200)} would be represented as
 * {@code NoCodeIndicatorReference{type=EMA, period=200}}.</p>
 *
 * <p>Extra parameters (e.g. Bollinger Band standard deviation multiplier)
 * are stored in the {@code params} map with string keys.</p>
 */
@Getter
@Builder
@ToString
public class NoCodeIndicatorReference {

    /** Which indicator to compute. */
    private final NoCodeIndicatorType type;

    /**
     * The look-back period. Ignored for indicators where
     * {@link NoCodeIndicatorType#requiresPeriod()} is false.
     */
    @Builder.Default
    private final int period = 0;

    /**
     * Extra parameters keyed by name. Examples:
     * <ul>
     *   <li>{@code "stddev"} → "2.0" for Bollinger Bands</li>
     *   <li>{@code "fast"} → "12", {@code "slow"} → "26" for MACD</li>
     * </ul>
     */
    private final Map<String, String> params;

    /**
     * Computes the effective period, using the indicator's default when period is 0.
     *
     * @return effective period (always ≥ 1)
     */
    public int effectivePeriod() {
        if (period > 0) return period;
        int def = type.defaultPeriod();
        return def > 0 ? def : 1;
    }

    /**
     * Returns the minimum warmup bars required for this indicator reference.
     *
     * @return warmup bars
     */
    public int requiredWarmupBars() {
        return type.minWarmupBars(effectivePeriod());
    }

    /**
     * Returns a human-readable label for the Preview panel.
     *
     * @return e.g. "EMA(200)", "RSI(14)", "Close"
     */
    public String toLabel() {
        if (type.requiresPeriod && effectivePeriod() > 0) {
            return type.displayName + "(" + effectivePeriod() + ")";
        }
        return type.displayName;
    }
}
