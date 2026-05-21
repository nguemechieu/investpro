package org.investpro.exchange.providers;

import org.investpro.exchange.Bitfinex;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class BitfinexExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "BITFINEX";
    }

    @Override
    public String displayName() {
        return "Bitfinex";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public Set<String> aliases() {
        return Set.of("bitfinex", "bitfinex_us", "bitfinexus");
    }

    @Override
    public boolean supportsPaperTrading() {
        return false;
    }

    @Override
    public boolean supportsLiveTrading() {
        return true;
    }

    @Override
    public Exchange create(ExchangeProviderContext context) {
        ExchangeCredentials credentials = context.credentialResolver().resolve("bitfinex");
        return new Bitfinex(credentials);
    }
}
