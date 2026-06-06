package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Position(
        String positionId,
        InstrumentId instrumentId,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal unrealizedPnl,
        Instant updatedAt
) {
    public Position {
        positionId = positionId == null ? "" : positionId.trim();
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        averagePrice = averagePrice == null ? BigDecimal.ZERO : averagePrice;
        unrealizedPnl = unrealizedPnl == null ? BigDecimal.ZERO : unrealizedPnl;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
