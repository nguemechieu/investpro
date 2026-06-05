package org.investpro.broker.ibkr;

import org.investpro.models.trading.TradePair;

public class IBKRContractService {

    public IBKRContract resolveContract(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("TradePair is required");
        }

        String base = tradePair.getBaseCurrency().getCode();
        String quote = tradePair.getCounterCurrency().getCode();
        String secType = "STK";
        String exchange = "SMART";
        String primaryExchange = "NASDAQ";

        if (quote.equalsIgnoreCase("USD") && base.length() == 3) {
            secType = "CASH";
            exchange = "IDEALPRO";
            primaryExchange = "IDEALPRO";
        }

        return new IBKRContract(tradePair, secType, exchange, quote, primaryExchange);
    }
}
