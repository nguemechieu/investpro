package org.investpro.strategy.recovery;

import java.time.Instant;
import java.util.Objects;

/**
 * Captures runtime ownership of a broker position by an assignment.
 */
public record StrategyPositionOwnership(
        String assignmentId,
        String strategyId,
        String symbol,
        String timeframe,
        String brokerPositionId,
        String exchange,
        Instant openedAt,
        Instant lastSyncedAt,
        double quantity,
        boolean live) {

    public StrategyPositionOwnership {
        assignmentId = Objects.requireNonNullElse(assignmentId, "").trim();
        strategyId = Objects.requireNonNullElse(strategyId, "").trim();
        symbol = Objects.requireNonNullElse(symbol, "").trim();
        timeframe = Objects.requireNonNullElse(timeframe, "").trim();
        brokerPositionId = Objects.requireNonNullElse(brokerPositionId, "").trim();
        exchange = Objects.requireNonNullElse(exchange, "").trim();
        openedAt = openedAt == null ? Instant.now() : openedAt;
        lastSyncedAt = lastSyncedAt == null ? Instant.now() : lastSyncedAt;
    }

    public boolean matchesSymbol(String targetSymbol) {
        return !symbol.isBlank() && symbol.equalsIgnoreCase(Objects.requireNonNullElse(targetSymbol, "").trim());
    }
}
