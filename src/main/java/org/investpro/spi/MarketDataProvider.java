package org.investpro.spi;

import java.util.Set;

public interface MarketDataProvider extends InvestProPlugin {
    Set<String> supportedAssetClasses();

    MarketDataClient create(MarketDataProviderContext context);
}
