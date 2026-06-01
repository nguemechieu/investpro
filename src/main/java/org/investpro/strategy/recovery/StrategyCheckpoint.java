package org.investpro.strategy.recovery;

import java.time.Instant;
import java.util.Objects;

/**
 * Snapshot of assignment runtime state used for safe restart recovery.
 */
public record StrategyCheckpoint(
        String assignmentId,
        String strategyId,
        String symbol,
        String timeframe,
        String exchange,
        String status,
        String brokerPositionId,
        String openOrderIds,
        double unrealizedPnl,
        double drawdown,
        String healthLevel,
        Instant createdAt,
        Instant updatedAt) {

    public StrategyCheckpoint {
        assignmentId = Objects.requireNonNullElse(assignmentId, "").trim();
        strategyId = Objects.requireNonNullElse(strategyId, "").trim();
        symbol = Objects.requireNonNullElse(symbol, "").trim();
        timeframe = Objects.requireNonNullElse(timeframe, "").trim();
        exchange = Objects.requireNonNullElse(exchange, "").trim();
        status = Objects.requireNonNullElse(status, "").trim();
        brokerPositionId = Objects.requireNonNullElse(brokerPositionId, "").trim();
        openOrderIds = Objects.requireNonNullElse(openOrderIds, "").trim();
        healthLevel = Objects.requireNonNullElse(healthLevel, "UNKNOWN").trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
