package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.StellarNetwork;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class StellarNetworkExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "STELLAR_NETWORK";
    }

    @Override
    public String displayName() {
        return "Stellar Network";
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
        return Set.of("stellar", "stellar network", "stellar-network", "stellarnetwork", "xlm");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("stellar_network");
        return new StellarNetwork(credentials);
    }
}
