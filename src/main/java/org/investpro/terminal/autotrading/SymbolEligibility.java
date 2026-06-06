package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.StrategyScore;
import org.investpro.terminal.domain.StrategySignal;

import java.time.Instant;
import java.util.Map;

public record SymbolEligibility(
        Instrument instrument,
        boolean tradable,
        TradabilityFailureReason failureReason,
        AutoTradeSymbolState botState,
        String assignedStrategy,
        StrategyScore strategyScore,
        StrategySignal latestSignal,
        double spreadPercent,
        int liquidityScore,
        double volume24h,
        String marketDataStatus,
        int openOrders,
        int openPositions,
        Instant lastDecisionTime,
        Map<String, Object> metadata
) {
    public SymbolEligibility {
        failureReason = failureReason == null ? TradabilityFailureReason.UNKNOWN : failureReason;
        botState = botState == null ? AutoTradeSymbolState.DISCOVERED : botState;
        assignedStrategy = assignedStrategy == null ? "" : assignedStrategy.trim();
        marketDataStatus = marketDataStatus == null ? "" : marketDataStatus.trim();
        lastDecisionTime = lastDecisionTime == null ? Instant.now() : lastDecisionTime;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SymbolEligibility fromResult(TradabilityContext context, TradabilityResult result) {
        String strategyId = context.latestSignal() != null
                ? context.latestSignal().strategyId()
                : context.strategyScore() == null ? "" : context.strategyScore().strategyId();
        return new SymbolEligibility(
                context.instrument(),
                result.tradable(),
                result.failureReason(),
                result.state(),
                strategyId,
                context.strategyScore(),
                context.latestSignal(),
                result.spreadPercent(),
                result.liquidityScore(),
                result.volume24h(),
                context.marketQuality().tick() == null ? "MISSING" : "LIVE",
                context.openOrders(),
                context.openPositions(),
                result.checkedAt(),
                result.metadata());
    }
}
