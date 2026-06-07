package org.investpro.agent.symbol;

public interface SymbolRiskEvaluator {
    RiskDecision evaluateTradeIntent(TradeIntent intent, SymbolAgentState state, MarketContext context);
}
