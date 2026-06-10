package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.kraken.Kraken;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class KrakenExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "KRAKEN";
    }

    @Override
    public String displayName() {
        return "Kraken";
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
        return Set.of("kraken", "kraken_spot", "krakenpro");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("kraken");
        return new Kraken(credentials);
    }
}
