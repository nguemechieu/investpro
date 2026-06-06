package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ExchangeSessionService {

    private final List<ProviderSessionPolicy> policies;

    public ExchangeSessionService() {
        this(List.of(
                new CoinbaseSessionPolicy(),
                new StellarSessionPolicy(),
                new OandaSessionPolicy(),
                new DefaultMarketHoursSessionPolicy()));
    }

    public ExchangeSessionService(List<ProviderSessionPolicy> policies) {
        this.policies = policies == null || policies.isEmpty()
                ? List.of(new DefaultMarketHoursSessionPolicy())
                : List.copyOf(policies);
    }

    public SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata
    ) {
        return sessionState(exchange, instrument, productStatus, productMetadata, Instant.now());
    }

    public SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        return policyFor(exchange).sessionState(
                exchange,
                instrument,
                productStatus,
                productMetadata == null ? Map.of() : productMetadata,
                now);
    }

    public SessionState sessionState(
            ProviderSessionContext provider,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        return policyFor(provider).sessionState(
                provider,
                instrument,
                productStatus,
                productMetadata == null ? Map.of() : productMetadata,
                now);
    }

    public boolean requiresActiveSessionForOrderSubmission(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata
    ) {
        return sessionState(exchange, instrument, productStatus, productMetadata).requiresActiveSession();
    }

    private ProviderSessionPolicy policyFor(Exchange exchange) {
        return policies.stream()
                .filter(policy -> policy.supports(exchange))
                .findFirst()
                .orElseGet(DefaultMarketHoursSessionPolicy::new);
    }

    private ProviderSessionPolicy policyFor(ProviderSessionContext provider) {
        return policies.stream()
                .filter(policy -> policy.supports(provider))
                .findFirst()
                .orElseGet(DefaultMarketHoursSessionPolicy::new);
    }
}
