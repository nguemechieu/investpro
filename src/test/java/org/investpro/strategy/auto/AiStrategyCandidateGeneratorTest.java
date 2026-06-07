package org.investpro.strategy.auto;

import org.investpro.ai.AiStrategyGenerationResult;
import org.investpro.enums.timeframe.Timeframe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiStrategyCandidateGeneratorTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("ai.strategyGeneration.enabled");
    }

    @Test
    void disabledByDefaultCreatesNoCandidates() {
        AiStrategyCandidateGenerator generator = new AiStrategyCandidateGenerator(request ->
                AiStrategyGenerationResult.failure("should not be called", BigDecimal.ZERO));

        List<StrategyCandidate> candidates = generator.generateCandidates(new StrategyGenerationContext(
                "BTC/USD",
                Timeframe.H1,
                List.of(),
                MarketRegime.UNKNOWN,
                RiskProfile.conservative(),
                "make strategy"));

        assertThat(candidates).isEmpty();
    }
}
