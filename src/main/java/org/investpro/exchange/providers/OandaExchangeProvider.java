package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.Oanda;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class OandaExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "OANDA";
    }

    @Override
    public String displayName() {
        return "OANDA FX/CFD";
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
        return Set.of("oanda", "fx", "forex", "oanda_fx", "oandafxcfd");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("oanda");
        return new Oanda(credentials);
    }
}
