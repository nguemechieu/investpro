package org.investpro.strategy.rules;

public record IndicatorCondition(
        String outputKey,
        IndicatorConditionOperator operator,
        Double compareValue,
        String compareOutputKey,
        SignalType signalType) {
}
