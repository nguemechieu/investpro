package org.investpro.strategy.nocode;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Validates a {@link NoCodeStrategyDefinition} before it is compiled or executed.
 *
 * <p>Validation checks include:
 * <ul>
 *   <li>Strategy name is not blank</li>
 *   <li>At least one entry rule exists</li>
 *   <li>Each entry rule has at least one condition</li>
 *   <li>Each condition has a valid left indicator</li>
 *   <li>Indicators that require a period have a valid period (≥1)</li>
 *   <li>BETWEEN operator has both lower and upper bounds</li>
 *   <li>At least one exit or risk rule is defined</li>
 *   <li>Risk settings values are non-negative</li>
 * </ul>
 * </p>
 *
 * <p>Warnings (non-blocking) are emitted for:
 * <ul>
 *   <li>No description provided</li>
 *   <li>Stop-loss not configured</li>
 *   <li>Very short period values that could cause excessive signals</li>
 * </ul>
 * </p>
 *
 * <p>This class is stateless and thread-safe.</p>
 */
@Slf4j
public class NoCodeStrategyValidator {

    /**
     * Validates the given strategy definition.
     *
     * @param def the strategy definition to validate (must not be {@code null})
     * @return a {@link ValidationResult} describing all errors and warnings found
     */
    public ValidationResult validate(NoCodeStrategyDefinition def) {
        if (def == null) {
            return ValidationResult.builder().error("Strategy definition must not be null.").build();
        }

        ValidationResult.Builder result = ValidationResult.builder();

        validateBasicInfo(def, result);
        validateEntryRules(def.getEntryRules(), result);
        validateExitRules(def.getExitRules(), result);
        validateRiskSettings(def.getRiskSettings(), result);

        ValidationResult finalResult = result.build();
        log.debug("Validation of strategy '{}': {}", def.getName(), finalResult);
        return finalResult;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void validateBasicInfo(NoCodeStrategyDefinition def, ValidationResult.Builder result) {
        if (def.getName() == null || def.getName().isBlank()) {
            result.error("Strategy name must not be empty.");
        }
        if (def.getDescription() == null || def.getDescription().isBlank()) {
            result.warning("No description provided for strategy.");
        }
    }

    private void validateEntryRules(List<NoCodeRule> rules, ValidationResult.Builder result) {
        if (rules == null || rules.isEmpty()) {
            result.error("At least one entry rule is required.");
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            validateRule(rules.get(i), "Entry rule #" + (i + 1), result);
        }
    }

    private void validateExitRules(List<NoCodeRule> rules, ValidationResult.Builder result) {
        if (rules == null || rules.isEmpty()) {
            result.warning("No exit rules defined. Strategy will rely on risk settings for exits.");
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            validateRule(rules.get(i), "Exit rule #" + (i + 1), result);
        }
    }

    private void validateRule(NoCodeRule rule, String context, ValidationResult.Builder result) {
        if (rule == null) {
            result.error(context + " is null.");
            return;
        }
        List<NoCodeCondition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            result.error(context + " has no conditions.");
            return;
        }
        for (int i = 0; i < conditions.size(); i++) {
            validateCondition(conditions.get(i), context + ", condition #" + (i + 1), result);
        }
        if (rule.getAction() == null) {
            result.error(context + " has no action.");
        }
    }

    private void validateCondition(NoCodeCondition cond, String context, ValidationResult.Builder result) {
        if (cond == null) {
            result.error(context + " is null.");
            return;
        }

        // Left indicator
        if (cond.getLeftIndicator() == null) {
            result.error(context + ": left indicator must not be null.");
        } else {
            validateIndicatorRef(cond.getLeftIndicator(), context + " (left)", result);
        }

        // Operator
        if (cond.getOperator() == null) {
            result.error(context + ": operator must not be null.");
            return;
        }

        // Right side
        if (cond.getOperator() == NoCodeConditionOperator.BETWEEN) {
            if (Double.isNaN(cond.getRightValue()) || Double.isNaN(cond.getRightValue2())) {
                result.error(context + ": BETWEEN operator requires both lower and upper bounds.");
            } else if (cond.getRightValue() >= cond.getRightValue2()) {
                result.error(context + ": BETWEEN lower bound must be less than upper bound.");
            }
        } else {
            boolean hasConstant = !Double.isNaN(cond.getRightValue());
            boolean hasIndicator = cond.getRightIndicator() != null;
            if (!hasConstant && !hasIndicator) {
                result.error(context + ": right-hand side must be a constant value or an indicator.");
            }
            if (hasConstant && hasIndicator) {
                result.warning(context + ": both constant and indicator set for right-hand side; indicator will take precedence.");
            }
            if (hasIndicator) {
                validateIndicatorRef(cond.getRightIndicator(), context + " (right)", result);
            }
        }
    }

    private void validateIndicatorRef(NoCodeIndicatorReference ref, String context, ValidationResult.Builder result) {
        if (ref.getType() == null) {
            result.error(context + ": indicator type must not be null.");
            return;
        }
        if (ref.getType().requiresPeriod) {
            int p = ref.effectivePeriod();
            if (p < 1) {
                result.error(context + ": " + ref.getType().displayName + " requires a period ≥ 1.");
            } else if (p < 3) {
                result.warning(context + ": very short period (" + p + ") for " + ref.getType().displayName + " may generate excessive signals.");
            }
        }
    }

    private void validateRiskSettings(NoCodeRiskSettings risk, ValidationResult.Builder result) {
        if (risk == null) {
            result.warning("No risk settings defined; platform defaults will be used.");
            return;
        }
        if (risk.getStopLossPercent() < 0) {
            result.error("Stop-loss percent must be ≥ 0.");
        } else if (risk.getStopLossPercent() == 0) {
            result.warning("Stop-loss is not configured. This increases risk.");
        }
        if (risk.getTakeProfitPercent() < 0) {
            result.error("Take-profit percent must be ≥ 0.");
        }
        if (risk.getMaxTradesPerDay() < 0) {
            result.error("Max trades per day must be ≥ 0.");
        }
        if (risk.getMaxDrawdownPercent() < 0) {
            result.error("Max drawdown percent must be ≥ 0.");
        }
        if (risk.getMaxPositionSizePercent() < 0) {
            result.error("Max position size percent must be ≥ 0.");
        }
    }
}
