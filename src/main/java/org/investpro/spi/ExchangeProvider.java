package org.investpro.spi;

import org.investpro.exchange.Exchange;

import java.util.Set;

public interface ExchangeProvider extends InvestProPlugin {
    Set<String> aliases();

    boolean supportsPaperTrading();

    boolean supportsLiveTrading();

    Exchange create(ExchangeProviderContext context);
}
