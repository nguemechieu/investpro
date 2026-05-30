package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared tradability provider registry used by UI and execution pipelines.
 */
@Slf4j
public final class TradabilityProviderRegistry {
    private static volatile TradabilityProviderRegistry instance;

    private final AtomicReference<TradabilityProvider> provider = new AtomicReference<>();

    private TradabilityProviderRegistry() {
    }

    public static TradabilityProviderRegistry getInstance() {
        TradabilityProviderRegistry local = instance;
        if (local == null) {
            synchronized (TradabilityProviderRegistry.class) {
                local = instance;
                if (local == null) {
                    local = new TradabilityProviderRegistry();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void setProvider(TradabilityProvider newProvider) {
        provider.set(newProvider);
        log.debug("Tradability provider registered: {}",
                newProvider == null ? "none" : newProvider.getClass().getSimpleName());
    }

    public Optional<TradabilityProvider> getProvider() {
        return Optional.ofNullable(provider.get());
    }
}