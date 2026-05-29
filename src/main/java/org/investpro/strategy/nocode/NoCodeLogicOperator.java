package org.investpro.strategy.nocode;

/**
 * Logical combiner for multiple conditions within a single no-code strategy rule.
 *
 * <p>When a {@link NoCodeRule} has multiple conditions, {@code AND} requires
 * all conditions to be true, while {@code OR} requires at least one to be true.</p>
 */
public enum NoCodeLogicOperator {
    /** All conditions must be satisfied for the rule to trigger. */
    AND("AND"),

    /** At least one condition must be satisfied for the rule to trigger. */
    OR("OR");

    /** Display text shown between conditions in the Preview panel. */
    public final String displayText;

    NoCodeLogicOperator(String displayText) {
        this.displayText = displayText;
    }
}
