package org.investpro.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.*;
import org.investpro.investpro.model.*;
import org.investpro.investpro.services.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Coinbase exchange implementation using modular services.
 */
@Getter
@Setter
public class Coinbase extends Exchange {

    public static final String API_URL = "https://api.exchange.coinbase.com";
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    private final CoinbaseOrderService orderService;
    private final CoinbaseAccountService accountService;
    private final CoinbaseMarketDataService marketDataService;
    private final CoinbaseCandleService candleService;
    private final CoinbaseTradeService tradeService;
    private TradePair tradePair;

    public Coinbase(String apiKey, String apiSecret) {
        Objects.requireNonNull(apiKey);
        Objects.requireNonNull(apiSecret);
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();
        this.orderService = new CoinbaseOrderService(apiKey, apiSecret, httpClient);
        this.accountService = new CoinbaseAccountService(apiKey, apiSecret, httpClient);
        this.marketDataService = new CoinbaseMarketDataService(apiKey, apiSecret, httpClient);
        this.candleService = new CoinbaseCandleService(apiKey, apiSecret, httpClient, 1000);
        this.tradeService = new CoinbaseTradeService(apiKey, apiSecret, httpClient);
    }

    @Override
    public Set<Integer> granularity() {
        return Set.of(60, 300, 900, 1800, 3600, 7200, 14400, 86400, 604800);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTrades(TradePair tradePair, Instant instant) {
        return tradeService.fetchRecentTrades(tradePair);
    }

    @Override
    public CompletableFuture<List<OrderBook>> getOrderBook(TradePair tradePair, Instant instant) {
        return tradeService.getOrderBook(tradePair);
    }

    @Override
    public String getRecentTrades(TradePair pair) {
        return tradeService.getRecentTrades(pair).orElse("no data found");
    }

    @Override
    public void connectAndProcessTrades(String symbol, InProgressCandleUpdater updater) {
        tradeService.streamLiveTrades(symbol, updater);
    }

    @Override
    public Double[] getLatestPrice(TradePair pair) {
        return tradeService.getLatestPrice(pair);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTrade(TradePair pair, Instant instant) {
        return tradeService.fetchRecentTrades(pair);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return candleService.getCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<List<Optional<?>>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(List.of(tradeService.fetchCandleDataForInProgressCandle(tradePair, currentCandleStartedAt, secondsIntoCurrentCandle
                , secondsPerCandle)));
    }


    @Override
    public String getExchangeMessage() {
        return marketDataService.getExchangeMessage();
    }

    @Override
    public double fetchLivesBidAsk(TradePair tradePair) {
        return marketDataService.fetchLivesBidAsk(tradePair);
    }

    @Override
    public CompletableFuture<Optional<?>> getHistoricalCandles(TradePair tradePair, Instant startTime, @NotNull Instant endTime, int interval) {
        return CompletableFuture.completedFuture(tradeService.fetchCandleDataForInProgressCandle(tradePair, startTime, endTime.getEpochSecond(), interval));

    }

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return marketDataService.fetchOrderBook(tradePair);
    }

    @Override
    public List<TradePair> getTradePairs() throws Exception {
        return marketDataService.getTradePairs();
    }

    @Override
    public List<Fee> getTradingFee() throws IOException, InterruptedException {
        return accountService.getTradingFee();
    }

    @Override
    public List<Account> getAccounts() throws IOException, InterruptedException {
        return accountService.getAccounts();
    }

    @Override
    public List<Account> getAccountSummary() {
        return accountService.getAccountSummary();
    }

    @Override
    public List<Order> getOrders() throws IOException, InterruptedException {
        try {
            return orderService.getOrders(tradePair);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        return orderService.getOpenOrder(tradePair);
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, ExecutionException, InterruptedException {
        return orderService.getPendingOrders();
    }

    @Override
    public void createOrder(TradePair tradePair, @NotNull Side side, ENUM_ORDER_TYPE orderType,
                            double price, double size, Date timestamp,
                            double stopLoss, double takeProfit) throws IOException, InterruptedException {
        orderService.createOrder(tradePair, side, orderType, price, size, timestamp, stopLoss, takeProfit);
    }

    @Override
    public void cancelOrder(String orderId) throws IOException, InterruptedException {
        orderService.cancelOrder(orderId);
    }

    @Override
    public void cancelAllOrders() throws IOException, InterruptedException {
        orderService.cancelAllOrders();
    }

    @Override
    public List<Position> getPositions() throws IOException, InterruptedException {
        return accountService.getPositions();
    }
}
