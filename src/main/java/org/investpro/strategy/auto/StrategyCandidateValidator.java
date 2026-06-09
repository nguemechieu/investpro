package org.investpro.strategy.auto;

import org.investpro.indicators.IndicatorCatalog;
import org.investpro.indicators.metadata.IndicatorDefinition;
import org.investpro.indicators.metadata.IndicatorParameterDefinition;
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

    private void validateRule(
            StrategyRuleDefinition rule,
            List<String> errors,
            List<String> warnings
    ) {
        if (rule == null) {
            errors.add("Strategy contains an empty rule.");
            return;
        }

        if (rule.ruleSource() == null) {
            errors.add("Rule source is required.");
            return;
        }

        if (rule.signalType() == null) {
            errors.add("Rule signal type is required.");
        }

        if (rule.timeframe() == null) {
            errors.add("Rule timeframe is required.");
        }

        if (rule.ruleSource() == StrategyRuleSource.INDICATOR) {
            validateIndicatorRule(rule, errors, warnings);
            return;
        }

        if (rule.ruleSource() == StrategyRuleSource.CANDLE_PATTERN) {
            validateCandlePatternRule(rule, errors);
        }
    }

    private void validateIndicatorRule(
            StrategyRuleDefinition rule,
            List<String> errors,
            List<String> warnings
    ) {
        if (rule.indicator() == null) {
            errors.add("Indicator rule is missing an indicator.");
            return;
        }

        IndicatorDefinition definition = IndicatorCatalog.get(rule.indicator());

        if (definition == null) {
            errors.add("No indicator definition found for " + rule.indicator() + ".");
            return;
        }

        List<IndicatorParameterDefinition> parameterDefinitions = definition.parameters();

        if (parameterDefinitions == null || parameterDefinitions.isEmpty()) {
            return;
        }

        Map<String, String> parameters = rule.parameters();

        for (IndicatorParameterDefinition parameter : parameterDefinitions) {
            if (parameter == null || blank(parameter.name())) {
                continue;
            }

            validateParameter(
                    parameters,
                    parameter.name(),
                    parameter.type(),
                    parameter.required(),
                    errors,
                    warnings
            );
        }
    }

    private void validateCandlePatternRule(
            StrategyRuleDefinition rule,
            List<String> errors
    ) {
        if (rule.candlePattern() == null) {
            errors.add("Candle pattern rule is missing a pattern.");
        }
    }

    private void validateParameter(
            Map<String, String> parameters,
            String parameterName,
            String type,
            boolean required,
            List<String> errors,
            List<String> warnings
    ) {
        String value = parameters == null ? null : parameters.get(parameterName);

        if (value == null || value.isBlank()) {
            if (required) {
                warnings.add("Missing required parameter '" + parameterName + "'; default will be used when available.");
            } else {
                warnings.add("Missing optional parameter '" + parameterName + "'; default will be used when available.");
            }
            return;
        }

        if ("INTEGER".equalsIgnoreCase(type)) {
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException exception) {
                errors.add("Parameter '" + parameterName + "' must be an integer.");
            }
            return;
        }

        if ("DOUBLE".equalsIgnoreCase(type)) {
            try {
                Double.parseDouble(value.trim());
            } catch (NumberFormatException exception) {
                errors.add("Parameter '" + parameterName + "' must be a decimal number.");
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}