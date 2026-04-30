package org.investpro.models.trading;


public interface LiveTradesConsumer {

    boolean containsKey(TradePair tradePair);

    void remove(TradePair tradePair);


    void put(TradePair tradePair);

    Trade get(TradePair tradePair);

    void accept(Trade trade);


    void acceptTrades(Trade trades);
}
