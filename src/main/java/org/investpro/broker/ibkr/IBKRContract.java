package org.investpro.broker.ibkr;

import org.investpro.models.trading.TradePair;

public record IBKRContract(
        TradePair tradePair,
        String secType,
        String exchange,
        String currency,
        String primaryExchange) {
}
