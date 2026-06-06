package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TradePrint(
        InstrumentId instrumentId,
        String tradeId,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        Instant timestamp
) {
    public TradePrint {
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        tradeId = tradeId == null ? "" : tradeId.trim();
        side = side == null ? "" : side.trim().toUpperCase();
        price = price == null ? BigDecimal.ZERO : price;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
