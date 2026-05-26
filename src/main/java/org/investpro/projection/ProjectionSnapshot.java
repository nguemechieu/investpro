package org.investpro.projection;

import java.time.Instant;

public record ProjectionSnapshot(
        OrdersProjection orders,
        PositionsProjection positions,
        TradesProjection trades,
        AccountProjection account,
        PortfolioProjection portfolio,
        PerformanceProjection performance,
        Instant projectedAt) {

    public ProjectionSnapshot {
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }
}
