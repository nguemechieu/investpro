package org.investpro.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.*;
import org.investpro.investpro.model.*;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.services.CoinbaseAccountService;
import org.investpro.investpro.services.CoinbaseMarketDataService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class Coinbase extends Exchange {

    public static final String API_URL = "https://api.coinbase.com/api/v3/brokerage";
    private static final Logger logger = LoggerFactory.getLogger(Coinbase.class);
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    private final CoinbaseAccountService accountService;
    private final CoinbaseMarketDataService marketDataService;

    // TODO: CoinbaseOrderService and CoinbaseCandleService can be added similarly

    public Coinbase(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();

        this.accountService = new CoinbaseAccountService(apiKey, apiSecret, httpClient);
        this.marketDataService = new CoinbaseMarketDataService(apiKey, apiSecret, httpClient);
    }

    @Override
    public Set<Integer> granularity() {
        return Set.of(1, 5, 15, 60, 1440); // supported granularities in minutes
    }

    @Override
    public CompletableFuture<Trade> fetchRecentTrades(TradePair tradePair, Instant instant) {
        return null;
    }

    @Override
    public String getRecentTrades(TradePair pair) {
        return "";
    }

    @Override
    public void connectAndProcessTrades(String string, InProgressCandleUpdater updater) {

    }

    @Override
    public Optional<Double> getLatestPrice(TradePair pair) {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Trade> fetchRecentTrade(TradePair pair, Instant instant) {
        return null;
    }


    @Override
    public List<Fee> getTradingFee() {
        return accountService.getTradingFee();
    }

    @Override
    public List<org.investpro.investpro.model.Account> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        return List.of();
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

    // Stub methods (To be implemented or delegated)

    @Override
    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) {
        throw new UnsupportedOperationException("createOrder not yet implemented");
    }


    @Override
    public void cancelOrder(String orderId) {
        throw new UnsupportedOperationException("cancelOrder not yet implemented");
    }

    @Override
    public void cancelAllOrders() {
        throw new UnsupportedOperationException("cancelAllOrders not yet implemented");
    }

    @Override
    public List<Order> getOrders() {
        throw new UnsupportedOperationException("getOrders not yet implemented");
    }

    @Override
    public List<Order> getOpenOrder(TradePair tradePair) {
        throw new UnsupportedOperationException("getOpenOrder not yet implemented");
    }

    @Override
    public List<Order> getPendingOrders() {
        throw new UnsupportedOperationException("getPendingOrders not yet implemented");
    }

    @Override
    public List<TradePair> getTradePairs() throws Exception {
        return marketDataService.getTradePairs();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        throw new UnsupportedOperationException("getCandleDataSupplier not yet implemented");
    }

    @Override
    public CompletableFuture<Optional<CandleData>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant start, long offset, int secondsPerCandle) {
        throw new UnsupportedOperationException("fetchCandleDataForInProgressCandle not yet implemented");
    }

    @Override
    public List<CandleData> getHistoricalCandles(TradePair symbol, Instant startTime, Instant endTime, int interval) {
        throw new UnsupportedOperationException("getHistoricalCandles not yet implemented");
    }
}
