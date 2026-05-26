package org.investpro.strategy.signals;

import org.investpro.indicators.IndicatorSnapshot;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record StrategySignal(
        String strategyName,
        String exchangeId,
        String symbol,
        TradingAction action,
        BigDecimal confidence,
        String reason,
        BigDecimal suggestedStopLoss,
        BigDecimal suggestedTakeProfit,
        Duration suggestedHoldingPeriod,
        IndicatorSnapshot indicatorSnapshot,
        Instant generatedAt,
        Map<String, Object> metadata) {

    public StrategySignal {
        strategyName = safe(strategyName);
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        action = action == null ? TradingAction.HOLD : action;
        confidence = clamp(confidence);
        reason = safe(reason);
        suggestedStopLoss = value(suggestedStopLoss);
        suggestedTakeProfit = value(suggestedTakeProfit);
        suggestedHoldingPeriod = suggestedHoldingPeriod == null ? Duration.ZERO : suggestedHoldingPeriod;
        indicatorSnapshot = indicatorSnapshot == null ? IndicatorSnapshot.empty() : indicatorSnapshot;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static StrategySignal hold(String strategyName, String symbol, String reason) {
        return new StrategySignal(strategyName, "", symbol, TradingAction.HOLD, BigDecimal.ZERO, reason,
                BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO, IndicatorSnapshot.empty(), Instant.now(), Map.of());
    }

    private static BigDecimal clamp(BigDecimal value) {
        BigDecimal safe = value(value);
        if (safe.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (safe.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return safe;
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
