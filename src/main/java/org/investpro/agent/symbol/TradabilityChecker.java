package org.investpro.agent.symbol;

import org.investpro.models.trading.TradePair;

public interface TradabilityChecker {
    TradabilityDecision check(String exchangeId, TradePair pair, SymbolAgentMode mode);
}
