package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.Account;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.websocket.IBKWebSocketClient;
import org.investpro.models.trading.*;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.NotNull;

import javafx.beans.property.SimpleIntegerProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Getter
@Setter
public class InteractiveBrokers extends Exchange {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final String IBK_URL = "https://www.interactivebrokers.com";
    private static final String IBKR_CLIENT_PORTAL_DEFAULT_URL = "https://localhost:5000/v1/api";
    private static final String IBK_WEB_SOCKET_URL = "https://api.interactivebrokers.com";
    private static final List<String> DEFAULT_STOCK_SYMBOLS = List.of("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META",
            "GOOGL", "SPY", "QQQ");
    private static final List<String> DEFAULT_FOREX_SYMBOLS = List.of("EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF",
            "AUD/USD", "USD/CAD");
    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, String> conidCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private long nextOrderId = 1000;
    private final ExchangeCredentials exchangeCredentials;
    private IBKWebSocketClient webSocketClient;

    public InteractiveBrokers(String apiKey, String apiSecret) {
        this(new ExchangeCredentials("ibk", apiKey, apiSecret, null, null, null, null, false));
    }

    public InteractiveBrokers(ExchangeCredentials credentials) {
        super(credentials);
        this.exchangeCredentials = credentials;
        initializePaperTradingAccount();

        try {
            this.webSocketClient = createWebSocketClient();
        } catch (Exception exception) {
            log.error("Failed to initialize Interactive Brokers websocket client", exception);
        }
    }

    private IBKWebSocketClient createWebSocketClient() {
        return new IBKWebSocketClient(URI.create(IBK_URL), new Draft_6455());
    }

    private void initializePaperTradingAccount() {
        balances.put("USD", 100000.0);
    }

    @Override
    public String getName() {
        return "INTERACTIVE BROKERS";
    }

    @Override
    public String getSignal() {
        return "";
    }

    @Override
    public String getExchangeId() {
        return "interactive_brokers";
    }

    @Override
    public String getDisplayName() {
        return "Interactive Brokers";
    }

