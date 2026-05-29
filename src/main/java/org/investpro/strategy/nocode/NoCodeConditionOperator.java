package org.investpro.strategy.nocode;

/**
 * Comparison operators used in no-code strategy conditions.
 *
 * <p>Each operator defines how the left-hand side indicator value is compared
 * to the right-hand side (a numeric constant or another indicator value).</p>
 *
 * <p>{@link #CROSSES_ABOVE} and {@link #CROSSES_BELOW} require two consecutive
 * bar values and are evaluated using the previous and current bar.</p>
 */
public enum NoCodeConditionOperator {

    /** Left-side value is strictly greater than right-side value. */
    GREATER_THAN(">", "greater than"),

    /** Left-side value is strictly less than right-side value. */
    LESS_THAN("<", "less than"),

    /** Left-side crosses above right-side between previous and current bar. */
    CROSSES_ABOVE("crosses above", "crosses above"),

    /** Left-side crosses below right-side between previous and current bar. */
    CROSSES_BELOW("crosses below", "crosses below"),

    /** Left-side equals right-side (within floating-point epsilon). */
    EQUALS("=", "equals"),

    /**
     * Left-side is between two values.
     * Requires {@code rightValue} = lower bound and {@code rightValue2} = upper bound
     * on the {@link NoCodeCondition}.
     */
    BETWEEN("between", "is between"),

    /** Left-side is greater than or equal to right-side. */
    GREATER_THAN_OR_EQUAL(">=", "greater than or equal to"),

    /** Left-side is less than or equal to right-side. */
    LESS_THAN_OR_EQUAL("<=", "less than or equal to");

    /** Symbol or short text used in UI condition display. */
    public final String symbol;

    /** Natural-language description for the Preview panel. */
    public final String description;

    NoCodeConditionOperator(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    /**
     * Returns whether this operator compares across two time periods
     * (requires previous-bar values).
     *
     * @return true for CROSSES_ABOVE and CROSSES_BELOW
     */
    public boolean requiresPreviousBar() {
        return this == CROSSES_ABOVE || this == CROSSES_BELOW;
    }

    /**
     * Returns whether this operator requires two right-hand side bounds.
     *
     * @return true for BETWEEN
     */
    public boolean requiresTwoBounds() {
        return this == BETWEEN;
    }
}
