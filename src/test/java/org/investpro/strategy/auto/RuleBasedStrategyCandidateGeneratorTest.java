package org.investpro.strategy.auto;

import org.investpro.enums.timeframe.Timeframe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedStrategyCandidateGeneratorTest {

    @Test
    void generatesValidRuleBasedCandidates() {
        StrategyGenerationContext context = new StrategyGenerationContext(
                "BTC/USD",
                Timeframe.H1,
                List.of(),
                MarketRegime.TRENDING_UP,
                RiskProfile.conservative(),
                "");

        List<StrategyCandidate> candidates = new RuleBasedStrategyCandidateGenerator().generateCandidates(context);
        StrategyCandidateValidator validator = new StrategyCandidateValidator();

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(candidate ->
                assertThat(validator.validate(candidate).valid()).isTrue());
    }
}
