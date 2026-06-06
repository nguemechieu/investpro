package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;

public interface ProviderSessionPolicy extends TradingSessionPolicy {
    boolean supports(Exchange exchange);

    boolean supports(ProviderSessionContext provider);
}
