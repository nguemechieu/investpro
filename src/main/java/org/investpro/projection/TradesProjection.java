package org.investpro.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradesProjection(List<TradeView> trades, Instant projectedAt) {
    public TradesProjection {
        trades = trades == null ? List.of() : List.copyOf(trades);
        projectedAt = projectedAt == null ? Instant.now() : projectedAt;
    }

    public record TradeView(String exchangeId, String tradeId, String orderId, String symbol, BigDecimal quantity, BigDecimal price) {
        public TradeView {
            quantity = quantity == null ? BigDecimal.ZERO : quantity;
            price = price == null ? BigDecimal.ZERO : price;
        }
    }
}
