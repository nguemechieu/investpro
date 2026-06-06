package org.investpro.asset;

import org.investpro.models.trading.OpenOrder;

import java.math.BigDecimal;

public record OrderTradabilityRequest(
        ExchangeId exchangeId,
        String symbol,
        OpenOrder.OrderType orderType,
        BigDecimal quantity,
        boolean liveMode,
        boolean exchangeConnected,
        boolean sessionOpen
) {
    public OrderTradabilityRequest {
        exchangeId = exchangeId == null ? ExchangeId.UNKNOWN : exchangeId;
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }
}
