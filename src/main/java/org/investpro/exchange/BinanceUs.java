package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.investpro.data.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.trading.*;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.binance.BinanceCandleDataSupplier;
import org.investpro.exchange.websocket.BinanceWebSocketClient;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class BinanceUs extends Exchange {
    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BINANCE_US_WS_URL = "wss://stream.binance.us:9443/ws";
    private static final String BINANCE_US_REST_URL = "https://api.binance.us";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private ExchangeWebSocketClient websocketClient;
    private final java.util.concurrent.atomic.AtomicBoolean connected = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    public BinanceUs(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        initializePaperTradingAccount();

        try {
            this.websocketClient = createWebSocketClient();
        } catch (Exception ex) {
            logger.error("Failed to initialize BinanceUs websocket client", ex);
        }
    }

    private void initializePaperTradingAccount() {
        // Initialize with $10,000 USD for paper trading
        balances.put("USDT", 10000.0);
        balances.put("USD", 10000.0);
        balances.put("BTC", 0.0);
        balances.put("ETH", 0.0);
        logger.info("BinanceUs paper trading account initialized with $10,000 USDT");
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? "USDT"
                : currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Constructor with Telegram token and email notification support
     */
    public BinanceUs(String apiKey, String apiSecret, String telegramToken, String emailNotification) {
        this(apiKey, apiSecret);
        this.setTelegramToken(telegramToken);
        this.setEmailNotification(emailNotification);
    }

    private ExchangeWebSocketClient createWebSocketClient() throws Exception {
        return new BinanceWebSocketClient(URI.create(BINANCE_US_WS_URL), new org.java_websocket.drafts.Draft_6455());
    }

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return getSelectedTradePair();
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
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
            double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(tradePair != null
                && size >= getMinOrderAmount(tradePair)
                && supportsMarketType(marketType));
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return Double.isFinite(amount) && amount > 0 ? amount : 0.0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return Double.isFinite(price) && price >= 0 ? price : 0.0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.00000001;
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
        logger.warn("setLeverage() not implemented for BinanceUS");
        return failedFuture(unsupported("setLeverage"));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        logger.warn("modifyStopLoss() not implemented for BinanceUS");
        return failedFuture(unsupported("modifyStopLoss"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        logger.warn("closePartialPosition() not implemented for BinanceUS");
        return failedFuture(unsupported("closePartialPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        logger.warn("closePosition(symbol, positionId) not implemented for BinanceUS");
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        logger.warn("modifyTakeProfit() not implemented for BinanceUS");
        return failedFuture(unsupported("modifyTakeProfit"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        logger.warn("enableTrailingStop() not implemented for BinanceUS");
        return failedFuture(unsupported("enableTrailingStop"));
    }

    @Override
    public boolean supportsLiveTrading() {
        return hasCredentials();
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
        return false;
    }

    @Override
    public boolean supportsAccountTrades() {
        return false;
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
        return false;
    }

    @Override
    public boolean supportsCrypto() {
        return true;
    }

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.WEBSOCKET;
    }

    @Override
    public boolean supportsNativeWebSocket() {
        return true;
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

    }

    @Override
    public void disconnectStream() {

    }

    @Override
    public boolean isStreamConnected() {
        return false;
    }

    @Override
    public void reconnectStream() {

    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        if (subscription == null || consumer == null) {
            return;
        }

        // Stream market data based on subscription flags
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                streamTicker(pair, consumer);
            }
            if (subscription.isTrades()) {
                streamTrades(pair, consumer);
            }
            if (subscription.isOrderBook()) {
                streamOrderBook(pair, consumer);
            }
            if (subscription.isCandles()) {
                streamCandles(pair, 60, consumer);
            }
        }

        // Stream account data based on subscription flags
        if (subscription.isAccount()) {
            streamAccount(consumer);
        }
        if (subscription.isOrders()) {
            streamOrders(consumer);
        }
        if (subscription.isFills()) {
            streamFills(consumer);
        }
        if (subscription.isPositions()) {
            streamPositions(consumer);
        }
        if (subscription.isBalances()) {
            streamBalances(consumer);
        }
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
        if (subscription == null) {
            return;
        }

        // Stop market data streams only if they were subscribed
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                stopTickerStream(pair);
            }
            if (subscription.isTrades()) {
                stopTradesStream(pair);
            }
            if (subscription.isOrderBook()) {
                stopOrderBookStream(pair);
            }
            if (subscription.isCandles()) {
                stopCandlesStream(pair, 60);
            }
        }

        // Stop account data streams only if they were subscribed
        if (subscription.isAccount()) {
            stopAccountStream();
        }
        if (subscription.isOrders()) {
            stopOrdersStream();
        }
        if (subscription.isFills()) {
            stopFillsStream();
        }
        if (subscription.isPositions()) {
            stopPositionsStream();
        }
        if (subscription.isBalances()) {
            stopBalancesStream();
        }
    }

    @Override
    public void stopAllStreams() {

    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void stopTickerStream(TradePair tradePair) {

    }

    @Override
    public void stopTradesStream(TradePair tradePair) {

    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {

    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {

    }

    @Override
    public void stopAccountStream() {

    }

    @Override
    public void stopBalancesStream() {

    }

    @Override
    public void stopOrdersStream() {

    }

    @Override
    public void stopFillsStream() {

    }

    @Override
    public void stopPositionsStream() {

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
    public boolean supportsTickerStreaming() {
        return false;
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

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 180 -> "3m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 1800 -> "30m";

            case 3600 -> "1h";
            case 7200 -> "2h";
            case 14400 -> "4h";
            case 21600 -> "6h";
            case 28800 -> "8h";
            case 43200 -> "12h";

            case 86400 -> "1d";
            case 259200 -> "3d";
            case 604800 -> "1w";
            case 2592000 -> "1M";

            default -> "1m";
        };
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1,
                Timeframe.W1
        );
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public void autoTrading(@NotNull Boolean auto, String signal) {
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return websocketClient;
    }

    @Override
    public boolean supportsWebSocket() {
        return true;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return false;
    }

    @Override
    public Boolean isConnected() {
        try {
            return connected.get()
                    || websocketClient != null
                            && websocketClient.connectionEstablished != null
                            && websocketClient.connectionEstablished.get();
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle) {
        logger.warn("fetchCandleDataForInProgressCandle() not implemented for BinanceUS");
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
        logger.warn("fetchRecentTradesUntil() not implemented for BinanceUS");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public String getTimestamp() {
        return Instant.now().toString();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return new TradePair("BTC", "USDT");
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair) {
        return new BinanceCandleDataSupplier(i, tradePair);
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        Objects.requireNonNull(order, "order must not be null");
        String type = order.getType() == null ? "MARKET" : order.getType().trim().toUpperCase(java.util.Locale.ROOT);
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        if ("LIMIT".equals(type)) {
            return createLimitOrder(tradePairFromSymbol(order.getSymbol(), "USDT"), side, order.getQuantity(),
                    order.getPrice());
        }
        return createMarketOrder(tradePairFromSymbol(order.getSymbol(), "USDT"), side, order.getQuantity());
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        logger.warn("createOrder() not implemented for BinanceUS");
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        logger.warn("createStopOrder() not implemented for BinanceUS");
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
            double entryPrice, double stopLoss, double takeProfit) {
        logger.warn("createBracketOrder() not implemented for BinanceUS");
        return failedFuture(unsupported("createBracketOrder"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        logger.warn("cancelOrder not yet fully implemented for BinanceUs");
        return CompletableFuture.completedFuture(orderId);
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
        logger.warn("cancelOrders not yet fully implemented for BinanceUs");
        return CompletableFuture.completedFuture(orderIds);
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        logger.warn("cancelAllOrders not yet fully implemented for BinanceUs");
        return CompletableFuture.completedFuture("All orders canceled");
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        logger.warn("fetchOrder not yet fully implemented for BinanceUs");
        return CompletableFuture.completedFuture(java.util.Optional.empty());
    }

    @Override
    public String getSignal() {
        return "Binance US";
    }

    @Override
    public String getExchangeId() {
        return "binanceus";
    }

    @Override
    public String getDisplayName() {
        return "Binance US";
    }

    @Override
    public boolean isSandbox() {
        return isPaperTrading();
    }

    @Override
    public boolean isPaperTrading() {
        // If user explicitly selected trading mode during onboarding, respect that
        if (getUserSelectedTradingMode() != null && !getUserSelectedTradingMode().isBlank()) {
            return "PAPER".equalsIgnoreCase(getUserSelectedTradingMode());
        }
        // Otherwise, default to paper trading if no credentials
        return !hasCredentials();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        if (marketType == null) {
            return false;
        }
        String name = marketType.name().toUpperCase(java.util.Locale.ROOT);
        return name.contains("CRYPTO") || name.contains("SPOT");
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return java.util.Arrays.stream(MARKET_TYPES.values())
                .filter(this::supportsMarketType)
                .toList();
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return createMarketOrder(symbol, side, quantity);
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return createLimitOrder(symbol, side, quantity, limitPrice);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (hasCredentials()) {
            return submitBinanceUsOrder(tradePair, side, amount, 0.0, "MARKET");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            double fillPrice = 50000.0; // Simulated market price

            if (side == Side.BUY) {
                double cost = amount * fillPrice;
                Double balance = balances.getOrDefault("USDT", 0.0);
                if (balance < cost) {
                    throw new RuntimeException(
                            "Insufficient balance for market order. Required: " + cost + ", Available: " + balance);
                }
                balances.put("USDT", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException(
                            "Insufficient " + tradePair.getBaseCode() + " balance for market order.");
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USDT", balances.getOrDefault("USDT", 0.0) + (amount * fillPrice));
            }

            // Record trade in history
            Trade trade = new Trade();
            trade.setTradePair(tradePair);
            trade.setPrice(fillPrice);
            trade.setAmount(amount);
            trade.setTransactionType(side);
            trade.setLocalTradeId(System.nanoTime());
            trade.setTimestamp(java.time.Instant.now());
            trade.setFee(0.0);
            trade.setStopLoss(0.0);
            trade.setTakeProfit(0.0);
            trade.setSwap(0.0);
            trade.setProfit(0.0);
            tradeHistory.add(trade);

            orders.put(orderId, "FILLED");
            logger.info("[PAPER] Market order {} executed: {} {} at ${}", orderId, side, amount, fillPrice);
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        if (hasCredentials()) {
            return submitBinanceUsOrder(tradePair, side, amount, limitPrice, "LIMIT");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();

            if (side == Side.BUY) {
                double cost = amount * limitPrice;
                Double balance = balances.getOrDefault("USDT", 0.0);
                if (balance < cost) {
                    throw new RuntimeException(
                            "Insufficient balance for limit order. Required: " + cost + ", Available: " + balance);
                }
                balances.put("USDT", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException(
                            "Insufficient " + tradePair.getBaseCode() + " balance for limit order.");
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USDT", balances.getOrDefault("USDT", 0.0) + (amount * limitPrice));
            }

            // Record trade in history
            Trade trade = new Trade();
            trade.setTradePair(tradePair);
            trade.setPrice(limitPrice);
            trade.setAmount(amount);
            trade.setTransactionType(side);
            trade.setLocalTradeId(System.nanoTime());
            trade.setTimestamp(java.time.Instant.now());
            trade.setFee(0.0);
            trade.setStopLoss(0.0);
            trade.setTakeProfit(0.0);
            trade.setSwap(0.0);
            trade.setProfit(0.0);
            tradeHistory.add(trade);

            orders.put(orderId, "FILLED");
            logger.info("[PAPER] Limit order {} executed: {} {} at limit ${}", orderId, side, amount, limitPrice);
            return orderId;
        });
    }

    @Override
    public void connect() {
        if (hasCredentials()) {
            try {
                fetchAccount().join();
                connected.set(true);
                logger.info("Connected to Binance US REST API");
                return;
            } catch (Exception exception) {
                connected.set(false);
                logger.warn("Unable to connect Binance US REST API", exception);
            }
        }
        try {
            if (websocketClient != null && !websocketClient.isOpen()) {
                // Use connectBlocking() to wait for actual connection establishment
                // with timeout to prevent hanging on stuck connections
                boolean connected = websocketClient.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!connected) {
                    throw new RuntimeException("WebSocket connection timeout after 10 seconds");
                }
                logger.info("Connected to BinanceUs WebSocket");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("BinanceUs WebSocket connection interrupted", exception);
        } catch (Exception exception) {
            logger.warn("Unable to connect BinanceUs WebSocket", exception);
        } finally {
            if (isPaperTrading()) {
                connected.set(true);
            }
        }
    }

    @Override
    public void disconnect() {
        connected.set(false);
        if (websocketClient != null) {
            websocketClient.close();
        }
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        // Using Binance US API to get all trading pairs
        String url = BINANCE_US_REST_URL + "/api/v3/exchangeInfo";
        ArrayList<TradePair> tradePairs = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "InvestPro/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info("Binance US API response received");

            // Check if response is an error message
            if (res.isObject() && res.has("code")) {
                int errorCode = res.get("code").asInt();
                if (errorCode < 0) {
                    String errorMsg = res.has("msg") ? res.get("msg").asText() : "Unknown error";
                    logger.warn("Binance US API error %d: %s".formatted(errorCode, errorMsg));
                    return tradePairs; // Return empty list on API error
                }
            }

            // Binance US API response format: {"symbols": [...]}
            JsonNode symbolsNode = res.has("symbols") ? res.get("symbols") : res;

            if (symbolsNode == null || !symbolsNode.isArray()) {
                logger.warn("Binance US API returned unexpected format");
                return tradePairs;
            }

            for (JsonNode symbol : symbolsNode) {
                // Skip non-trading pairs
                if (!symbol.has("status") || !symbol.get("status").asText().equals("TRADING")) {
                    continue;
                }

                CryptoCurrency baseCurrency, counterCurrency;

                // Safely extract fields
                JsonNode baseAssetNode = symbol.get("baseAsset");
                JsonNode quoteAssetNode = symbol.get("quoteAsset");

                if (baseAssetNode == null || quoteAssetNode == null) {
                    logger.debug("Skipping symbol with missing currency fields");
                    continue;
                }

                String baseAsset = baseAssetNode.asText();
                String quoteAsset = quoteAssetNode.asText();

                try {
                    // Try to create currencies - may fail if currency is not recognized
                    baseCurrency = new CryptoCurrency(baseAsset, baseAsset, baseAsset, 8, baseAsset, baseAsset);
                    counterCurrency = new CryptoCurrency(quoteAsset, quoteAsset, quoteAsset, 8, quoteAsset, quoteAsset);

                    TradePair tp = new TradePair(baseCurrency, counterCurrency);
                    tradePairs.add(tp);
                    logger.debug("Added trade pair: %s".formatted(tp));
                } catch (SQLException | ClassNotFoundException e) {
                    logger.debug("Skipping pair %s-%s: %s".formatted(baseAsset, quoteAsset, e.getMessage()));
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching Binance US trade pairs", ex);
            // Return empty list instead of throwing exception
            return new ArrayList<>();
        }

        return tradePairs;
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
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
        return fetchOrderBook(tradePair);
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        if (tradePair == null)
            return Ticker.empty();
        Ticker ticker = new Ticker();
        // Simulated prices
        double price = tradePair.getBaseCode().equals("BTC") ? 50000.0 : 3000.0;
        ticker.setLastPrice(price);
        ticker.setBidPrice(price * 0.999);
        ticker.setAskPrice(price * 1.001);
        ticker.setVolume(1000000.0);
        ticker.setTimestamp(System.currentTimeMillis());
        return ticker;
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.completedFuture(getLivePrice(tradePair));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
        return CompletableFuture.completedFuture(
                tradePairs.stream()
                        .map(this::getLivePrice)
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (hasCredentials()) {
            return CompletableFuture.supplyAsync(this::fetchLiveAccount);
        }
        return CompletableFuture.supplyAsync(() -> {
            Account account = new Account();
            double equity = balances.values().stream().mapToDouble(Double::doubleValue).sum();
            account.setTotalBalance(balances.getOrDefault("USDT", 0.0));
            account.setAvailableBalance(balances.getOrDefault("USDT", 0.0));
            account.setEquity(equity);
            account.setBalances(new java.util.LinkedHashMap<>(balances));
            account.setAvailableBalances(new java.util.LinkedHashMap<>(balances));
            account.setExchangeId("binanceus");
            account.setBrokerName("Binance US");
            account.setPaperTrading(true);
            account.setConnected(true);
            account.setUpdatedAt(java.time.Instant.now());
            logger.debug("[PAPER] Account summary: Equity=${}, Balance=${}", equity,
                    balances.getOrDefault("USDT", 0.0));
            return account;
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
        return CompletableFuture.supplyAsync(() -> balances.values().stream().mapToDouble(Double::doubleValue).sum());
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
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        logger.warn("getUserAccountDetails not fully implemented for BinanceUs");
        CompletableFuture<Account> account = fetchAccount();
        return account.get();
    }

    @Override
    public double getLivePrice() {
        return 0.0;
    }

    @Override
    public String getName() {
        return "BinanceUs";
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
        return CompletableFuture.completedFuture(new OrderBook(tradePair));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(new ArrayList<>(positions));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(java.util.Optional.empty());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        positions.clear();
        return CompletableFuture.completedFuture("Closed all Binance US paper positions.");
    }

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
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        List<Trade> result = tradeHistory.stream()
                .filter(t -> t.getTimestamp() != null &&
                        (from == null || t.getTimestamp().isAfter(from)) &&
                        (to == null || t.getTimestamp().isBefore(to)))
                .filter(t -> tradePair == null || (t.getTradePair() != null && t.getTradePair().equals(tradePair)))
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {

    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {

    }

    private CompletableFuture<String> submitBinanceUsOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice,
            String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("symbol", binanceSymbol(tradePair));
                params.put("side", side == Side.SELL ? "SELL" : "BUY");
                params.put("type", type);
                params.put("quantity", decimal(amount));
                if ("LIMIT".equals(type)) {
                    params.put("timeInForce", "GTC");
                    params.put("price", decimal(limitPrice));
                }
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));
                JsonNode response = sendSignedBinanceUsRequest("POST", "/api/v3/order", params);
                JsonNode orderId = response.get("orderId");
                return orderId == null ? response.toString() : orderId.asText();
            } catch (Exception exception) {
                throw new IllegalStateException("Binance US order submission failed.", exception);
            }
        });
    }

    private Account fetchLiveAccount() {
        try {
            JsonNode response = sendSignedBinanceUsRequest("GET", "/api/v3/account", new LinkedHashMap<>());
            Map<String, Double> liveBalances = new LinkedHashMap<>();
            Map<String, Double> availableBalances = new LinkedHashMap<>();
            JsonNode balancesNode = response.get("balances");
            if (balancesNode != null && balancesNode.isArray()) {
                for (JsonNode balance : balancesNode) {
                    String asset = balance.path("asset").asText();
                    double free = balance.path("free").asDouble(0.0);
                    double locked = balance.path("locked").asDouble(0.0);
                    if (free != 0.0 || locked != 0.0) {
                        liveBalances.put(asset, free + locked);
                        availableBalances.put(asset, free);
                    }
                }
            }
            Account account = new Account();
            account.setBalances(liveBalances);
            account.setAvailableBalances(availableBalances);
            account.setTotalBalance(availableBalances.getOrDefault("USDT", 0.0));
            account.setAvailableBalance(availableBalances.getOrDefault("USDT", 0.0));
            account.setEquity(liveBalances.values().stream().mapToDouble(Double::doubleValue).sum());
            account.setExchangeId("binanceus");
            account.setBrokerName("Binance US");
            account.setPaperTrading(false);
            account.setConnected(true);
            account.setUpdatedAt(java.time.Instant.now());
            return account;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch Binance US account.", exception);
        }
    }

    private JsonNode sendSignedBinanceUsRequest(String method, String path, Map<String, String> params)
            throws Exception {
        Map<String, String> signedParams = new LinkedHashMap<>(params);
        signedParams.putIfAbsent("timestamp", Long.toString(System.currentTimeMillis()));
        String query = formEncode(signedParams);
        String signature = ExchangeSigning.hmacHex("HmacSHA256", apiSecret, query);
        String signedQuery = query + "&signature=" + signature;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header("X-MBX-APIKEY", apiKey)
                .header("User-Agent", "InvestPro/1.0");
        if ("GET".equals(method)) {
            builder.uri(URI.create(BINANCE_US_REST_URL + path + "?" + signedQuery)).GET();
        } else {
            builder.uri(URI.create(BINANCE_US_REST_URL + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(signedQuery));
        }
        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Binance US API returned HTTP %d: %s".formatted(response.statusCode(), body));
        }
        return body;
    }

    private static String binanceSymbol(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        return (tradePair.getBaseCode() + tradePair.getCounterCode()).toUpperCase(java.util.Locale.ROOT);
    }

    private static TradePair tradePairFromSymbol(String symbol, String defaultQuote) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("order symbol must not be blank");
        }
        String normalized = symbol.trim().replace("-", "/").toUpperCase(java.util.Locale.ROOT);
        String base;
        String quote;
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            base = parts[0];
            quote = parts[1];
        } else {
            quote = List.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR").stream()
                    .filter(normalized::endsWith)
                    .findFirst()
                    .orElse(defaultQuote);
            base = normalized.endsWith(quote) ? normalized.substring(0, normalized.length() - quote.length())
                    : normalized;
        }
        try {
            return TradePair.of(base, quote);
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalArgumentException("Unable to resolve order symbol: " + symbol, exception);
        }
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Order amount and price values must be positive.");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
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
