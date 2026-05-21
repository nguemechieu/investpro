package org.investpro.spi;

import org.investpro.strategy.TradingStrategy;

import java.util.Set;

public interface StrategyProvider extends InvestProPlugin {
    String category();

    Set<String> supportedMarketTypes();

    TradingStrategy create(StrategyProviderContext context);
}
