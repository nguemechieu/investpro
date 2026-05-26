package org.investpro.projection;

import java.time.Instant;
import java.util.List;

public record PositionsProjection(List<PositionView> positions, Instant projectedAt) {
    public PositionsProjection {
        positions = positions == null ? List.of() : List.copyOf(positions);
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }

    public record PositionView(String exchangeId, String positionId, String symbol, String status) {
    }
}
