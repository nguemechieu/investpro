package org.investpro.agent;

import org.investpro.models.trading.TradePair;

import java.util.Locale;

public record AgentKey(String exchangeId, String symbol) {

    public AgentKey {
        exchangeId = normalize(exchangeId);
        symbol = normalize(symbol);
    }

    public static AgentKey of(String exchangeId, TradePair pair) {
        return new AgentKey(exchangeId, pair == null ? "" : pair.toString('/'));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
