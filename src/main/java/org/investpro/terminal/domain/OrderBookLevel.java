package org.investpro.terminal.domain;

import java.math.BigDecimal;

public record OrderBookLevel(BigDecimal price, BigDecimal quantity, int orderCount) {
    public OrderBookLevel {
        price = price == null ? BigDecimal.ZERO : price;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        orderCount = Math.max(0, orderCount);
    }
}
