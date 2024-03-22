package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class LiveTrade {
    private static final Logger logger = LoggerFactory.getLogger(LiveTrade.class);
    List<LiveTrade> trades = new ArrayList<>();
    private TradePair tradepair;

    public LiveTrade(TradePair tradePair, Money price, Money size, Side side, long tradeId, Instant time) {
        this.tradepair = tradePair;
        trades.add(new LiveTrade(tradePair, price, size, side, tradeId, time));
    }


    LiveTrade get(TradePair tradePair) {
        for (LiveTrade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                return trade;

            }
        }
        return null;


    }

    boolean containsKey(TradePair tradePair) {
        for (LiveTrade trade : trades) {
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
        for (LiveTrade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                liveTradesConsumer.get(tradePair).acceptTrades(
                        (List<LiveTrade>) trade);
            }
        }



    }

    void acceptTrades(List<LiveTrade> trade) {
        trades.addAll(trade);
    }

    public TradePair getTradePair() {
        return tradepair;
    }

    public void setTradepair(TradePair tradepair) {
        this.tradepair = tradepair;
    }
}
