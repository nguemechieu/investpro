package org.investpro.strategy.rules;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StrategyRuleDefinition(
        String id,
        StrategyRuleSource ruleSource,
        SignalType signalType,
        INDICATORS indicator,
        CandlePattern candlePattern,
        Timeframe timeframe,
        Map<String, String> parameters,
        List<IndicatorCondition> conditions,
        boolean enabled) {

    public StrategyRuleDefinition(
            StrategyRuleSource ruleSource,
            SignalType signalType,
            INDICATORS indicator,
            CandlePattern candlePattern,
            Timeframe timeframe,
            Map<String, String> parameters) {
        this(UUID.randomUUID().toString(),
                ruleSource,
                signalType,
                indicator,
                candlePattern,
                timeframe,
                parameters == null ? Map.of() : Map.copyOf(parameters),
                List.of(),
                true);
    }
}
