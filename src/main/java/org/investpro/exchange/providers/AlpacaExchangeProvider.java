package org.investpro.exchange.providers;

import org.investpro.exchange.alpaca.Alpaca;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class AlpacaExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "ALPACA";
    }

    @Override
    public String displayName() {
        return "Alpaca";
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
        return Set.of("alpaca", "alpaca_stocks", "alpaca_crypto", "alpacastocks");
    }

    @Override
    public boolean supportsPaperTrading() {
        return true;
    }

    @Override
    public boolean supportsLiveTrading() {
        return true;
    }

    @Override
    public Exchange create(ExchangeProviderContext context) {
        ExchangeCredentials credentials = context.credentialResolver().resolve("alpaca");
        return new Alpaca(credentials);
    }
}
