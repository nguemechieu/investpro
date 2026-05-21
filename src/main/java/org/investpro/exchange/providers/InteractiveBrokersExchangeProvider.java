package org.investpro.exchange.providers;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.InteractiveBrokers;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class InteractiveBrokersExchangeProvider implements ExchangeProvider {
    @Override
    public String id() {
        return "INTERACTIVE_BROKERS";
    }

    @Override
    public String displayName() {
        return "Interactive Brokers";
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
        return Set.of("interactive brokers", "interactive_broker", "interactivebrokers", "ib", "ibk", "ibkr");
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
        ExchangeCredentials credentials = context.credentialResolver().resolve("interactive_brokers");
        return new InteractiveBrokers(credentials);
    }
}
