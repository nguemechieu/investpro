package org.investpro.execution.routing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record VenueScore(
        String venueId,
        BigDecimal liquidityScore,
        BigDecimal latencyScore,
        BigDecimal feeScore,
        BigDecimal reliabilityScore,
        BigDecimal compositeScore,
        Map<String, Object> metadata,
        Instant scoredAt) {

    public VenueScore {
        venueId = venueId == null ? "" : venueId.trim();
        liquidityScore = value(liquidityScore);
        latencyScore = value(latencyScore);
        feeScore = value(feeScore);
        reliabilityScore = value(reliabilityScore);
        compositeScore = value(compositeScore);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        scoredAt = scoredAt == null ? Instant.now() : scoredAt;
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
