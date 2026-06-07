package org.investpro.strategy.auto;

import org.investpro.indicators.metadata.IndicatorDefinitionRegistry;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StrategyCandidateValidator {

    public StrategyValidationResult validate(StrategyCandidate candidate) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (candidate == null || candidate.strategyDefinition() == null) {
            errors.add("Strategy candidate definition is required.");
            return StrategyValidationResult.invalid(errors, warnings);
        }
        if (blank(candidate.strategyDefinition().getName())) {
            errors.add("Strategy name is required.");
        }
        List<StrategyRuleDefinition> rules = candidate.strategyDefinition().getRules();
        if (rules == null || rules.isEmpty()) {
            errors.add("Strategy must contain at least one rule.");
        } else {
            for (StrategyRuleDefinition rule : rules) {
                validateRule(rule, errors, warnings);
            }
        }
        return errors.isEmpty()
                ? StrategyValidationResult.valid(warnings)
                : StrategyValidationResult.invalid(errors, warnings);
    }

    private void validateRule(StrategyRuleDefinition rule, List<String> errors, List<String> warnings) {
        if (rule == null) {
            errors.add("Strategy contains an empty rule.");
            return;
        }
        if (rule.signalType() == null) {
            errors.add("Rule signal type is required.");
        }
        if (rule.timeframe() == null) {
            errors.add("Rule timeframe is required.");
        }
        if (rule.ruleSource() == StrategyRuleSource.INDICATOR) {
            if (rule.indicator() == null) {
                errors.add("Indicator rule is missing an indicator.");
                return;
            }
            IndicatorDefinitionRegistry.get(rule.indicator()).parameters()
                    .forEach(parameter -> validateParameter(rule.parameters(), parameter.name(), parameter.type(), errors, warnings));
        } else if (rule.ruleSource() == StrategyRuleSource.CANDLE_PATTERN && rule.candlePattern() == null) {
            errors.add("Candle pattern rule is missing a pattern.");
        }
    }

    private void validateParameter(
            Map<String, String> parameters,
            String parameterName,
            String type,
            List<String> errors,
            List<String> warnings) {
        String value = parameters == null ? null : parameters.get(parameterName);
        if (value == null || value.isBlank()) {
            warnings.add("Missing parameter " + parameterName + "; default will be used when available.");
            return;
        }
        if ("INTEGER".equalsIgnoreCase(type)) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                errors.add("Parameter " + parameterName + " must be an integer.");
            }
        } else if ("DOUBLE".equalsIgnoreCase(type)) {
            try {
                Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                errors.add("Parameter " + parameterName + " must be a decimal number.");
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
