package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public final class CoinbaseSessionPolicy implements ProviderSessionPolicy {
    @Override
    public boolean supports(Exchange exchange) {
        String id = exchange == null ? "" : exchange.getExchangeId();
        String name = exchange == null ? "" : exchange.getName();
        return id.toLowerCase(Locale.ROOT).contains("coinbase")
                || name.toLowerCase(Locale.ROOT).contains("coinbase");
    }

    @Override
    public boolean supports(ProviderSessionContext provider) {
        return provider != null && provider.providerContains("coinbase");
    }

    @Override
    public SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        boolean connected = exchange != null
                && (Boolean.TRUE.equals(exchange.isConnected()) || exchange.canSubmitOrders());
        ProviderSessionContext provider = new ProviderSessionContext(
                exchange == null ? "" : exchange.getExchangeId(),
                exchange == null ? "" : exchange.getName(),
                connected,
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

        if (productStatus == TradabilityStatus.CANCEL_ONLY) {
            return new SessionState(true, false, true, true, false, false,
                    "Order blocked because product is cancel-only.");
        }

        if (isBlockedStatus(productStatus)) {
            return new SessionState(true, false, false, false, false, false,
                    "Order blocked because Coinbase product status is " + productStatus + ".");
        }

        if (!provider.canSubmitOrders()) {
            return new SessionState(true, false, true, false, false, false,
                    "Order blocked because account lacks live trading permission.");
        }

        return new SessionState(true, true, true, false, true, false,
                "Coinbase product is fully tradable and order submission is allowed.");
    }

    private boolean isBlockedStatus(TradabilityStatus status) {
        return status == TradabilityStatus.DISABLED
                || status == TradabilityStatus.HALTED
                || status == TradabilityStatus.VIEW_ONLY
                || status == TradabilityStatus.AUCTION_ONLY
                || status == TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE
                || status == TradabilityStatus.MIN_SIZE_INVALID
                || status == TradabilityStatus.PERMISSION_DENIED
                || status == TradabilityStatus.API_KEY_RESTRICTED
                || status == TradabilityStatus.INACTIVE
                || status == TradabilityStatus.UNKNOWN;
    }
}
