package org.investpro.strategy.auto;

import org.investpro.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyCandidateValidatorTest {

    @Test
    void rejectsCandidateWithNoRules() {
        StrategyCandidate candidate = new StrategyCandidate(
                "c1",
                StrategyDefinition.builder().name("No Rules").baseName("No Rules").rules(List.of()).build(),
                StrategyGenerationSource.RULE_BASED,
                "BTC/USD",
                MarketRegime.UNKNOWN,
                0.0,
                List.of(),
                Instant.now());

        StrategyValidationResult result = new StrategyCandidateValidator().validate(candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Strategy must contain at least one rule.");
    }
}
