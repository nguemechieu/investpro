package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class LiveTrade {
    private static final Logger logger = LoggerFactory.getLogger(LiveTrade.class);
    List<Trade> trades = new ArrayList<>();


    Object get(TradePair tradePair) {
        for (Trade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                return trade;
            }
        }
        return null;


    }

    boolean containsKey(TradePair tradePair) {
        for (Trade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                return true;
            }
        }
        return false;
    }

    void remove(TradePair tradePair) {
        for (int i = 0; i < trades.size(); i++) {
            if (trades.get(i).getTradePair().equals(tradePair)) {
                trades.remove(i);
            } else {
                logger.error(STR."tradePair: \{tradePair.toString()} not found");
            }

        }

    }

    void put(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        for (Trade trade : trades) {

            if (trade.getTradePair().equals(tradePair)) {
                liveTradesConsumer.get(tradePair).acceptTrades(
                        (List<Trade>) trade);

            }
        }


    }

    void acceptTrades(List<Trade> trade) {
        trades.addAll(trade);
    }
}
