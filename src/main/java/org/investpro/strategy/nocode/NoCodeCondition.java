package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * A single comparison between an indicator reference and a value (or another indicator).
 *
 * <p>Examples:
 * <pre>
 *   RSI(14)  &lt;  30        → leftIndicator=RSI(14),  operator=LESS_THAN,     rightValue=30
 *   Close    &gt;  EMA(200)  → leftIndicator=Close,     operator=GREATER_THAN,  rightIndicator=EMA(200)
 *   RSI(14)  BETWEEN 30 and 70 → leftIndicator=RSI(14), operator=BETWEEN, rightValue=30, rightValue2=70
 * </pre>
 * </p>
 *
 * <p>Either {@code rightValue} or {@code rightIndicator} must be set, not both.
 * When {@link NoCodeConditionOperator#requiresTwoBounds()} is true,
 * both {@code rightValue} and {@code rightValue2} are required.</p>
 */
@Getter
@Builder
@ToString
public class NoCodeCondition {

    /** Left-hand side: the indicator being tested. Always required. */
    private final NoCodeIndicatorReference leftIndicator;

    /** Comparison operator. Always required. */
    private final NoCodeConditionOperator operator;

    /**
     * Right-hand side as a numeric constant.
     * Set to {@link Double#NaN} when {@code rightIndicator} is used instead.
     */
    @Builder.Default
    private final double rightValue = Double.NaN;

    /**
     * Upper bound for {@link NoCodeConditionOperator#BETWEEN}.
     * Ignored for all other operators.
     */
    @Builder.Default
    private final double rightValue2 = Double.NaN;

    /**
     * Right-hand side as another indicator (for cross-indicator comparisons).
     * Null when {@code rightValue} is used instead.
     */
    private final NoCodeIndicatorReference rightIndicator;

    /** Optional human-readable label shown in the Preview panel. */
    private final String label;

    // =========================================================================
    // Convenience
    // =========================================================================

    /** @return true if the right-hand side is a constant numeric value. */
    public boolean hasConstantRight() {
        return !Double.isNaN(rightValue) && rightIndicator == null;
    }

    /** @return true if the right-hand side is another indicator. */
    public boolean hasIndicatorRight() {
        return rightIndicator != null;
    }

    /**
     * Returns the maximum warmup bars required by this condition
     * (max of left and right indicator warmup requirements).
     *
     * @return warmup bars
     */
    public int requiredWarmupBars() {
        int left = leftIndicator != null ? leftIndicator.requiredWarmupBars() : 1;
        int right = rightIndicator != null ? rightIndicator.requiredWarmupBars() : 0;
        return Math.max(left, right);
    }

    /**
     * Returns a natural-language description of this condition for the Preview panel.
     *
     * @return e.g. "RSI(14) < 30", "Close > EMA(200)"
     */
    public String toPreviewText() {
        String leftStr = leftIndicator != null ? leftIndicator.toLabel() : "?";
        String rightStr = hasIndicatorRight()
                ? rightIndicator.toLabel()
                : (hasConstantRight() ? String.valueOf((int) rightValue) : "?");
        if (operator == NoCodeConditionOperator.BETWEEN && !Double.isNaN(rightValue2)) {
            return leftStr + " between " + (int) rightValue + " and " + (int) rightValue2;
        }
        return leftStr + " " + operator.symbol + " " + rightStr;
    }
}
