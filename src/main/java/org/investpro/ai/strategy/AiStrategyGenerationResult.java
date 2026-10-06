package org.investpro.ai.strategy;

import org.investpro.strategy.StrategyDefinition;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

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

    @Contract("_, _ -> new")
    public static @NonNull AiStrategyGenerationResult failure(String error, BigDecimal estimatedCost) {
        return new AiStrategyGenerationResult(false, null, "", List.of(), List.of(error), estimatedCost, BigDecimal.ZERO);
    }
}
