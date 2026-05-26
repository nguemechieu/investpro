package org.investpro.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record PerformanceProjection(BigDecimal realizedPnl, int filledTrades, Instant projectedAt) {
    public PerformanceProjection {
        realizedPnl = realizedPnl == null ? BigDecimal.ZERO : realizedPnl;
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }
}
