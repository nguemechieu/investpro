package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        return defaultEquityPairs();
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
        return new EmptyCandleDataSupplier(secondsPerCandle, tradePair);
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
        return accountSnapshot();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.completedFuture(accountSnapshot());
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

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount, double limitPrice) {
        return failedFuture(unsupported("createLimitOrder"));
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount, double entryPrice, double stopLoss, double takeProfit) {
        return failedFuture(unsupported("createBracketOrder"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return failedFuture(unsupported("cancelOrder"));
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        return failedFuture(unsupported("cancelOrders"));
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return CompletableFuture.completedFuture("No live broker order adapter configured.");
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return CompletableFuture.completedFuture("No live broker position adapter configured.");
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        throw unsupported("buy");
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        throw unsupported("sell");
    }

    @Override
    public void autoTrading(@NotNull Boolean auto, String signal) {
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return Math.max(0.0, amount);
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return Math.max(0.0, price);
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(unsupported("setLeverage"));
    }

    @Override
    public boolean supportsLiveTrading() {
        return false;
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return false;
    }

    @Override
    public boolean supportsPositions() {
        return true;
    }

    @Override
    public boolean supportsAccountTrades() {
        return true;
    }

    @Override
    public boolean supportsStopLossTakeProfit() {
        return false;
    }

    @Override
    public boolean supportsBracketOrders() {
        return false;
    }

    @Override
    public boolean supportsLeverage() {
        return false;
    }

    @Override
    public boolean supportsDerivatives() {
        return false;
    }

    @Override
    public boolean supportsForex() {
        return false;
    }

    @Override
    public boolean supportsStocks() {
        return true;
    }

    @Override
    public boolean supportsCrypto() {
        return false;
    }

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.POLLING;
    }

    @Override
    public boolean supportsNativeWebSocket() {
        return false;
    }

    @Override
    public boolean supportsHttpStreaming() {
        return false;
    }

    @Override
    public boolean supportsPollingFallback() {
        return true;
    }

    @Override
    public void connectStream() {
        connect();
    }

    @Override
    public void disconnectStream() {
        stopAllStreams();
    }

    @Override
    public boolean isStreamConnected() {
        return isConnected();
    }

    @Override
    public void reconnectStream() {
        reconnect();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        if (subscription == null || consumer == null) {
            return;
        }
        if (!isConnected()) {
            connect();
        }
        consumer.onConnected(getName());
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                streamTicker(pair, consumer);
            }
            if (subscription.isOrderBook()) {
                streamOrderBook(pair, consumer);
            }
        }
        if (subscription.isAccount() || subscription.isBalances()) {
            streamAccount(consumer);
        }
        if (subscription.isOrders() || subscription.isFills()) {
            streamOrders(consumer);
        }
        if (subscription.isPositions()) {
            streamPositions(consumer);
        }
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
        if (subscription == null) {
            return;
        }
        for (TradePair pair : subscription.getTradePairs()) {
            stopTickerStream(pair);
            stopOrderBookStream(pair);
        }
        if (subscription.isAccount() || subscription.isBalances()) {
            stopAccountStream();
        }
        if (subscription.isOrders() || subscription.isFills()) {
            stopOrdersStream();
        }
        if (subscription.isPositions()) {
            stopPositionsStream();
        }
    }

    @Override
    public void stopAllStreams() {
        pollingStreamer.stopAll();
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamTicker(tradePair, consumer);
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrderBook(tradePair, consumer);
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamAccount(consumer);
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamAccount(consumer);
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrders(consumer);
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrders(consumer);
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamPositions(consumer);
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        pollingStreamer.stopTicker(tradePair);
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        pollingStreamer.stopOrderBook(tradePair);
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
    }

    @Override
    public void stopAccountStream() {
        pollingStreamer.stopAccount();
    }

    @Override
    public void stopBalancesStream() {
        pollingStreamer.stopAccount();
    }

    @Override
    public void stopOrdersStream() {
        pollingStreamer.stopOrders();
    }

    @Override
    public void stopFillsStream() {
        pollingStreamer.stopOrders();
    }

    @Override
    public void stopPositionsStream() {
        pollingStreamer.stopPositions();
    }

    @Override
    public boolean supportsAccountStreaming() {
        return true;
    }

    @Override
    public boolean supportsOrderStreaming() {
        return true;
    }

    @Override
    public boolean supportsFillStreaming() {
        return true;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return true;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return true;
    }

    @Override
    public boolean supportsTickerStreaming() {
        return true;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return false;
    }

    @Override
    public boolean supportsCandleStreaming() {
        return false;
    }

    @Override
    public boolean supportsTradeStreaming() {
        return false;
    }

    protected Account accountSnapshot() {
        Account account = Account.forExchange(this);
        account.setConnected(isConnected());
        account.setPaperTrading(isPaperTrading());
        account.setSandbox(isSandbox());
        account.setUpdatedAt(now());
        return account;
    }

    protected List<TradePair> defaultEquityPairs() {
        String[][] symbols = {
                {"AAPL", "USD"}, {"MSFT", "USD"}, {"NVDA", "USD"}, {"TSLA", "USD"},
                {"AMZN", "USD"}, {"GOOGL", "USD"}, {"META", "USD"}, {"SPY", "USD"},
                {"QQQ", "USD"}, {"IWM", "USD"}
        };

        List<TradePair> pairs = new ArrayList<>();
        for (String[] symbol : symbols) {
            try {
                pairs.add(new TradePair(
                        new CryptoCurrency(symbol[0], symbol[0], symbol[0], 2, symbol[0], symbol[0]),
                        new CryptoCurrency(symbol[1], symbol[1], symbol[1], 2, symbol[1], symbol[1])
                ));
            } catch (Exception ignored) {
            }
        }
        return pairs;
    }

    private static final class EmptyCandleDataSupplier extends CandleDataSupplier {
        private EmptyCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(120, secondsPerCandle, tradePair, new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        }

        @Override
        public List<org.investpro.data.CandleData> getCandleData() {
            return List.of();
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new EmptyCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.@NotNull Future<List<CandleData>> get() {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
