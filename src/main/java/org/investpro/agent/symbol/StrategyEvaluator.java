package org.investpro.agent.symbol;

import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyDefinition;

import java.util.List;

public interface StrategyEvaluator {
    AgentStrategySignal evaluate(StrategyDefinition strategy, TradePair pair, List<CandleData> candles, MarketContext context);
}
