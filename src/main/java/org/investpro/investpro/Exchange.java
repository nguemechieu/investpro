package org.investpro.investpro;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class Exchange {
    protected final ExchangeWebSocketClient webSocketClient;

    protected Exchange(ExchangeWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    /**
     * @return this exchange's {@code ExchangeWebSocketClient} instance, which is responsible for grabbing
     * live-streaming data (such as trades, orders, etc).
     */
    public ExchangeWebSocketClient getWebsocketClient() {
        return webSocketClient;
    }

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */
    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);

    /**
     * Returns the {@code CandleDataSupplier} implementation that will be used to provide pages of candle data for the
     * given {@code secondsPerCandle} and {@code tradePair}.
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    /**
     * Fetches completed candles (of smaller duration than the current {@code secondsPerCandle}) in the duration of
     * the current live-syncing candle.
     * <p>
     * TThis method only needs to be implemented to support live syncing.
     */
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) throws OandaException, InterruptedException {
        throw new UnsupportedOperationException("Exchange: " + this + " does not support fetching candle data" +
                " for in-progress candle");
    }

    public abstract boolean isInputClosed();

    public abstract void abort();


    public RootBidAsk getBidAsk(@NotNull TradePair tradePair) {
        RootBidAsk bidAsk = new RootBidAsk();


        bidAsk.instrument = tradePair.toString('_');
        Ask ask = new Ask();

        bidAsk.asks.add(ask);
        Bid bid = new Bid();
        bidAsk.bids.add(bid);

        return bidAsk;
    }
}
