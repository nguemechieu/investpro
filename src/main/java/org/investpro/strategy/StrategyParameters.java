package org.investpro.strategy;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder(toBuilder = true)
public class StrategyParameters {

    @Builder.Default
    private final int rsiPeriod = 14;

    @Builder.Default
    private final int emaFast = 20;

    @Builder.Default
    private final int emaSlow = 50;

    @Builder.Default
    private final int atrPeriod = 14;

    @Builder.Default
    private final int breakoutLookback = 20;

    @Builder.Default
    private final double oversoldThreshold = 35.0;

    @Builder.Default
    private final double overboughtThreshold = 65.0;

    @Builder.Default
    private final double minConfidence = 0.55;

    @Builder.Default
    private final double signalAmount = 1.0;

    public StrategyParameters merge(StrategyParameters override) {
        if (override == null) {
            return this;
        }

        return this.toBuilder()
                .rsiPeriod(override.getRsiPeriod())
                .emaFast(override.getEmaFast())
                .emaSlow(override.getEmaSlow())
                .atrPeriod(override.getAtrPeriod())
                .breakoutLookback(override.getBreakoutLookback())
                .oversoldThreshold(override.getOversoldThreshold())
                .overboughtThreshold(override.getOverboughtThreshold())
                .minConfidence(override.getMinConfidence())
                .signalAmount(override.getSignalAmount())
                .build();
    }
}