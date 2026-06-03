package org.investpro.spi.providers;

import org.investpro.spi.MarketDataClient;
import org.investpro.spi.MarketDataProvider;
import org.investpro.spi.MarketDataProviderContext;

import java.util.Set;

/**
 * Built-in market data provider so the plugin manager can discover a default
 * market data entry.
 */
public final class CoreMarketDataProvider implements MarketDataProvider {

    @Override
    public String id() {
        return "core-market-data";
    }

    @Override
    public String displayName() {
        return "Core Market Data";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public Set<String> supportedAssetClasses() {
        return Set.of("STOCKS", "FOREX", "CRYPTO", "FUTURES");
    }

    @Override
    public MarketDataClient create(MarketDataProviderContext context) {
        return () -> "core-market-data";
    }
}