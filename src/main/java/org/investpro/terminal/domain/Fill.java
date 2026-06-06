package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Fill(
        String fillId,
        OrderId orderId,
        InstrumentId instrumentId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        String feeCurrency,
        Instant timestamp
) {
    public Fill {
        fillId = fillId == null ? "" : fillId.trim();
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        price = price == null ? BigDecimal.ZERO : price;
        fee = fee == null ? BigDecimal.ZERO : fee;
        feeCurrency = feeCurrency == null ? "" : feeCurrency.trim().toUpperCase();
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
