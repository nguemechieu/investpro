package org.investpro.agent.symbol;

public class SafeDefaultRiskEvaluator implements SymbolRiskEvaluator {

    @Override
    public RiskDecision evaluateTradeIntent(TradeIntent intent, SymbolAgentState state, MarketContext context) {
        return RiskDecision.rejected("No RiskManagementSystem adapter configured");
    }
}
