package org.investpro.strategy.providers;

import org.investpro.spi.StrategyProvider;
import org.investpro.spi.StrategyProviderContext;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.MeanReversionStrategy;

import java.util.Set;

public final class RsiStrategyProvider implements StrategyProvider {
    @Override
    public String id() {
        return "RSI_STRATEGY";
    }

    @Override
    public String displayName() {
        return "RSI Mean Reversion Strategy";
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
        return "MEAN_REVERSION";
    }

    @Override
    public Set<String> supportedMarketTypes() {
        return Set.of("CRYPTO", "FOREX", "EQUITY", "SPOT");
    }

    @Override
    public TradingStrategy create(StrategyProviderContext context) {
        return new MeanReversionStrategy();
    }
}
