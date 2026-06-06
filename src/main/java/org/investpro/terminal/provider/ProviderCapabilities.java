package org.investpro.terminal.provider;

import org.investpro.terminal.domain.AssetClass;

import java.util.Set;

public interface ProviderCapabilities {
    String providerId();

    default Set<AssetClass> supportedAssetClasses() {
        return Set.of();
    }

    default boolean supportsLiveMarketData() {
        return false;
    }

    default boolean supportsHistoricalCandles() {
        return false;
    }

    default boolean supportsTrading() {
        return false;
    }

    default boolean supportsOrderBook() {
        return false;
    }

    default boolean supportsAccountBalances() {
        return false;
    }

    default boolean supportsPositions() {
        return false;
    }

    default boolean supportsBrokerActivity() {
        return false;
    }

    default boolean supportsInstrumentDiscovery() {
        return false;
    }
}
