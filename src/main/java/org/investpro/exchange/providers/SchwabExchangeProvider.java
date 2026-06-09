package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.schwab.Schwab;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class SchwabExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "SCHWAB";
    }

    @Override
    public String displayName() {
        return "Charles Schwab";
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
        return Set.of("schwab", "charles schwab", "charlesschwab");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("schwab");
        return new Schwab(credentials);
    }
}
