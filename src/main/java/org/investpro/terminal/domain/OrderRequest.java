package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record OrderRequest(
        String clientOrderId,
        InstrumentId instrumentId,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        Instant submittedAt,
        Map<String, Object> metadata
) {
    public OrderRequest {
        clientOrderId = clientOrderId == null ? "" : clientOrderId.trim();
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        side = side == null ? "" : side.trim().toUpperCase();
        orderType = orderType == null ? "" : orderType.trim().toUpperCase();
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        limitPrice = limitPrice == null ? BigDecimal.ZERO : limitPrice;
        stopPrice = stopPrice == null ? BigDecimal.ZERO : stopPrice;
        submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
