package org.investpro.agent.symbol;

import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyDefinition;

import java.time.LocalDateTime;
import java.util.Map;

public record SymbolAgentState(
        TradePair pair,
        String exchangeId,
        SymbolAgentStatus status,
        SymbolAgentMode mode,
        StrategyDefinition assignedStrategy,
        LocalDateTime startedAt,
        LocalDateTime lastCandleAt,
        LocalDateTime lastSignalAt,
        LocalDateTime lastEvaluationAt,
        LocalDateTime lastBrokerActivityAt,
        int candlesLoaded,
        boolean marketDataReady,
        boolean tradable,
        boolean orderSubmissionAllowed,
        boolean hasOpenPosition,
        boolean hasPendingOrder,
        String lastSignal,
        String lastError,
        Map<String, String> metadata) {

    public SymbolAgentState {
        status = status == null ? SymbolAgentStatus.CREATED : status;
        mode = mode == null ? SymbolAgentMode.PAPER : mode;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public SymbolAgentState withStatus(SymbolAgentStatus status, String error) {
        return new SymbolAgentState(pair, exchangeId, status, mode, assignedStrategy, startedAt, lastCandleAt,
                lastSignalAt, lastEvaluationAt, lastBrokerActivityAt, candlesLoaded, marketDataReady, tradable,
                orderSubmissionAllowed, hasOpenPosition, hasPendingOrder, lastSignal, error, metadata);
    }

    public SymbolAgentState withStrategy(StrategyDefinition strategy) {
        return new SymbolAgentState(pair, exchangeId, status, mode, strategy, startedAt, lastCandleAt,
                lastSignalAt, lastEvaluationAt, lastBrokerActivityAt, candlesLoaded, marketDataReady, tradable,
                orderSubmissionAllowed, hasOpenPosition, hasPendingOrder, lastSignal, lastError, metadata);
    }
}
