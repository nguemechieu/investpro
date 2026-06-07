package org.investpro.strategy.auto;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyMutationEngineTest {

    @Test
    void createsBoundedControlledParameterVariations() {
        StrategyDefinition definition = StrategyDefinition.builder()
                .name("Base RSI")
                .baseName("Base RSI")
                .rules(List.of(new StrategyRuleDefinition(
                        StrategyRuleSource.INDICATOR,
                        SignalType.BUY,
                        INDICATORS.RSI,
                        null,
                        Timeframe.H1,
                        Map.of("period", "14", "oversold", "30", "overbought", "70"))))
                .build();
        StrategyGenerationContext context = new StrategyGenerationContext(
                "BTC/USD",
                Timeframe.H1,
                List.of(),
                MarketRegime.RANGING,
                RiskProfile.conservative(),
                "");

        List<StrategyCandidate> mutations = new StrategyMutationEngine().mutate(definition, context);

        assertThat(mutations).isNotEmpty();
        assertThat(mutations).allMatch(candidate -> candidate.source() == StrategyGenerationSource.MUTATION);
        assertThat(mutations)
                .flatExtracting(candidate -> candidate.strategyDefinition().getRules())
                .anySatisfy(rule -> assertThat(rule.parameters()).containsEntry("period", "10"));
        assertThat(mutations)
                .flatExtracting(candidate -> candidate.strategyDefinition().getRules())
                .anySatisfy(rule -> assertThat(rule.parameters()).containsEntry("period", "21"));
    }
}
