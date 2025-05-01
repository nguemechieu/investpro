package org.investpro.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.Side;
import org.investpro.investpro.model.*;
import org.investpro.investpro.services.BinanceUSAccountService;
import org.investpro.investpro.services.BinanceUSCandleService;
import org.investpro.investpro.services.BinanceUSMarketDataService;
import org.investpro.investpro.services.BinanceUSOrderService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class BinanceUS extends Exchange {

    public static final String API_URL = "https://api.binance.us/api/v3";
    private static final Logger logger = LoggerFactory.getLogger(BinanceUS.class);
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    private final BinanceUSAccountService accountService;
    private final BinanceUSMarketDataService marketDataService;
    private final BinanceUSOrderService orderService;
    private final BinanceUSCandleService candleService;
    private final int timeframe = 3600;
    TradePair tradePair;

    public BinanceUS(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();

        this.accountService = new BinanceUSAccountService(apiKey, apiSecret, httpClient);
        this.marketDataService = new BinanceUSMarketDataService(apiKey, httpClient);
        this.orderService = new BinanceUSOrderService(apiKey, apiSecret, httpClient);

        this.candleService = new BinanceUSCandleService(apiKey, apiSecret, tradePair, timeframe);
    }

    @Override
    public List<Account> getAccounts() throws IOException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        return accountService.getAccounts();
    }

    @Override
    public List<Fee> getTradingFee() {
        return accountService.getTradingFee();
    }

    @Override
    public List<Account> getAccountSummary() {
        return accountService.getAccountSummary();
    }

    @Override
    public List<Position> getPositions() {
        return accountService.getPositions();
    }

    @Override
    public String getExchangeMessage() {
        return marketDataService.getExchangeMessage();
    }

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return marketDataService.fetchOrderBook(tradePair);
    }

    @Override
    public double fetchLivesBidAsk(TradePair tradePair) {
        return marketDataService.fetchLivesBidAsk(tradePair);
    }

    @Override
    public void createOrder(TradePair tradePair, Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, java.util.Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
        orderService.createOrder(tradePair, side, orderType, price, size, timestamp, stopLoss, takeProfit);
    }


    @Override
    public void cancelOrder(String orderId) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        orderService.cancelOrder(orderId, tradePair);
    }

    @Override
    public void cancelAllOrders() {
        orderService.cancelAllOrders();
    }

    @Override
    public List<Order> getOrders() {
        return orderService.getOrders();
    }

    @Override
    public List<Order> getOpenOrder(TradePair tradePair) {
        return orderService.getOpenOrder(tradePair);
    }

    @Override
    public List<Order> getPendingOrders() {
        return orderService.getPendingOrders();
    }

    @Override
    public List<TradePair> getTradePairs() throws Exception {
        return marketDataService.getTradePairs();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return candleService.getCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<Candle>> fetchCandleDataForInProgressCandle(TradePair tradePair, java.time.Instant start, long offset, int secondsPerCandle) {
        return candleService.fetchCandleDataForInProgressCandle(tradePair, start, offset, secondsPerCandle);
    }

    @Override
    public List<Candle> getHistoricalCandles(String symbol, java.time.Instant startTime, java.time.Instant endTime, String interval) {
        return candleService.getHistoricalCandles(symbol, startTime, endTime, interval);
    }
}
