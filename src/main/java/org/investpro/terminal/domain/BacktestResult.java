package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BacktestResult(
        String resultId,
        String strategyId,
        InstrumentId instrumentId,
        String timeframe,
        BigDecimal totalReturn,
        BigDecimal maxDrawdown,
        double winRate,
        double profitFactor,
        double sharpeLikeScore,
        int numberOfTrades,
        String failureReason,
        Instant completedAt,
        Map<String, Object> metrics
) {
    public BacktestResult {
        resultId = resultId == null ? "" : resultId.trim();
        strategyId = strategyId == null ? "" : strategyId.trim();
        timeframe = timeframe == null ? "" : timeframe.trim();
        totalReturn = totalReturn == null ? BigDecimal.ZERO : totalReturn;
        maxDrawdown = maxDrawdown == null ? BigDecimal.ZERO : maxDrawdown;
        failureReason = failureReason == null ? "" : failureReason.trim();
        completedAt = completedAt == null ? Instant.now() : completedAt;
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
