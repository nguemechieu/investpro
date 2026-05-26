package org.investpro.strategy.signals;

import org.investpro.market.MarketContext;

public interface TradingStrategy {
    String name();

    StrategySignal evaluate(MarketContext context);
}
