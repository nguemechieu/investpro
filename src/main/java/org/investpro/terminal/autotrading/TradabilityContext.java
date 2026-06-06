package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.StrategyScore;
import org.investpro.terminal.domain.StrategySignal;

import java.time.Instant;
import java.util.Map;

public record TradabilityContext(
        Instrument instrument,
        SymbolTradingPolicy policy,
        ExchangeConnectionState connectionState,
        boolean reconciliationComplete,
        MarketQualitySnapshot marketQuality,
        boolean accountSupportsProduct,
        boolean permissionsAllowProduct,
        boolean riskAllowed,
        boolean strategyAssigned,
        boolean strategyDataReady,
        StrategyScore strategyScore,
        StrategySignal latestSignal,
        int openOrders,
        int openPositions,
        boolean duplicateOrderExists,
        boolean disabledByUser,
        Map<String, Object> metadata,
        Instant checkedAt
) {
    public TradabilityContext {
        policy = policy == null ? SymbolTradingPolicy.defaults() : policy;
        connectionState = connectionState == null ? ExchangeConnectionState.DISCONNECTED : connectionState;
        marketQuality = marketQuality == null ? MarketQualitySnapshot.missing() : marketQuality;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }
}
