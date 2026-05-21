package org.investpro.strategy.providers;

import org.investpro.spi.StrategyProvider;
import org.investpro.spi.StrategyProviderContext;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.BreakoutStrategy;

import java.util.Set;

public final class BreakoutStrategyProvider implements StrategyProvider {
    @Override
    public String id() {
        return "BREAKOUT";
    }

    @Override
    public String displayName() {
        return "Breakout Strategy";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public String category() {
        return "BREAKOUT";
    }

    @Override
    public Set<String> supportedMarketTypes() {
        return Set.of("CRYPTO", "FOREX", "COMMODITY", "SPOT", "PERPETUAL");
    }

    @Override
    public TradingStrategy create(StrategyProviderContext context) {
        return new BreakoutStrategy();
    }
}
