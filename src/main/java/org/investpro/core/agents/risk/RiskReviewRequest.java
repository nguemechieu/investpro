package org.investpro.core.agents.risk;

import lombok.Builder;
import lombok.Getter;
import org.investpro.strategy.StrategySignal;

import java.util.Map;

@Getter
@Builder
public class RiskReviewRequest {
    private final String symbol;
    private final String decisionId;
    private final StrategySignal signal;
    private final Object dataset;
    private final String timeframe;
    private final Object regimeSnapshot;
    private final Object portfolioSnapshot;

    @Builder.Default
    private final Map<String, Object> context = Map.of();
}
