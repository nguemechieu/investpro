package org.investpro.agent.symbol;

import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyDefinition;

import java.util.List;
import java.util.Map;

public class DefaultStrategyEvaluator implements StrategyEvaluator {

    @Override
    public AgentStrategySignal evaluate(StrategyDefinition strategy, TradePair pair, List<CandleData> candles, MarketContext context) {
        return new AgentStrategySignal(SignalType.NEUTRAL, 0.0, "No concrete evaluator configured", Map.of());
    }
}
