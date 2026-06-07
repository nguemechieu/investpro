package org.investpro.ai;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record AiModelDefinition(
        String id,
        AiProvider provider,
        String displayName,
        String description,
        BigDecimal creditsPerMillionTokens,
        boolean free,
        boolean supportsVision,
        Set<AiFeature> features,
        AiModelStatus status,
        Map<String, String> metadata) {
}
