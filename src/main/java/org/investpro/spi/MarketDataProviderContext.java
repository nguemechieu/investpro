package org.investpro.spi;

import org.investpro.exchange.Exchange;
import org.investpro.market.MarketDataEngine;

import java.util.Map;
import java.util.Optional;

public record MarketDataProviderContext(
        Exchange exchange,
        MarketDataEngine marketDataEngine,
        Map<String, String> config) {

    public MarketDataProviderContext {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public Optional<String> configValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.get(key));
    }
}
