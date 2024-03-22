package org.investpro;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Bitfinex extends Exchange {
    public Bitfinex(String s, String s1) {
        super(s, s1);
    }

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return null;
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    Boolean isConnected() {
        return null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
        return null;
    }

    @Override
    public String getTimestamp() {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair) {
        return null;
    }

    @Override
    public String getSignal() {
        return null;
    }

    @Override
    public void connect() {

    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        return null;
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }
}
