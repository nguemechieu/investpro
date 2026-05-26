package org.investpro.projection;

import java.time.Instant;
import java.util.List;

public record OrdersProjection(List<OrderView> orders, Instant projectedAt) {
    public OrdersProjection {
        orders = orders == null ? List.of() : List.copyOf(orders);
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }

    public record OrderView(String exchangeId, String orderId, String symbol, String status) {
    }
}
