package org.investpro.terminal.provider;

import java.util.Optional;

public interface ProviderBundle extends ProviderCapabilities {
    default Optional<MarketDataProvider> marketDataProvider() {
        return Optional.empty();
    }

    default Optional<TradingProvider> tradingProvider() {
        return Optional.empty();
    }

    default Optional<AccountProvider> accountProvider() {
        return Optional.empty();
    }

    default Optional<InstrumentProvider> instrumentProvider() {
        return Optional.empty();
    }

    default Optional<BrokerActivityProvider> brokerActivityProvider() {
        return Optional.empty();
    }

    default Optional<HistoricalDataProvider> historicalDataProvider() {
        return Optional.empty();
    }
}
