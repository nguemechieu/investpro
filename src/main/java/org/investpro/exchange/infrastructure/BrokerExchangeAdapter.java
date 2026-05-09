package org.investpro.exchange.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.investpro.data.Account;
import  org.investpro.data.InProgressCandleData;
import org.investpro.exchange.credentials.ExchangeCredentials;
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
    private final java.util.Map<String, Double> paperBalances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> paperOrders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong nextPaperOrderId = new java.util.concurrent.atomic.AtomicLong(1000);

    protected BrokerExchangeAdapter(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        paperBalances.put("USD", 10000.0);
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
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return new Account();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        Account account = new Account();
        double equity = paperBalances.values().stream().mapToDouble(Double::doubleValue).sum();
        account.setTotalBalance(paperBalances.getOrDefault("USD", 0.0));
        account.setAvailableBalance(paperBalances.getOrDefault("USD", 0.0));
        account.setEquity(equity);
        account.setBalances(new java.util.LinkedHashMap<>(paperBalances));
        account.setAvailableBalances(new java.util.LinkedHashMap<>(paperBalances));
        account.setExchangeId(getExchangeId());
        account.setBrokerName(getDisplayName());
        account.setPaperTrading(isPaperTrading());
        account.setConnected(Boolean.TRUE.equals(isConnected()));
        account.setUpdatedAt(Instant.now());
        return CompletableFuture.completedFuture(account);
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(paperBalances.getOrDefault(normalizeCurrency(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAvailableBalance(currencyCode);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(paperBalances.values().stream().mapToDouble(Double::doubleValue).sum());
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchEquity();
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
        return createPaperOrder(tradePair, side, amount, 1.0, "MARKET");
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount, double limitPrice) {
        return createPaperOrder(tradePair, side, amount, limitPrice, "LIMIT");
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

    private CompletableFuture<String> createPaperOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double price,
            String type) {
        if (!supportsPaperTradingMode() || !isPaperTrading()) {
            return failedFuture(unsupported("create%sOrder".formatted(type)));
        }
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("Trade pair is required."));
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            return failedFuture(new IllegalArgumentException("Order amount must be greater than zero."));
        }
        if (!Double.isFinite(price) || price <= 0) {
            return failedFuture(new IllegalArgumentException("Order price must be greater than zero."));
        }

        String base = tradePair.getBaseCode();
        String quote = tradePair.getCounterCode();
        String orderId = "%s-PAPER-%d".formatted(getExchangeId().toUpperCase(java.util.Locale.ROOT),
                nextPaperOrderId.incrementAndGet());

        if (side == Side.SELL) {
            double baseBalance = paperBalances.getOrDefault(base, 0.0);
            if (baseBalance < amount) {
                return failedFuture(new IllegalStateException("Insufficient %s paper balance.".formatted(base)));
            }
            paperBalances.put(base, baseBalance - amount);
            paperBalances.merge(quote, amount * price, Double::sum);
        } else {
            double quoteCost = amount * price;
            double quoteBalance = paperBalances.getOrDefault(quote, 0.0);
            if (quoteBalance < quoteCost) {
                return failedFuture(new IllegalStateException("Insufficient %s paper balance.".formatted(quote)));
            }
            paperBalances.put(quote, quoteBalance - quoteCost);
            paperBalances.merge(base, amount, Double::sum);
        }

        paperOrders.put(orderId, "FILLED");
        return CompletableFuture.completedFuture(orderId);
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? "USD"
                : currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
