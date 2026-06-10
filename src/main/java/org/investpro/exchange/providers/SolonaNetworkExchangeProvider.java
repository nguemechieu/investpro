package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.solona.SolonaNetwork;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class SolonaNetworkExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "SOLONA_NETWORK";
    }

    @Override
    public String displayName() {
        return "Solona Network";
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
        return Set.of("solona", "solona network", "solona-network", "solonanetwork", "sol");
    }

    @Override
    public boolean supportsPaperTrading() {
        return true;
    }

    @Override
    public boolean supportsLiveTrading() {
        return false;
    }

    @Override
    public Exchange create(ExchangeProviderContext context) {
        ExchangeCredentials credentials = context.credentialResolver().resolve("solona_network");
        return new SolonaNetwork(credentials);
    }
}
