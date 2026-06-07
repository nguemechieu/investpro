package org.investpro.strategy.auto;

import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.config.AppConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StrategyMutationEngine {

    public List<StrategyCandidate> mutate(StrategyDefinition definition, StrategyGenerationContext context) {
        if (definition == null || definition.getRules() == null || definition.getRules().isEmpty()) {
            return List.of();
        }
        List<StrategyCandidate> mutations = new ArrayList<>();
        addMutation(mutations, definition, context, "RSI More Sensitive", Map.of("period", "10", "oversold", "25", "overbought", "75"));
        addMutation(mutations, definition, context, "RSI Smoother", Map.of("period", "21", "oversold", "35", "overbought", "65"));
        addMutation(mutations, definition, context, "EMA Faster", Map.of("fastPeriod", "9", "emaFast", "9", "period", "9"));
        addMutation(mutations, definition, context, "EMA Slower", Map.of("slowPeriod", "34", "emaSlow", "34", "period", "34"));
        addMutation(mutations, definition, context, "ATR Tight", Map.of("atrPeriod", "10", "period", "10"));
        addMutation(mutations, definition, context, "ATR Wide", Map.of("atrPeriod", "20", "period", "20"));
        addMutation(mutations, definition, context, "Bollinger Tight", Map.of("stdDevMult", "1.8"));
        addMutation(mutations, definition, context, "Bollinger Wide", Map.of("stdDevMult", "2.2"));
        int limit = Math.max(1, AppConfig.getInt("autoStrategy.maxCandidatesPerSymbol", 20));
        return mutations.stream().limit(limit).toList();
    }

    private void addMutation(
            List<StrategyCandidate> mutations,
            StrategyDefinition definition,
            StrategyGenerationContext context,
            String suffix,
            Map<String, String> replacements) {
        StrategyCandidate mutation = mutation(definition, context, suffix, replacements);
        if (mutation.strategyDefinition().getRules().stream().anyMatch(rule -> !rule.parameters().equals(
                definition.getRules().stream()
                        .filter(original -> original.id().equals(rule.id()))
                        .findFirst()
                        .map(StrategyRuleDefinition::parameters)
                        .orElse(Map.of())))) {
            mutations.add(mutation);
        }
    }

    private StrategyCandidate mutation(
            StrategyDefinition definition,
            StrategyGenerationContext context,
            String suffix,
            Map<String, String> replacements) {
        List<StrategyRuleDefinition> rules = definition.getRules().stream()
                .map(rule -> new StrategyRuleDefinition(
                        rule.id(),
                        rule.ruleSource(),
                        rule.signalType(),
                        rule.indicator(),
                        rule.candlePattern(),
                        rule.timeframe(),
                        mutateParameters(rule.parameters(), replacements),
                        rule.conditions(),
                        rule.enabled()))
                .toList();
        StrategyDefinition mutated = StrategyDefinition.builder()
                .name(definition.getName() + " " + suffix)
                .baseName(definition.getBaseName())
                .parameters(definition.getParameters())
                .rules(rules)
                .marketCompatibility(definition.getMarketCompatibility())
                .build();
        return new StrategyCandidate(
                UUID.randomUUID().toString(),
                mutated,
                StrategyGenerationSource.MUTATION,
                context == null ? "UNKNOWN" : context.symbol(),
                context == null ? MarketRegime.UNKNOWN : context.marketRegime(),
                45.0,
                List.of("Mutated parameters from " + definition.getName()),
                Instant.now());
    }

    private Map<String, String> mutateParameters(Map<String, String> parameters, Map<String, String> replacements) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, String> mutated = new LinkedHashMap<>(parameters);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            if (mutated.containsKey(replacement.getKey())) {
                mutated.put(replacement.getKey(), replacement.getValue());
            }
        }
        return mutated;
    }
}
