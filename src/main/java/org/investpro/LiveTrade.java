
package org.investpro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


/**
 * This class represents a live trade.
 */
public class LiveTrade {
    private static final Logger logger = LoggerFactory.getLogger(LiveTrade.class);
    /**
     * A list of trades.
     */
    private final List<LiveTrade> trades = new ArrayList<>();
    /**
     * The trade pair.
     */
    private TradePair tradepair;

    /**
     * Constructs a new LiveTrade object.
     *
     * @param tradePair The trade pair
     * @param price     The price
     * @param size      The size
     * @param side      The side
     * @param tradeId   The trade ID
     * @param time      The time
     */
    public LiveTrade(TradePair tradePair, Money price, Money size, Side side, long tradeId, Instant time) {
        this.tradepair = tradePair;
        trades.add(new LiveTrade(tradePair, price, size, side, tradeId, time));
    }

    /**
     * Returns the LiveTrade with the specified trade pair.
     *
     * @param tradePair The trade pair
     * @return The LiveTrade, or null if not found
     */
    public LiveTrade get(TradePair tradePair) {
        for (LiveTrade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                return trade;

            }
        }
        return null;


    }

    /**
     * Checks if the specified trade pair is present in the list of trades.
     *
     * @param tradePair The trade pair
     * @return True if present, false otherwise
     */
    public boolean containsKey(TradePair tradePair) {
        for (LiveTrade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the trade with the specified trade pair from the list of trades.
     *
     * @param tradePair The trade pair
     */
    public void remove(TradePair tradePair) {
        for (int i = 0; i < trades.size(); i++) {
            if (trades.get(i).getTradePair().equals(tradePair)) {
                trades.remove(i);
            } else {
                logger.error(STR."tradePair: \{tradePair.toString()} not found");
            }

        }

    }

    /**
     * Adds the specified LiveTrade to the list of trades, or updates it if it already exists.
     *
     * @param tradePair          The trade pair
     * @param liveTradesConsumer The consumer that receives the updated list of trades
     */
    public void put(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        for (LiveTrade trade : trades) {
            if (trade.getTradePair().equals(tradePair)) {
                liveTradesConsumer.get(tradePair).acceptTrades(
                        (List<LiveTrade>) trade);
            }
        }


    }

    /**
     * Adds the specified list of trades to the list of trades.
     *
     * @param trade The list of trades
     */
    public void acceptTrades(List<LiveTrade> trade) {
        trades.addAll(trade);
    }

    /**
     * Returns the trade pair.
     *
     * @return The trade pair
     */
    public TradePair getTradePair() {
        return tradepair;
    }

    /**
     * Sets the trade pair.
     *
     * @param tradepair The trade pair
     */
    public void setTradepair(TradePair tradepair) {
        this.tradepair = tradepair;
    }
}