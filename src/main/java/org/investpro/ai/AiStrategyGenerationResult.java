package org.investpro.ai;

import org.investpro.strategy.StrategyDefinition;

import java.math.BigDecimal;
import java.util.List;

public record AiStrategyGenerationResult(
        boolean success,
        StrategyDefinition strategyDefinition,
        String rawAiResponse,
        List<String> warnings,
        List<String> errors,
        BigDecimal estimatedCost,
        BigDecimal actualCost) {

    public static AiStrategyGenerationResult failure(String error, BigDecimal estimatedCost) {
        return new AiStrategyGenerationResult(false, null, "", List.of(), List.of(error), estimatedCost, BigDecimal.ZERO);
    }
}
