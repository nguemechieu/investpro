package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record StrategySignal(
        String strategyId,
        InstrumentId instrumentId,
        String action,
        double confidence,
        String reason,
        String invalidationReason,
        BigDecimal suggestedStopLoss,
        BigDecimal suggestedTakeProfit,
        BigDecimal suggestedPositionSize,
        String suggestedOrderType,
        Instant generatedAt
) {
    public StrategySignal {
        strategyId = strategyId == null ? "" : strategyId.trim();
        action = action == null ? "HOLD" : action.trim().toUpperCase();
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        reason = reason == null ? "" : reason.trim();
        invalidationReason = invalidationReason == null ? "" : invalidationReason.trim();
        suggestedStopLoss = suggestedStopLoss == null ? BigDecimal.ZERO : suggestedStopLoss;
        suggestedTakeProfit = suggestedTakeProfit == null ? BigDecimal.ZERO : suggestedTakeProfit;
        suggestedPositionSize = suggestedPositionSize == null ? BigDecimal.ZERO : suggestedPositionSize;
        suggestedOrderType = suggestedOrderType == null ? "" : suggestedOrderType.trim().toUpperCase();
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }
}
