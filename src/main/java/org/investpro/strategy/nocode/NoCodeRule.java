package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.util.List;

/**
 * A single rule within a no-code strategy definition.
 *
 * <p>A rule fires when its conditions are met (evaluated using the
 * {@link #logicOperator}) and emits the specified {@link #action}.
 * Entry rules fire to open positions; exit rules fire to close them.</p>
 *
 * <p>A {@link NoCodeStrategyDefinition} has separate lists of entry and exit rules.
 * The runtime evaluates entry rules first; if none fire, it evaluates exit rules.</p>
 *
 * <p><strong>CRITICAL:</strong> The action only generates a signal. It never
 * submits an order. All signals flow through the InvestPro risk and execution pipeline.</p>
 */
@Getter
@Builder
@ToString
public class NoCodeRule {

    /**
     * Optional label shown in the UI rule list (e.g. "RSI Oversold Entry").
     * Auto-generated from conditions if blank.
     */
    private final String label;

    /**
     * The conditions that must be evaluated to determine whether this rule fires.
     * Must contain at least one condition.
     */
    @Singular
    private final List<NoCodeCondition> conditions;

    /**
     * How multiple conditions are combined.
     * Default: AND (all must be true).
     */
    @Builder.Default
    private final NoCodeLogicOperator logicOperator = NoCodeLogicOperator.AND;

    /**
     * The action to emit when conditions are satisfied.
     * Default: HOLD (no action).
     */
    @Builder.Default
    private final NoCodeAction action = NoCodeAction.HOLD;

    /**
     * Confidence value (0.0–1.0) emitted with the signal when this rule fires.
     * Higher = stronger conviction.
     */
    @Builder.Default
    private final double confidence = 0.7;

    // =========================================================================
    // Convenience
    // =========================================================================

    /**
     * Returns the maximum warmup bars required by any condition in this rule.
     *
     * @return warmup bars
     */
    public int requiredWarmupBars() {
        if (conditions == null || conditions.isEmpty()) return 0;
        return conditions.stream().mapToInt(NoCodeCondition::requiredWarmupBars).max().orElse(0);
    }

    /**
     * Generates a natural-language preview of this rule for the UI.
     *
     * @return e.g. "IF RSI(14) < 30 AND Close > EMA(200) THEN BUY"
     */
    public String toPreviewText() {
        if (conditions == null || conditions.isEmpty()) {
            return "(no conditions) THEN " + action.displayName;
        }
        String sep = " " + logicOperator.displayText + " ";
        StringBuilder sb = new StringBuilder("IF ");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(conditions.get(i).toPreviewText());
        }
        sb.append(" THEN ").append(action.displayName);
        return sb.toString();
    }
}
