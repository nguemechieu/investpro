package org.investpro.terminal.ai;

import java.time.Instant;
import java.util.Map;

public record AiReviewResult(
        boolean available,
        boolean approved,
        double confidence,
        String recommendation,
        String explanation,
        Instant reviewedAt,
        Map<String, Object> metadata
) {
    public AiReviewResult {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        recommendation = recommendation == null ? "" : recommendation.trim();
        explanation = explanation == null ? "" : explanation.trim();
        reviewedAt = reviewedAt == null ? Instant.now() : reviewedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AiReviewResult disabled() {
        return new AiReviewResult(false, false, 0.0, "DISABLED", "AI integration is disabled by configuration.", null, Map.of());
    }
}
