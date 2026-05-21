package org.investpro.models.currency.spi;

import org.investpro.models.currency.Currency;

import java.util.Collection;
import java.util.Set;

/**
 * Service Provider Interface for grouped currency providers.
 */
public interface CurrencyProvider {

    /**
     * Stable provider id such as FIAT, CRYPTO, METALS, INDEX.
     */
    String providerId();

    /**
     * Human-friendly provider name for diagnostics/UI.
     */
    String displayName();

    /**
     * Supported logical currency types (FIAT, FOREX, CRYPTO, METAL, INDEX, STOCK).
     */
    Set<String> supportedCurrencyTypes();

    /**
     * All currencies exported by this provider.
     */
    Collection<Currency> getCurrencies();
}
