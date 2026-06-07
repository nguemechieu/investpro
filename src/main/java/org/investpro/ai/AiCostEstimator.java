package org.investpro.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AiCostEstimator {
    private AiCostEstimator() {
    }

    public static int estimatePromptTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(prompt.length() / 4.0));
    }

    public static int estimateOutputTokens(String prompt) {
        return Math.max(500, Math.min(2500, estimatePromptTokens(prompt)));
    }

    public static BigDecimal estimateTotalCredits(AiModelDefinition model, String prompt) {
        if (model == null || model.free()) {
            return BigDecimal.ZERO;
        }
        int tokens = estimatePromptTokens(prompt) + estimateOutputTokens(prompt);
        return model.creditsPerMillionTokens()
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }
}
