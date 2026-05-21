package org.investpro.spi;

import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.operations.SystemActivityBus;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ExchangeProviderContext(
        CredentialProvider credentialProvider,
        ExchangeCredentialResolver credentialResolver,
        Map<String, String> config,
        SystemActivityBus eventBus) {

    public ExchangeProviderContext {
        Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        Objects.requireNonNull(credentialResolver, "credentialResolver must not be null");
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public Optional<String> configValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.get(key));
    }
}
