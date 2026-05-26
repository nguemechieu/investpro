package org.investpro.strategy.signals;

import org.investpro.market.MarketContext;

import java.util.List;

public class PipelineStrategyEngine {
    private final PipelineStrategyCatalog catalog;

    public PipelineStrategyEngine(PipelineStrategyCatalog catalog) {
        this.catalog = catalog == null ? new PipelineStrategyCatalog() : catalog;
    }

    public List<StrategySignal> evaluate(MarketContext context) {
        return catalog.strategies().stream()
                .map(strategy -> safeEvaluate(strategy, context))
                .toList();
    }

    private StrategySignal safeEvaluate(TradingStrategy strategy, MarketContext context) {
        try {
            StrategySignal signal = strategy.evaluate(context);
            return signal == null
                    ? StrategySignal.hold(strategy.name(), context == null ? "" : context.symbol(), "Strategy returned no signal.")
                    : signal;
        } catch (Exception exception) {
            return StrategySignal.hold(strategy.name(), context == null ? "" : context.symbol(),
                    "Strategy failed: " + exception.getMessage());
        }
    }
}
