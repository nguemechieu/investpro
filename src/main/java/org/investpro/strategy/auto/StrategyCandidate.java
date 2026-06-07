package org.investpro.strategy.auto;

import org.investpro.strategy.StrategyDefinition;

import java.time.Instant;
import java.util.List;

public record StrategyCandidate(
        String id,
        StrategyDefinition strategyDefinition,
        StrategyGenerationSource source,
        String symbol,
        MarketRegime marketRegime,
        double generationScore,
        List<String> rationale,
        Instant generatedAt) {
}
