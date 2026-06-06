package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.Instant;
import java.util.Map;

public interface TradingSessionPolicy {
    SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    );

    SessionState sessionState(
            ProviderSessionContext provider,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    );

    default boolean requiresActiveSessionForOrderSubmission(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata
    ) {
        return sessionState(exchange, instrument, productStatus, productMetadata, Instant.now()).requiresActiveSession();
    }
}
