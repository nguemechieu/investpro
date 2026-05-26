package org.investpro.execution;

import org.investpro.strategy.signals.TradingAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrderIntent(
        String exchangeId,
        String symbol,
        TradingAction side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        String clientOrderId,
        String sourceStrategy,
        String botDecisionId,
        String riskDecisionId,
        String reason,
        ExecutionMode executionMode,
        Instant createdAt,
        Map<String, Object> metadata) {

    public OrderIntent {
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        side = side == null ? TradingAction.HOLD : side;
        orderType = safe(orderType).isBlank() ? "MARKET" : safe(orderType);
        quantity = value(quantity);
        limitPrice = value(limitPrice);
        stopPrice = value(stopPrice);
        stopLoss = value(stopLoss);
        takeProfit = value(takeProfit);
        clientOrderId = safe(clientOrderId).isBlank() ? UUID.randomUUID().toString() : safe(clientOrderId);
        sourceStrategy = safe(sourceStrategy);
        botDecisionId = safe(botDecisionId);
        riskDecisionId = safe(riskDecisionId);
        reason = safe(reason);
        executionMode = executionMode == null ? ExecutionMode.PAPER : executionMode;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
