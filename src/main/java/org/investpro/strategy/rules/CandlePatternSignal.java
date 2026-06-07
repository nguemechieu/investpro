package org.investpro.strategy.rules;

public record CandlePatternSignal(
        CandlePattern pattern,
        SignalType signalType,
        int candleIndex,
        double confidence,
        String reason) {
}
