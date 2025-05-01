package org.investpro.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.*;
import org.investpro.investpro.model.*;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.services.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private static final Logger logger = LoggerFactory.getLogger(Oanda.class);
    private final String accountId;
    private final String apiSecret;
    private final HttpClient httpClient;

    private final OandaOrderService orderService;
    private final OandaAccountService accountService;
    private final OandaMarketDataService marketDataService;
    private final OandaCandleService candleService;

    public Oanda(String accountId, String apiSecret) {
        super(accountId, apiSecret);
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();

        this.orderService = new OandaOrderService(accountId, apiSecret, httpClient);
        this.accountService = new OandaAccountService(accountId, apiSecret, httpClient);
        this.marketDataService = new OandaMarketDataService(accountId, apiSecret, httpClient);
        this.candleService = new OandaCandleService(accountId, apiSecret, httpClient);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return candleService.getCandleDataSupplier(secondsPerCandle, tradePair);
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
    public CompletableFuture<Optional<Candle>> fetchCandleDataForInProgressCandle(@NotNull TradePair pair, Instant start, long offset, int secondsPerCandle) {
        return candleService.fetchCandleDataForInProgressCandle(pair, start, offset, secondsPerCandle);
    }

    @Override
    public java.util.List<Candle> getHistoricalCandles(String symbol, java.time.Instant startTime, java.time.Instant endTime, String interval) {
        return candleService.getHistoricalCandles(symbol, startTime, endTime, interval);
    }

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return marketDataService.fetchOrderBook(tradePair);
    }

    @Override
    public java.util.List<TradePair> getTradePairs() throws Exception {
        return marketDataService.getTradePairs();
    }

    @Override
    public java.util.List<Fee> getTradingFee() throws IOException, InterruptedException {
        return accountService.getTradingFee();
    }

    @Override
    public List<org.investpro.investpro.model.Account> getAccounts() throws IOException, InterruptedException {
        return accountService.getAccounts();
    }

    @Override
    public List<Account> getAccountSummary() {
        return accountService.getAccountSummary();
    }

    @Override
    public java.util.List<Order> getOrders() throws IOException, InterruptedException {
        return orderService.getOrders();
    }

    @Override
    public java.util.List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, ExecutionException, InterruptedException {
        return orderService.getOpenOrder(tradePair);
    }

    @Override
    public java.util.List<Order> getPendingOrders() throws IOException, ExecutionException, InterruptedException {
        return orderService.getPendingOrders();
    }

    @Override
    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType,
                            double price, double size, java.util.Date timestamp,
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
    public java.util.List<Position> getPositions() throws IOException, InterruptedException {
        return accountService.getPositions();
    }
}
