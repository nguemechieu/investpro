package org.investpro.exchange.providers;

import org.investpro.exchange.binance.Binance;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class BinanceExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "BINANCE";
    }

    @Override
    public String displayName() {
        return "Binance";
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
        return Set.of("binance", "binance_global", "binance international", "binanceglobal");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("binance");
        return new Binance(credentials);
    }
}