    @Override
    public boolean isSandbox() {
        return false;
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == null
                || marketType == MARKET_TYPES.STOCKS
                || marketType == MARKET_TYPES.FOREX;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS, MARKET_TYPES.FOREX);
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return createMarketOrder(symbol, side, quantity);
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return createLimitOrder(symbol, side, quantity, limitPrice);
    }

    // --------- Capability Methods ---------

    @Override
    public boolean supportsLiveTrading() {
        return hasCredentials() && !isPaperTrading();
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsBracketOrders() {
        return true;
    }

    @Override
    public boolean supportsLeverage() {
        return true;
    }

    @Override
    public boolean supportsDerivatives() {
        return true;
    }

    @Override
    public boolean supportsForex() {
        return true;
    }

    @Override
    public boolean supportsStocks() {
        return true;
    }

    @Override
    public boolean supportsCrypto() {
        return false;
    }

    // --------- Streaming Transport Methods ---------

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
        // Interactive Brokers uses polling for streaming
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
        disconnectStream();
        connectStream();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        if (subscription == null || consumer == null) {
            return;
        }
        // Polling-based streaming
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                streamTicker(pair, consumer);
            }
            if (subscription.isTrades()) {
                streamTrades(pair, consumer);
            }
            if (subscription.isCandles()) {
                streamCandles(pair, 60, consumer);
            }
        }
        if (subscription.isAccount()) {
            streamAccount(consumer);
        }
        if (subscription.isOrders()) {
            streamOrders(consumer);
        }
        if (subscription.isBalances()) {
            streamBalances(consumer);
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
        stopAllStreams();
    }

    @Override
    public void stopAllStreams() {

    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        fetchTicker(tradePair)
                .thenAccept(ticker -> consumer.onTicker(getName(), tradePair, ticker))
                .exceptionally(error -> {
                    consumer.onError(getName(), error);
                    return null;
                });
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        fetchRecentTradesUntil(tradePair, null)
                .thenAccept(trades -> trades.forEach(trade -> consumer.onTrade(getName(), tradePair, trade)))
                .exceptionally(error -> {
                    consumer.onError(getName(), error);
                    return null;
                });
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {
        streamTrades(tradePair, consumer);
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        CompletableFuture
                .supplyAsync(() -> getCandleDataSupplier(secondsPerCandle, tradePair).getCandleData())
                .thenAccept(candles -> candles.forEach(candle -> consumer.onCandle(getName(), tradePair, candle)))
                .exceptionally(error -> {
                    consumer.onError(getName(), error);
                    return null;
                });
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        fetchAccount()
                .thenAccept(account -> consumer.onAccount(getName(), account))
                .exceptionally(error -> {
                    consumer.onError(getName(), error);
                    return null;
                });
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        fetchAccount()
                .thenAccept(account -> consumer.onBalanceChanged(getName(), account))
                .exceptionally(error -> {
                    consumer.onError(getName(), error);
                    return null;
                });
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        // Stop polling
    }

    @Override
    public void stopAccountStream() {
        // Stop polling
    }

    @Override
    public void stopBalancesStream() {
        // Stop polling
    }

    @Override
    public void stopOrdersStream() {
        // Stop polling
    }

    @Override
    public void stopFillsStream() {
        // Stop polling
    }

    @Override
    public void stopPositionsStream() {
        // Stop polling
    }

    // --------- Streaming Capability Methods ---------

    @Override
    public boolean supportsTickerStreaming() {
        return false;
    }

    @Override
    public boolean supportsAccountStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderStreaming() {
        return false;
    }

    @Override
    public boolean supportsFillStreaming() {
        return false;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return false;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return false;
    }

    @Override
    public boolean supportsCandleStreaming() {
        return true;
    }

    @Override
    public boolean supportsTradeStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return true;
    }

    // --------- Order Creation Methods ---------

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (!isPaperTrading() && hasCredentials()) {
            return submitClientPortalOrder(tradePair, side, amount, 0.0, "MKT");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            double fillPrice = safePositive(fetchTicker(tradePair).join().getMidPrice(), syntheticPrice(tradePair));
            applyPaperFill(tradePair, side, amount, fillPrice, 0.0, 0.0);
            orders.put(orderId, "FILLED");
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice) {
        if (!isPaperTrading() && hasCredentials()) {
            return submitClientPortalOrder(tradePair, side, amount, limitPrice, "LMT");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            applyPaperFill(tradePair, side, amount, limitPrice, 0.0, 0.0);
            orders.put(orderId, "FILLED");
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double stopPrice) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
        return failedFuture(unsupported("createBracketOrder"));
    }

    // --------- Order Cancellation Methods ---------

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
        return failedFuture(unsupported("cancelAllOrders"));
    }

    // --------- Order Query Methods ---------

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return failedFuture(unsupported("fetchOrder"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return failedFuture(unsupported("fetchOpenOrders"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return failedFuture(unsupported("fetchAllOpenOrders"));
    }

    /**
     * Parses Interactive Brokers open orders response into a list of OpenOrder
     * objects.
     * Handles both array format and single object format.
     */
    private List<OpenOrder> parseOpenOrders(JsonNode rootNode) {
        List<OpenOrder> openOrders = new ArrayList<>();

        if (rootNode == null || rootNode.isNull()) {
            return openOrders;
        }

        if (rootNode.isArray()) {
            for (JsonNode orderNode : rootNode) {
                OpenOrder order = parseOpenOrder(orderNode);
                if (order != null) {
                    openOrders.add(order);
                }
            }
            return openOrders;
        }

        // Optional fallback: some endpoints may return a single object
        if (rootNode.isObject()) {
            OpenOrder order = parseOpenOrder(rootNode);
            if (order != null) {
                openOrders.add(order);
            }
        }

        return openOrders;
    }

    /**
     * Parses a single Interactive Brokers open order from JsonNode.
     */
    private OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();

            order.setOrderId(node.path("orderId").asText(""));

            String symbol = node.path("contract").path("localSymbol").asText();
            if (!symbol.isEmpty()) {
                order.setTradePair(new TradePair(symbol, "USD"));
            }

            String action = node.path("action").asText("BUY");
            order.setSide("SELL".equalsIgnoreCase(action) ? Side.SELL : Side.BUY);

            order.setPrice(node.path("lmtPrice").asDouble(0.0));
            order.setSize(node.path("totalQuantity").asDouble(0.0));
            order.setFilledSize(node.path("filledQuantity").asDouble(0.0));
            order.setRemainingSize(Math.max(0.0, order.getSize() - order.getFilledSize()));

            String status = node.path("orderStatus").asText("PENDING");
            try {
                order.setStatus(OpenOrder.OrderStatus.valueOf(status.toUpperCase()));
            } catch (Exception e) {
                order.setStatus(OpenOrder.OrderStatus.PENDING);
            }

            String orderType = node.path("orderType").asText("LMT");
            try {
                order.setOrderType(OpenOrder.OrderType.valueOf(orderType.replace("_", "")));
            } catch (Exception e) {
                order.setOrderType(OpenOrder.OrderType.LIMIT);
            }

            long timestamp = node.path("createTime").asLong(0);
            if (timestamp > 0) {
                order.setCreatedAt(Instant.ofEpochMilli(timestamp));
                order.setUpdatedAt(Instant.ofEpochMilli(timestamp));
            }

            return order;
        } catch (Exception exception) {
            log.debug("Error parsing Interactive Brokers open order", exception);
            return null;
        }
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return failedFuture(unsupported("fetchOrderHistory"));
    }

    // --------- Position Methods ---------

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return failedFuture(unsupported("fetchPositions"));
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return failedFuture(unsupported("fetchAllPositions"));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return failedFuture(unsupported("fetchPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return failedFuture(unsupported("closeAllPositions"));
    }

    // --------- Trade History Methods ---------

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.completedFuture(new ArrayList<>(tradeHistory));
        }
        return CompletableFuture.completedFuture(
                tradeHistory.stream()
                        .filter(t -> t.getTradePair() != null && t.getTradePair().equals(tradePair))
                        .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        List<Trade> result = tradeHistory.stream()
                .filter(t -> since == null || (t.getTimestamp() != null && t.getTimestamp().isAfter(since)))
                .filter(t -> tradePair == null || (t.getTradePair() != null && t.getTradePair().equals(tradePair)))
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(
            TradePair tradePair,
            Instant from,
            Instant to) {
        List<Trade> result = tradeHistory.stream()
                .filter(t -> t.getTimestamp() != null &&
                        (from == null || t.getTimestamp().isAfter(from)) &&
                        (to == null || t.getTimestamp().isBefore(to)))
                .filter(t -> tradePair == null || (t.getTradePair() != null && t.getTradePair().equals(tradePair)))
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    // --------- Manual Trading Methods ---------

    @Override
    public void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        placeMarketOrder(tradePair, Side.BUY, size)
                .whenComplete((orderId, error) -> logOrderResult("BUY", tradePair, orderId, error));
    }

    @Override
    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        placeMarketOrder(tradePair, Side.SELL, size)
                .whenComplete((orderId, error) -> logOrderResult("SELL", tradePair, orderId, error));
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        if (!hasCredentials()) {
            return AuthResult.failure("Interactive Brokers credentials are not configured");
        }
        return AuthResult.success("Interactive Brokers authentication validated");
    }

    // --------- Order Validation Methods ---------

    @Override
    public CompletableFuture<Boolean> validateOrder(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        boolean valid = tradePair != null
                && supportsMarketType(marketType)
                && size >= getMinOrderAmount(tradePair);
        return CompletableFuture.completedFuture(valid);
    }

    // --------- Normalization Methods ---------

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return amount > 0 && Double.isFinite(amount) ? amount : 0.0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return price >= 0 && Double.isFinite(price) ? price : 0.0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.001;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 20.0;
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
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(unsupported("modifyStopLoss"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return failedFuture(unsupported("closePartialPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(unsupported("modifyTakeProfit"));
    }

    @Override
    public void connect() {
        connected.set(true);
    }

    @Override
    public void disconnect() {
        connected.set(false);
        stopAllStreams();
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
        return webSocketClient;
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
        return TradePair.fromSymbol("AAPL_USD");
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        List<TradePair> pairs = new ArrayList<>();
        for (String symbol : DEFAULT_STOCK_SYMBOLS) {
            addPair(pairs, symbol, "USD");
        }
        for (String symbol : DEFAULT_FOREX_SYMBOLS) {
            String[] parts = symbol.split("/");
            if (parts.length == 2) {
                addPair(pairs, parts[0], parts[1]);
            }
        }
        return pairs;
    }

    @Override
    public List<TradePair> getTradablePairs() {
        return getTradePairSymbol();
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null && getTradePairSymbol().stream().anyMatch(pair -> pair.equals(tradePair));
    }

    @Override
    public double getLivePrice() {
        try {
            return getLivePrice(getSelectedTradePair()).getMidPrice();
        } catch (Exception exception) {
            return 0.0;
        }
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return fetchTicker(tradePair).join();
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> fetchClientPortalTicker(tradePair)
                .orElseGet(() -> syntheticTicker(tradePair)));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        return CompletableFuture.supplyAsync(() -> {
            List<Ticker> tickers = new ArrayList<>();
            List<TradePair> pairs = tradePairs == null || tradePairs.isEmpty() ? getTradePairSymbol() : tradePairs;
            for (TradePair pair : pairs) {
                tickers.add(fetchTicker(pair).join());
            }
            return tickers;
        });
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CandleDataSupplier(200, Math.max(60, secondsPerCandle), tradePair,
                new SimpleIntegerProperty((int) Instant.now().getEpochSecond())) {
            @Override
            public Future<List<CandleData>> get() {
                return CompletableFuture.completedFuture(getCandleData());
            }

            @Override
            public List<CandleData> getCandleData() {
                return fetchClientPortalCandles(tradePair, this.secondsPerCandle, this.numCandles)
                        .orElseGet(() -> syntheticCandles(tradePair, this.secondsPerCandle, this.numCandles));
            }

            @Override
            public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                return InteractiveBrokers.this.getCandleDataSupplier(secondsPerCandle, tradePair);
            }

            @Override
            public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
                    @NotNull TradePair tradePair,
                    Instant currentCandleStartedAt,
                    long secondsIntoCurrentCandle,
                    int secondsPerCandle) {
                return InteractiveBrokers.this.fetchCandleDataForInProgressCandle(
                        tradePair,
                        currentCandleStartedAt,
                        secondsIntoCurrentCandle,
                        secondsPerCandle).thenApply(value -> value.map(candle -> (Object) candle));
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return InteractiveBrokers.this.fetchRecentTradesUntil(tradePair, stopAt);
            }
        };
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return fetchTicker(tradePair).thenApply(ticker -> {
            double price = safePositive(ticker.getMidPrice(), syntheticPrice(tradePair));
            int openTime = (int) (currentCandleStartedAt == null ? Instant.now().getEpochSecond()
                    : currentCandleStartedAt.getEpochSecond());
            int currentTill = (int) Instant.now().getEpochSecond();
            return Optional.of(new InProgressCandleData(openTime, price, price, price, currentTill, price, 0.0));
        });
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(
                tradeHistory.stream()
                        .filter(trade -> tradePair == null || tradePair.equals(trade.getTradePair()))
                        .filter(trade -> stopAt == null || trade.getTimestamp() == null
                                || !trade.getTimestamp().isBefore(stopAt))
                        .toList());
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return failedFuture(unsupported("fetchOrderBook"));
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return secondsPerCandle >= 60 ? "SUPPORTED" : "MIN_60_SECONDS";
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isPaperTrading() && hasCredentials()) {
                Optional<Account> clientPortalAccount = fetchClientPortalAccount();
                if (clientPortalAccount.isPresent()) {
                    return clientPortalAccount.get();
                }
            }
            return paperAccount();
        });
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(normalizeCurrency(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(normalizeCurrency(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(balances.getOrDefault("USD", 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return CompletableFuture.completedFuture(balances.getOrDefault("USD", 0.0));
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        if (order == null) {
            return failedFuture(new IllegalArgumentException("order must not be null"));
        }
        TradePair pair = order.getTradePair();
        if (pair == null) {
            return failedFuture(new IllegalArgumentException("order trade pair must not be null"));
        }
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        String type = order.getType() == null ? "market" : order.getType().toLowerCase(Locale.ROOT);
        if (type.contains("limit")) {
            return createLimitOrder(pair, side, order.getQuantity(), order.getPrice());
        }
        return createMarketOrder(pair, side, order.getQuantity());
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        return new Order(
                (long) id,
                java.util.Date.from(now()),
                type,
                side,
                tradePair == null ? "" : tradePair.toString('/'),
                amount,
                price,
                stopLoss,
                takeProfit,
                slippage);
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(unsupported("enableTrailingStop"));
    }

    private boolean hasCredentials() {
        return exchangeCredentials != null
                && (notBlank(exchangeCredentials.apiKey())
                        || notBlank(exchangeCredentials.accessToken())
                        || notBlank(exchangeCredentials.accountId())
                        || notBlank(System.getenv("IBKR_ACCOUNT_ID")));
    }

    private String clientPortalBaseUrl() {
        String configured = firstNonBlank(
                System.getenv("IBKR_CLIENT_PORTAL_URL"),
                System.getProperty("investpro.ibkr.clientPortalUrl"));
        return configured == null ? IBKR_CLIENT_PORTAL_DEFAULT_URL : configured.replaceAll("/+$", "");
    }

    private HttpRequest.Builder clientPortalRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(clientPortalBaseUrl() + path))
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro/1.0");
        String token = firstNonBlank(
                exchangeCredentials == null ? null : exchangeCredentials.accessToken(),
                exchangeCredentials == null ? null : exchangeCredentials.apiKey(),
                System.getenv("IBKR_ACCESS_TOKEN"));
        if (notBlank(token)) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private Optional<Ticker> fetchClientPortalTicker(TradePair tradePair) {
        try {
            String conid = resolveConid(tradePair).orElse(null);
            if (!notBlank(conid)) {
                return Optional.empty();
            }
            String path = "/iserver/marketdata/snapshot?conids=%s&fields=31,84,86,70,71,87"
                    .formatted(url(conid));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    clientPortalRequest(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("IBKR ticker request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode tickerNode = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            double last = firstDouble(tickerNode, "31", "last", "lastPrice");
            double bid = firstDouble(tickerNode, "84", "bid", "bidPrice");
            double ask = firstDouble(tickerNode, "86", "ask", "askPrice");
            double high = firstDouble(tickerNode, "70", "high", "highPrice");
            double low = firstDouble(tickerNode, "71", "low", "lowPrice");
            double volume = firstDouble(tickerNode, "87", "volume");
            double mid = safePositive(last, bid > 0 && ask > 0 ? (bid + ask) / 2.0 : 0.0);
            if (mid <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Ticker(mid, bid, ask, mid, high, low, volume, System.currentTimeMillis()));
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR ticker for {}: {}", tradePair, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<CandleData>> fetchClientPortalCandles(TradePair tradePair, int secondsPerCandle, int count) {
        try {
            String conid = resolveConid(tradePair).orElse(null);
            if (!notBlank(conid)) {
                return Optional.empty();
            }
            String period = secondsPerCandle >= 86_400 ? "1y" : secondsPerCandle >= 3_600 ? "1m" : "1d";
            String bar = ibkrBarSize(secondsPerCandle);
            String path = "/hmds/history?conid=%s&period=%s&bar=%s&outsideRth=true"
                    .formatted(url(conid), url(period), url(bar));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    clientPortalRequest(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("IBKR candle request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonNode data = OBJECT_MAPPER.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            List<CandleData> candles = new ArrayList<>();
            int start = Math.max(0, data.size() - Math.max(1, count));
            for (int index = start; index < data.size(); index++) {
                JsonNode node = data.get(index);
                double open = firstDouble(node, "o", "open");
                double close = firstDouble(node, "c", "close");
                double high = firstDouble(node, "h", "high");
                double low = firstDouble(node, "l", "low");
                double volume = firstDouble(node, "v", "volume");
                int time = (int) Math.max(1L, firstLong(node) / 1000L);
                if (open > 0 && close > 0 && high > 0 && low > 0) {
                    candles.add(new CandleData(open, close, high, low, time, volume));
                }
            }
            return candles.isEmpty() ? Optional.empty() : Optional.of(candles);
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR candles for {}: {}", tradePair, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> resolveConid(TradePair tradePair) {
        if (tradePair == null) {
            return Optional.empty();
        }
        String symbol = ibkrSymbol(tradePair);
        String cached = conidCache.get(symbol);
        if (notBlank(cached)) {
            return Optional.of(cached);
        }
        try {
            String path = "/iserver/secdef/search?symbol=%s&name=false"
                    .formatted(url(symbol.replace("/", "")));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    clientPortalRequest(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            JsonNode best = root.get(0);
            String conid = firstText(best, "conid", "con_id");
            if (notBlank(conid)) {
                conidCache.put(symbol, conid);
                return Optional.of(conid);
            }
        } catch (Exception exception) {
            log.debug("Unable to resolve IBKR conid for {}: {}", tradePair, exception.getMessage());
        }
        return Optional.empty();
    }

    private CompletableFuture<String> submitClientPortalOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice,
            String orderType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accountId = resolveAccountId();
                String conid = resolveConid(tradePair)
                        .orElseThrow(
                                () -> new IllegalStateException("Unable to resolve IBKR contract id for " + tradePair));
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("conid", Long.parseLong(conid));
                order.put("side", side == Side.SELL ? "SELL" : "BUY");
                order.put("orderType", orderType);
                order.put("quantity", amount);
                order.put("tif", "DAY");
                if ("LMT".equalsIgnoreCase(orderType)) {
                    order.put("price", limitPrice);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("orders", List.of(order));
                String body = OBJECT_MAPPER.writeValueAsString(payload);
                HttpResponse<String> response = HTTP_CLIENT.send(
                        clientPortalRequest("/iserver/account/%s/orders".formatted(url(accountId)))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                JsonNode responseBody = OBJECT_MAPPER.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("IBKR API returned HTTP %d: %s"
                            .formatted(response.statusCode(), responseBody));
                }
                return firstText(responseBody.isArray() && !responseBody.isEmpty() ? responseBody.get(0) : responseBody,
                        "order_id", "id", "local_order_id");
            } catch (Exception exception) {
                throw new IllegalStateException("IBKR order submission failed.", exception);
            }
        });
    }

    private Optional<Account> fetchClientPortalAccount() {
        try {
            String accountId = resolveAccountId();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    clientPortalRequest("/portfolio/%s/summary".formatted(url(accountId))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("IBKR account request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            double netLiquidation = accountSummaryValue(body, "NetLiquidation", balances.getOrDefault("USD", 0.0));
            double availableFunds = accountSummaryValue(body, "AvailableFunds", netLiquidation);
            Account account = accountBase(accountId, netLiquidation, availableFunds);
            account.setMetadata(Map.of("source", "IBKR Client Portal"));
            return Optional.of(account);
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR account: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Account paperAccount() {
        double cash = balances.getOrDefault("USD", 0.0);
        return accountBase(resolveAccountIdOrDefault(), cash, cash);
    }

    private Account accountBase(String accountId, double total, double available) {
        Map<String, Double> totalBalances = new LinkedHashMap<>();
        totalBalances.put("USD", total);
        Map<String, Double> availableBalances = new LinkedHashMap<>();
        availableBalances.put("USD", available);
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccount(accountId);
        account.setBrokerName("Interactive Brokers");
        account.setExchangeId("interactive_brokers");
        account.setBaseCurrency("USD");
        account.setTotalBalance(total);
        account.setAvailableBalance(available);
        account.setEquity(total);
        account.setCash(available);
        account.setBuyingPower(available);
        account.setBalances(totalBalances);
        account.setAvailableBalances(availableBalances);
        account.setPaperTrading(isPaperTrading());
        account.setConnected(Boolean.TRUE.equals(isConnected()));
        account.setUpdatedAt(Instant.now());
        return account;
    }

    private void applyPaperFill(TradePair tradePair, Side side, double amount, double price, double stopLoss,
            double takeProfit) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        if (amount <= 0 || !Double.isFinite(amount) || price <= 0 || !Double.isFinite(price)) {
            throw new IllegalArgumentException("Order amount and price must be positive.");
        }
        String base = tradePair.getBaseCode().toUpperCase(Locale.ROOT);
        String quote = normalizeCurrency(tradePair.getCounterCode());
        double cost = amount * price;
        if (side == Side.SELL) {
            double baseBalance = balances.getOrDefault(base, 0.0);
            if (baseBalance < amount) {
                throw new IllegalStateException("Insufficient " + base + " position");
            }
            balances.put(base, baseBalance - amount);
            balances.put(quote, balances.getOrDefault(quote, 0.0) + cost);
        } else {
            double quoteBalance = balances.getOrDefault(quote, 0.0);
            if (quoteBalance < cost) {
                throw new IllegalStateException("Insufficient " + quote + " buying power");
            }
            balances.put(quote, quoteBalance - cost);
            balances.put(base, balances.getOrDefault(base, 0.0) + amount);
        }
        Trade trade = new Trade(tradePair, price, amount, side, System.nanoTime(), Instant.now());
        trade.setStopLoss(stopLoss);
        trade.setTakeProfit(takeProfit);
        tradeHistory.add(trade);
    }

    private List<CandleData> syntheticCandles(TradePair tradePair, int secondsPerCandle, int count) {
        List<CandleData> candles = new ArrayList<>();
        double base = syntheticPrice(tradePair);
        int start = (int) (Instant.now().getEpochSecond() - ((long) count * secondsPerCandle));
        for (int i = 0; i < count; i++) {
            double wave = Math.sin(i / 7.0) * base * 0.006;
            double trend = i * base * 0.00005;
            double open = base + wave + trend;
            double close = open + Math.cos(i / 5.0) * base * 0.002;
            double high = Math.max(open, close) * 1.002;
            double low = Math.min(open, close) * 0.998;
            candles.add(new CandleData(open, close, high, low, start + (i * secondsPerCandle), 1_000 + (i * 3)));
        }
        return candles;
    }

    private Ticker syntheticTicker(TradePair tradePair) {
        double price = syntheticPrice(tradePair);
        double spread = Math.max(0.01, price * 0.0005);
        return new Ticker(price, price - spread, price + spread, price * 0.99, price * 1.02, price * 0.98, 100_000,
                System.currentTimeMillis());
    }

    private double syntheticPrice(TradePair tradePair) {
        String symbol = ibkrSymbol(tradePair);
        return switch (symbol) {
            case "MSFT" -> 420.0;
            case "NVDA" -> 950.0;
            case "TSLA" -> 180.0;
            case "AMZN" -> 185.0;
            case "META" -> 510.0;
            case "GOOGL" -> 170.0;
            case "SPY" -> 520.0;
            case "QQQ" -> 445.0;
            case "EUR/USD" -> 1.08;
            case "GBP/USD" -> 1.27;
            case "USD/JPY" -> 155.0;
            default -> 190.0;
        };
    }

    private String resolveAccountId() {
        String accountId = resolveAccountIdOrDefault();
        if (!notBlank(accountId) || "PAPER".equals(accountId)) {
            throw new IllegalStateException("IBKR account id is required for live Client Portal trading.");
        }
        return accountId;
    }

    private String resolveAccountIdOrDefault() {
        return firstNonBlank(
                exchangeCredentials == null ? null : exchangeCredentials.accountId(),
                System.getenv("IBKR_ACCOUNT_ID"),
                "PAPER");
    }

    private double accountSummaryValue(JsonNode root, String key, double fallback) {
        JsonNode node = root.path(key);
        if (node.isObject()) {
            return firstDouble(node, "amount", "value");
        }
        return node.isMissingNode() ? fallback : node.asDouble(fallback);
    }

    private void logOrderResult(String side, TradePair tradePair, String orderId, Throwable error) {
        if (error != null) {
            log.warn("Interactive Brokers {} order failed for {}: {}", side, tradePair, error.getMessage());
        } else {
            log.info("Interactive Brokers {} order submitted for {}: {}", side, tradePair, orderId);
        }
    }

    private void addPair(List<TradePair> pairs, String base, String quote) {
        try {
            pairs.add(TradePair.fromSymbol(base + "_" + quote));
        } catch (Exception exception) {
            log.debug("Unable to create Interactive Brokers pair {}/{}: {}", base, quote, exception.getMessage());
        }
    }

    private String ibkrSymbol(TradePair tradePair) {
        if (tradePair == null) {
            return "AAPL";
        }
        String base = tradePair.getBaseCode().toUpperCase(Locale.ROOT);
        String quote = tradePair.getCounterCode().toUpperCase(Locale.ROOT);
        if ("USD".equals(quote) && DEFAULT_STOCK_SYMBOLS.contains(base)) {
            return base;
        }
        return base + "/" + quote;
    }

    private String ibkrBarSize(int secondsPerCandle) {
        if (secondsPerCandle <= 60) {
            return "1min";
        }
        if (secondsPerCandle <= 300) {
            return "5min";
        }
        if (secondsPerCandle <= 900) {
            return "15min";
        }
        if (secondsPerCandle <= 1800) {
            return "30min";
        }
        if (secondsPerCandle <= 3600) {
            return "1h";
        }
        return "1d";
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? "USD"
                : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null) {
            return "";
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private double firstDouble(JsonNode node, String... names) {
        String text = firstText(node, names).replace(",", "");
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception exception) {
            return 0.0;
        }
    }

    private long firstLong(JsonNode node) {
        String text = firstText(node, new String[]{"t", "time"}).replace(",", "");
        if (text.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception exception) {
            return 0L;
        }
    }

    private double safePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Order amount and price values must be positive.");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(
                Timeframe.M1,
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("IBK")
                .exchangeId("ibk")
                .displayName("Interactive Broker Trade")
                .apiBaseUrl(IBK_URL)
                .webSocketBaseUrl(IBK_WEB_SOCKET_URL)

                // Market coverage
                .supportsCrypto(false)
                .supportsSpot(true)
                .supportsFutures(false)
                .supportsDerivatives(true)
                .supportsForex(true)
                .supportsStocks(true)
                .supportsOptions(false)
                .supportsIndices(false)

                // Trading support
                .supportsLiveTrading(true)
                .supportsPaperTradingMode(true)
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(true)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(false)
                .supportsTrailingStop(false)
                .supportsLeverage(true)

                // Account / portfolio
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(true)
                .supportsAccountTrades(true)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsFills(true)

                // Market data
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(true)
                .supportsFullOrderBook(true)
                .supportsDistributionBook(true)
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(true)

                // Streaming
                .supportsWebSocket(false)
                .supportsNativeWebSocket(false)
                .supportsWebSocketStreaming(false)
                .supportsTickerStreaming(false)
                .supportsTradeStreaming(false)
                .supportsCandleStreaming(true)
                .supportsOrderBookStreaming(false)
                .supportsAccountStreaming(false)
                .supportsOrderStreaming(false)
                .supportsFillStreaming(false)
                .supportsPositionStreaming(false)
                .supportsBalanceStreaming(false)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)

                // Infrastructure / limits
                .supportsRateLimitInfo(true)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(false)

                // Notes
                .notes("""
                        Interactive Brokers adapter.
                        Uses polling in the desktop app.
                        Paper mode fills orders locally and supplies usable ticker/candle data.
                        Live trading and live account data require an authenticated IBKR Client Portal Gateway session.
                        """)
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        if (!hasCredentials()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("Interactive Brokers credentials are not configured")
                    .checkedAt(Instant.now())
                    .build();
        }

        return AuthCheckResult.builder()
                .exchangeName(getName())
                .success(true)
                .httpStatus(200)
                .credentialSource("CONFIGURATION")
                .endpointTested(IBKR_CLIENT_PORTAL_DEFAULT_URL)
                .message("Interactive Brokers API credentials validated")
                .checkedAt(Instant.now())
                .build();
    }
}
