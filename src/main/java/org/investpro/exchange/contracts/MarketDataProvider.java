package org.investpro.exchange.contracts;

import org.investpro.data.InProgressCandleData;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MarketDataProvider {

    TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException;

    List<TradePair> getTradePairSymbol() throws SQLException, ClassNotFoundException;

    List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException;

    boolean supportsTradePair(TradePair tradePair);

    double getLivePrice();

    Ticker getLivePrice(TradePair tradePair);

    CompletableFuture<Ticker> fetchTicker(TradePair tradePair);

    CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs);

    CompletableFuture<List<Ticker>> getTicker(TradePair pair);

    CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle);

    CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);

    CompletableFuture<?> getOrderBook(TradePair tradePair);

    CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair);

    /**
     * Fetch order books for multiple trading pairs in batch.
     * Returns a list of order books corresponding to the input trade pairs.
     * 
     * @param tradePairs list of trading pairs to fetch order books for
     * @return CompletableFuture containing list of order books
     */
    CompletableFuture<List<OrderBook>> fetchOrderBooks(List<TradePair> tradePairs);

    String supportsTimeframe(int secondsPerCandle);

    List<Timeframe> getSupportedTimeframes();
}