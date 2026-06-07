package org.investpro.strategy.auto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRankingEngineTest {

    @Test
    void highestScoreRanksFirst() {
        StrategyEvaluationResult low = new StrategyEvaluationResult(null, null, null, 12.0, true, List.of(), List.of());
        StrategyEvaluationResult high = new StrategyEvaluationResult(null, null, null, 42.0, true, List.of(), List.of());

        assertThat(new StrategyRankingEngine().rank(List.of(low, high)).getFirst()).isSameAs(high);
    }
}
