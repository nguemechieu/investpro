package org.investpro.strategy.auto;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.lab.StrategyPerformanceReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyAssignmentEngineTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("autoStrategy.minImprovementScore");
        System.clearProperty("autoStrategy.maxDrawdownPercent");
        System.clearProperty("autoStrategy.allowLiveAutoAssignment");
    }

    @Test
    void refusesCandidateBelowImprovementThreshold() {
        System.setProperty("autoStrategy.minImprovementScore", "10.0");
        StrategyAssignmentDecision decision = new StrategyAssignmentEngine().decide(
                result("Small Improvement", 5.0, 0.05, true),
                null,
                context(),
                true);

        assertThat(decision.assigned()).isFalse();
        assertThat(decision.reason()).contains("improvement score");
    }

    @Test
    void refusesCandidateAboveDrawdownThreshold() {
        System.setProperty("autoStrategy.minImprovementScore", "1.0");
        System.setProperty("autoStrategy.maxDrawdownPercent", "15.0");
        StrategyAssignmentDecision decision = new StrategyAssignmentEngine().decide(
                result("High Drawdown", 50.0, 0.30, true),
                null,
                context(),
                true);

        assertThat(decision.assigned()).isFalse();
        assertThat(decision.reason()).contains("drawdown");
    }

    private StrategyEvaluationResult result(String name, double score, double maxDrawdown, boolean passed) {
        StrategyCandidate candidate = new StrategyCandidate(
                name,
                StrategyDefinition.builder().name(name).baseName(name).build(),
                StrategyGenerationSource.RULE_BASED,
                "BTC/USD",
                MarketRegime.UNKNOWN,
                score,
                List.of(),
                Instant.now());
        StrategyPerformanceReport report = StrategyPerformanceReport.builder()
                .strategyName(name)
                .baseStrategyName(name)
                .symbol("BTC/USD")
                .timeframe(Timeframe.H1)
                .maxDrawdown(maxDrawdown)
                .build();
        return new StrategyEvaluationResult(candidate, report, report, score, passed, List.of(), List.of());
    }

    private StrategyGenerationContext context() {
        return new StrategyGenerationContext("BTC/USD", Timeframe.H1, List.of(), MarketRegime.UNKNOWN, RiskProfile.conservative(), "");
    }
}
