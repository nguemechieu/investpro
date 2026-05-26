package org.investpro.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountProjection(String exchangeId, BigDecimal balance, BigDecimal marginAvailable, Instant projectedAt) {
    public AccountProjection {
        exchangeId = exchangeId == null ? "" : exchangeId.trim();
        balance = balance == null ? BigDecimal.ZERO : balance;
        marginAvailable = marginAvailable == null ? BigDecimal.ZERO : marginAvailable;
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }
}
