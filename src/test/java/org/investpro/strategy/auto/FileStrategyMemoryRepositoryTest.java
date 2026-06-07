package org.investpro.strategy.auto;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileStrategyMemoryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsCandidatesEvaluationsAndDecisions() {
        Path memoryPath = tempDir.resolve("auto-memory.json");
        FileStrategyMemoryRepository repository = new FileStrategyMemoryRepository(memoryPath);
        StrategyCandidate candidate = candidate("BTC/USD");
        StrategyEvaluationResult evaluation = new StrategyEvaluationResult(
                candidate,
                null,
                null,
                42.0,
                false,
                List.of("warning"),
                List.of("not enough trades"));
        StrategyAssignmentDecision decision = new StrategyAssignmentDecision(
                "BTC/USD",
                "current",
                "candidate",
                false,
                false,
                "candidate",
                10.0,
                42.0,
                "Awaiting user approval before assignment.",
                List.of("paper required"),
                null);

        repository.saveCandidate(candidate);
        repository.saveEvaluation(evaluation);
        repository.saveDecision(decision);

        FileStrategyMemoryRepository reloaded = new FileStrategyMemoryRepository(memoryPath);

        assertThat(reloaded.candidatesFor("BTC/USD")).hasSize(1);
        assertThat(reloaded.candidatesFor("BTC/USD").getFirst().strategyDefinition().getRules()).hasSize(1);
        assertThat(reloaded.evaluationsFor("BTC/USD")).hasSize(1);
        assertThat(reloaded.evaluationsFor("BTC/USD").getFirst().errors()).contains("not enough trades");
        assertThat(reloaded.decisionsFor("BTC/USD")).hasSize(1);
        assertThat(reloaded.decisionsFor("BTC/USD").getFirst().reason()).contains("Awaiting user approval");
    }

    private StrategyCandidate candidate(String symbol) {
        StrategyDefinition definition = StrategyDefinition.builder()
                .name("Memory Candidate")
                .baseName("Memory Candidate")
                .rules(List.of(new StrategyRuleDefinition(
                        StrategyRuleSource.INDICATOR,
                        SignalType.BUY,
                        INDICATORS.RSI,
                        null,
                        Timeframe.H1,
                        Map.of("period", "14", "oversold", "30", "overbought", "70"))))
                .build();
        return new StrategyCandidate(
                "candidate-1",
                definition,
                StrategyGenerationSource.RULE_BASED,
                symbol,
                MarketRegime.RANGING,
                50.0,
                List.of("test"),
                Instant.now());
    }
}
