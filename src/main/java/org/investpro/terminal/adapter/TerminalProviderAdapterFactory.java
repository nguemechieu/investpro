package org.investpro.terminal.adapter;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.StellarNetwork;
import org.investpro.terminal.provider.ProviderBundle;

public final class TerminalProviderAdapterFactory {

    private TerminalProviderAdapterFactory() {
    }

    public static ProviderBundle forExchange(Exchange exchange) {
        if (exchange instanceof StellarNetwork stellarNetwork) {
            return new StellarTerminalProviderAdapter(stellarNetwork);
        }
        return new ExchangeTerminalProviderAdapter(exchange);
    }
}
