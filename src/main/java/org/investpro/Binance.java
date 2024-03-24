package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Binance extends Exchange {


    public Binance(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);

        this.apiKey = apiKey;
        this.apiSecret = apiSecret;

    }

    @Override
    public TradePair getSelecTradePair() {
        return null;
    }

    @Override
    public void buy(TradePair btcUsd, MARKET_TYPES marketType, double sizes, double stoploss, double takeProfit) {
        super.buy(btcUsd, marketType, sizes, stoploss, takeProfit);
    }

    @Override
    public void sell(TradePair btcUsd, MARKET_TYPES marketType, double sizes, double stopLoss, double takeProfit) {
        super.sell(btcUsd, marketType, sizes, stopLoss, takeProfit);
    }

    @Override
    public void cancelALL() {
        super.cancelALL();
    }

    @Override
    public void autoTrading(@NotNull Boolean auto, String signal) {
        super.autoTrading(auto, signal);
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
    public CompletableFuture<String> createOrder(Orders order) throws JsonProcessingException {
        return null;
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
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
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        return null;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return null;
    }

    @Override
    public Account getUserAccountDetails() {
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

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
