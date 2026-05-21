package org.investpro.strategy.providers;

import org.investpro.spi.StrategyProvider;
import org.investpro.spi.StrategyProviderContext;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.TrendFollowingStrategy;

import java.util.Set;

public final class MovingAverageStrategyProvider implements StrategyProvider {
    @Override
    public String id() {
        return "MOVING_AVERAGE_STRATEGY";
    }

    @Override
    public String displayName() {
        return "Moving Average Trend Strategy";
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
        return "TREND_FOLLOWING";
    }

    @Override
    public Set<String> supportedMarketTypes() {
        return Set.of("CRYPTO", "FOREX", "EQUITY", "SPOT", "PERPETUAL");
    }

    @Override
    public TradingStrategy create(StrategyProviderContext context) {
        return new TrendFollowingStrategy();
    }
}
