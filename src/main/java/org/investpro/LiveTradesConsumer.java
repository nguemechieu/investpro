package org.investpro;

import java.util.List;


public interface LiveTradesConsumer {
    void acceptTrades(TradePair tradePair);


    boolean containsKey(TradePair tradePair);

    void remove(TradePair tradePair);


    void put(TradePair tradePair);

    LiveTrade get(TradePair tradePair);

    void accept(Trade trade);

    void acceptTrades(List<Trade> trades);
}
