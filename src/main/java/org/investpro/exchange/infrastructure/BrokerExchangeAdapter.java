package org.investpro.exchange.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.investpro.data.Account;
import  org.investpro.data.InProgressCandleData;
import  org.investpro.models.trading.Order;
import  org.investpro.models.trading.OrderBook;
import  org.investpro.models.trading.Ticker;
import  org.investpro.models.trading.Trade;
import  org.investpro.models.trading.TradePair;
import  org.investpro.utils.CandleDataSupplier;
import  org.investpro.utils.MARKET_TYPES;
import  org.investpro.utils.Side;
import  org.investpro.exchange.websocket.ExchangeWebSocketClient;
import  org.investpro.exchange.Exchange;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared desktop-safe broker adapter scaffold.
 *
 * It lets brokers appear in the terminal, persist credentials, stream by polling
 * where possible, and fail trading-only features explicitly until a concrete API
 * integration is added.
 */
public abstract class BrokerExchangeAdapter extends Exchange {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final PollingExchangeStreamer pollingStreamer = new PollingExchangeStreamer(this);

    protected BrokerExchangeAdapter(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
    }

    @Override
    public String getSignal() {
        return "HOLD";
    }

    @Override
    public boolean isSandbox() {
        return isPaperTrading();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return getSupportedMarketTypes().contains(marketType);
    }

    @Override
    public void connect() {
        connected.set(true);
    }

    @Override
    public void disconnect() {
        stopAllStreams();
        connected.set(false);
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public Boolean isConnected() {
        return connected.get();
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public boolean supportsWebSocket() {
        return false;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return false;
    }

    @Override
    public String getTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        List<TradePair> pairs = getTradePairSymbol();
        return pairs.isEmpty() ? TradePair.of("AAPL", "USD") : pairs.get(0);
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        return List.of();
    }

    @Override
    public List<TradePair> getTradablePairs() {
        return getTradePairSymbol();
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null;
    }

    @Override
    public double getLivePrice() {
        return 0.0;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return Ticker.empty();
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.completedFuture(Ticker.empty());
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        int size = tradePairs == null ? 0 : tradePairs.size();
        List<Ticker> tickers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tickers.add(Ticker.empty());
        }
        return CompletableFuture.completedFuture(tickers);
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle
    ) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(new OrderBook());
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return "polling";
    }

    @Override
    public double getSize() {
        return 1.0;
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return new Account();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.completedFuture(new Account());
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        return failedFuture(unsupported("createOrder"));
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side, double stopLoss, double takeProfit, double slippage) {
        return createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        return failedFuture(unsupported("createMarketOrder"));
    }

    public void stopAllStreams() {
        pollingStreamer.stopAll();
    }

    @Override
    public boolean supportsTradeStreaming() {
        return false;
    }

    @Override
    public boolean supportsCandleStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return false;
    }
}
