package org.investpro.agent.symbol;

import org.investpro.models.trading.TradePair;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record TradeIntent(
        String id,
        TradePair pair,
        String exchangeId,
        String strategyName,
        SignalType signalType,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        String reason,
        double confidence,
        LocalDateTime createdAt,
        Map<String, String> metadata) {

    public TradeIntent {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        orderType = orderType == null ? OrderType.MARKET : orderType;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
