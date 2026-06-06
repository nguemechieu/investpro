package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public final class StellarSessionPolicy implements ProviderSessionPolicy {
    @Override
    public boolean supports(Exchange exchange) {
        String id = exchange == null ? "" : exchange.getExchangeId();
        String name = exchange == null ? "" : exchange.getName();
        return id.toLowerCase(Locale.ROOT).contains("stellar")
                || name.toLowerCase(Locale.ROOT).contains("stellar");
    }

    @Override
    public boolean supports(ProviderSessionContext provider) {
        return provider != null && provider.providerContains("stellar");
    }

    @Override
    public SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        ProviderSessionContext provider = new ProviderSessionContext(
                exchange == null ? "" : exchange.getExchangeId(),
                exchange == null ? "" : exchange.getName(),
                exchange != null && Boolean.TRUE.equals(exchange.isConnected()),
                exchange != null && exchange.canSubmitOrders());
        return sessionState(provider, instrument, productStatus, productMetadata, now);
    }

    @Override
    public SessionState sessionState(
            ProviderSessionContext provider,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        if (provider == null || !provider.connected()) {
            return new SessionState(true, false, false, false, false, false,
                    "Order blocked because provider is disconnected.");
        }
        if (productStatus == TradabilityStatus.FULLY_TRADABLE && provider.canSubmitOrders()) {
            return SessionState.open24x7("Stellar network is connected and order submission is allowed.");
        }
        return new SessionState(true, false, true, false, false, false,
                "Order blocked because Stellar tradability checks did not pass.");
    }
}
