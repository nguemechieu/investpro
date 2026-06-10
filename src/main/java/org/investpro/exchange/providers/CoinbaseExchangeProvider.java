package org.investpro.exchange.providers;

import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class CoinbaseExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "COINBASE";
    }

    @Override
    public String displayName() {
        return "Coinbase Advanced Trade";
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
        return Set.of("coinbase", "coinbase_advanced", "coinbase advanced trade", "coinbasepro");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("coinbase");
        return new Coinbase(credentials);
    }
}
