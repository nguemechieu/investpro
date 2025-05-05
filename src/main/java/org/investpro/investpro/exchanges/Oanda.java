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
 * Modularized version of Oanda exchange using composition.
 * Each major responsibility is delegated to a dedicated service.
 */
@Getter
@Setter
public class Oanda extends Exchange {

    public static final String API_URL = "https://api-fxtrade.oanda.com/v3";
    private final String accountId;
    private final String apiSecret;
    private final HttpClient httpClient;

    private final OandaOrderService orderService;
    private final OandaAccountService accountService;
    private final OandaMarketDataService marketDataService;
    private final OandaCandleService candleService;
    private final OandaTradeService oandaTradeService;
    private TradePair tradePair;

    public Oanda(String accountId, String apiSecret) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(apiSecret);
        super(accountId, apiSecret);
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();
        this.orderService = new OandaOrderService(accountId, apiSecret, httpClient);
        this.accountService = new OandaAccountService(accountId, apiSecret, httpClient);
        this.marketDataService = new OandaMarketDataService(accountId, apiSecret, httpClient);
        this.candleService = new OandaCandleService(accountId, apiSecret, httpClient, 1000);
        this.oandaTradeService = new OandaTradeService(accountId, apiSecret, httpClient);

    }

    @Override
    public Set<Integer> granularity() {
        return Set.of(
                60, 5 * 60, 15 * 60, 30 * 60, 3600, 2 * 3600, 4 * 3600, 24 * 7 * 3600, 24 * 7 * 3600 * 4
        ); // supported granularity in minutes
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTrades(TradePair tradePair, Instant instant) {
        return oandaTradeService.fetchRecentTrades(tradePair);
    }

    @Override
    public CompletableFuture<List<OrderBook>> getOrderBook(TradePair tradePair, Instant instant) {
        return oandaTradeService.getOrderBook(tradePair.toString('_'));
    }


    @Override
    public String getRecentTrades(TradePair pair) {
        return oandaTradeService.getRecentTrades(pair).orElse("no data found");
    }

    @Override
    public void connectAndProcessTrades(String symbol, InProgressCandleUpdater updater) {

    }


    @Override
    public Double[] getLatestPrice(TradePair pair) {
        return oandaTradeService.getLatestPrice(pair);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTrade(TradePair pair, Instant instant) {
        return oandaTradeService.fetchRecentTrades(pair);
    }


    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return candleService.getCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<List<Optional<?>>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        //  try {
        return null;// CompletableFuture.completedFuture(
//                    (Optional<CandleData>) candleService.fetchCandleDataForInProgressCandle(tradePair,
//                            currentCandleStartedAt, Instant.ofEpochSecond(secondsIntoCurrentCandle),
//                            secondsPerCandle).get())

//        } catch (InterruptedException | ExecutionException e) {
//            throw new RuntimeException(e);
//        }
    }

    // --- Delegated Implementations ---

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
        //return //candleService.fetchCandleDataForInProgressCandle(tradePair, startTime,

        //      endTime, interval);

        return null;
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
    public java.util.List<Fee> getTradingFee() throws IOException, InterruptedException {
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
        return orderService.getOrders(tradePair);
    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException {
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
