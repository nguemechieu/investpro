package org.investpro.agent.symbol;

public interface SymbolExecutionEngine {
    void submitTradeIntent(TradeIntent intent, RiskDecision decision);
}
