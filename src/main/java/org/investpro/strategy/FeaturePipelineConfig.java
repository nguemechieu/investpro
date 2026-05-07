package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for feature pipeline computation.
 * <p>
 * Defines the technical indicator periods to use when computing market
 * features.
 */
@Getter
@Builder
public class FeaturePipelineConfig {
    private final int rsiPeriod;
    private final int emaFast;
    private final int emaSlow;
    private final int atrPeriod;
    private final int breakoutLookback;

    /**
     * Create a pipeline config from strategy parameters.
     *
     * @param parameters The strategy parameters
     * @return A new pipeline configuration
     */
    public static @NotNull FeaturePipelineConfig from(@NotNull StrategyParameters parameters) {
        return FeaturePipelineConfig.builder()
                .rsiPeriod(parameters.getRsiPeriod())
                .emaFast(parameters.getEmaFast())
                .emaSlow(parameters.getEmaSlow())
                .atrPeriod(parameters.getAtrPeriod())
                .breakoutLookback(parameters.getBreakoutLookback())
                .build();
    }

    @Override
    public String toString() {
        return "FeaturePipelineConfig{" +
                "rsiPeriod=" + rsiPeriod +
                ", emaFast=" + emaFast +
                ", emaSlow=" + emaSlow +
                ", atrPeriod=" + atrPeriod +
                ", breakoutLookback=" + breakoutLookback +
                '}';
    }
}
