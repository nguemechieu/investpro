package org.investpro.strategy.engine;

import org.investpro.strategy.model.SignalAction;
import org.investpro.strategy.model.StrategyDecision;
import org.investpro.strategy.model.TradingSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalFusionEngineTest {
    @Test
    void weightedVoteSelectsHighestNormalizedAction() {
        StrategyDecision decision = new SignalFusionEngine(0.55).fuse(List.of(
                TradingSignal.buy("a", "A", 0.8, 2.0, "buy"),
                TradingSignal.sell("b", "B", 0.4, 1.0, "sell"),
                TradingSignal.hold("c", "C", "flat")
        ));

        assertThat(decision.action()).isEqualTo(SignalAction.BUY);
        assertThat(decision.supportingSignals()).hasSize(1);
        assertThat(decision.opposingSignals()).hasSize(1);
        assertThat(decision.diagnostics()).containsKeys("buyScore", "sellScore", "totalScore", "selectedAction");
    }
}
