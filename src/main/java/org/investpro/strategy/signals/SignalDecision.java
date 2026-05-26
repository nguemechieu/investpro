package org.investpro.strategy.signals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SignalDecision(
        TradingAction finalAction,
        BigDecimal finalConfidence,
        BigDecimal buyScore,
        BigDecimal sellScore,
        BigDecimal holdScore,
        BigDecimal conflictLevel,
        int strategiesVoting,
        List<String> winningStrategyNames,
        List<String> reasons,
        Instant decidedAt,
        Map<String, Object> metadata) {

    public SignalDecision {
        finalAction = finalAction == null ? TradingAction.HOLD : finalAction;
        finalConfidence = value(finalConfidence);
        buyScore = value(buyScore);
        sellScore = value(sellScore);
        holdScore = value(holdScore);
        conflictLevel = value(conflictLevel);
        winningStrategyNames = winningStrategyNames == null ? List.of() : List.copyOf(winningStrategyNames);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        decidedAt = decidedAt == null ? Instant.now() : decidedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean actionable() {
        return finalAction == TradingAction.BUY || finalAction == TradingAction.SELL
                || finalAction == TradingAction.CLOSE || finalAction == TradingAction.REDUCE;
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
