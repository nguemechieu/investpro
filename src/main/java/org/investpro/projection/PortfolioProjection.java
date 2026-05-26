package org.investpro.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioProjection(BigDecimal totalEquity, BigDecimal totalExposure, Instant projectedAt) {
    public PortfolioProjection {
        totalEquity = totalEquity == null ? BigDecimal.ZERO : totalEquity;
        totalExposure = totalExposure == null ? BigDecimal.ZERO : totalExposure;
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }
}
