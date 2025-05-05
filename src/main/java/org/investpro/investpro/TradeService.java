package org.investpro.investpro;

import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to trade-related operations for exchanges.
 */
public interface TradeService {

    /**
     * Fetches a list of recent trades for the given trading pair.
     *
     * @param pair The trading pair to fetch trades for.
     * @return A CompletableFuture of a list of trades.
     */
    String getRecentTrades(TradePair pair);

    /**
     * Streams live trades for the specified trading pair and processes each update via a callback.
     *
     * @param symbol  The trading symbol (e.g., BTC_USDT).
     * @param updater Callback to handle incoming in-progress candle data.
     */
    void connectAndProcessTrades(String symbol, InProgressCandleUpdater updater);

    /**
     * Fetches the most recent trade price for a trading pair.
     *
     * @param pair The trading pair.
     * @return Optional price as double.
     */
    Double[] getLatestPrice(TradePair pair);

    /**
     * Fetches a single most recent trade for the specified pair and instant.
     *
     * @param pair    The trading pair.
     * @param instant Timestamp reference.
     * @return A CompletableFuture of a Trade.
     */
    CompletableFuture<List<Trade>> fetchRecentTrade(TradePair pair, Instant instant);
}
