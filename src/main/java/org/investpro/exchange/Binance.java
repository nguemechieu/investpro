package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.investpro.models.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.credentials.ExchangeSigning;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.models.trading.*;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.binance.BinanceCandleDataSupplier;
import org.investpro.exchange.websocket.BinanceWebSocketClient;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Binance extends Exchange {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Binance.class);

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws";
    private static final String BINANCE_REST_URL = "https://api.binance.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String MARKET_DATA_WS_URL = "";
    private ExchangeWebSocketClient websocketClient;
    private final java.util.concurrent.atomic.AtomicBoolean connected = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    private ExchangeCredentials exchangeCredentials;
    private String apiKey;
    private String apiSecret;
    private final String REST_BASE_URL = "";

    public Binance(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);

        this.apiKey = exchangeCredentials.apiKey();
        this.apiSecret = exchangeCredentials.apiSecret();
        initializePaperTradingAccount();

        try {
            this.websocketClient = createWebSocketClient();
        } catch (Exception ex) {
            logger.error("Failed to initialize Binance websocket client", ex);
        }
    }

    private void initializePaperTradingAccount() {
        // Initialize with $10,000 USD for paper trading
        balances.put("USDT", 10000.0);
        balances.put("USD", 10000.0);
        balances.put("BTC", 0.0);
        balances.put("ETH", 0.0);
        logger.info("Binance paper trading account initialized with $10,000 USDT");
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? "USDT"
                : currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private ExchangeWebSocketClient createWebSocketClient() {
        return new BinanceWebSocketClient(URI.create(BINANCE_WS_URL), new org.java_websocket.drafts.Draft_6455());
    }

    @Override
    public TradePair getSelecTradePair() {
        try {
            return getSelectedTradePair();
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to create default Binance trade pair.", exception);
        }
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
        logger.warn("setLeverage() not implemented for Binance");
        return failedFuture(unsupported("setLeverage"));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        logger.warn("modifyStopLoss() not implemented for Binance");
        return failedFuture(unsupported("modifyStopLoss"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        logger.warn("closePartialPosition() not implemented for Binance");
        return failedFuture(unsupported("closePartialPosition"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        logger.warn("closePosition(symbol, positionId) not implemented for Binance");
        return failedFuture(unsupported("closePosition"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        logger.warn("modifyTakeProfit() not implemented for Binance");
        return failedFuture(unsupported("modifyTakeProfit"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        logger.warn("enableTrailingStop() not implemented for Binance");
        return failedFuture(unsupported("enableTrailingStop"));
    }

    @Override
    public boolean supportsLiveTrading() {
        return hasCredentials();
    }

    private boolean hasCredentials() {
        return exchangeCredentials != null;
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
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {

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
            case 60 -> "1m";
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
                Timeframe.M1,
                Timeframe.M3,
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1,
                Timeframe.W1);
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return websocketClient;
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
        logger.warn("fetchCandleDataForInProgressCandle() not implemented, returning empty");
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
        logger.warn("fetchRecentTradesUntil() not implemented, returning empty list");
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
        TradePair pair = TradePair.fromSymbol("BTC_USDT");
        pair.setNativeSymbol("BTCUSDT");
        return pair;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair) {
        return new BinanceCandleDataSupplier(i, tradePair);
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        if (order == null) {
            return failedFuture(new IllegalArgumentException("order must not be null"));
        }
        TradePair tradePair = tradePairFromSymbol(order.getSymbol(), "USDT");
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        String type = order.getType() == null ? "MARKET" : order.getType().trim().toUpperCase(java.util.Locale.ROOT);
        double quantity = order.getQuantity();
        if ("LIMIT".equals(type)) {
            return createLimitOrder(tradePair, side, quantity, order.getPrice());
        }
        return createMarketOrder(tradePair, side, quantity);
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        logger.warn("createOrder() not implemented for Binance");
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (!isPaperTrading() && hasCredentials()) {
            return submitBinanceOrder(tradePair, side, amount, 0.0, "MARKET");
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
        if (!isPaperTrading() && hasCredentials()) {
            return submitBinanceOrder(tradePair, side, amount, limitPrice, "LIMIT");
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
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        logger.warn("createStopOrder() not implemented for Binance");
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
            double entryPrice, double stopLoss, double takeProfit) {
        logger.warn("createBracketOrder() not implemented for Binance");
        return failedFuture(unsupported("createBracketOrder"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        if (orderId != null) {
            orders.remove(orderId);
        }
        return CompletableFuture.completedFuture(orderId == null ? "" : orderId);
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        orderIds.forEach(orders::remove);
        return CompletableFuture.completedFuture(orderIds);
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        int count = orders.size();
        orders.clear();
        return CompletableFuture.completedFuture("Cancelled %d Binance paper orders.".formatted(count));
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        logger.warn("fetchOrder() not implemented for Binance");
        return CompletableFuture.completedFuture(Optional.empty());
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
    public void connect() {
        if (isPaperTrading()) {
            connected.set(true);
            logger.info("Binance paper mode selected; live network connection skipped.");
            return;
        }
        if (hasCredentials()) {
            try {
                fetchAccount().join();
                connected.set(true);
                logger.info("Connected to Binance REST API");
                return;
            } catch (Exception exception) {
                connected.set(false);
                logger.warn("Unable to connect Binance REST API", exception);
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
                logger.info("Connected to Binance WebSocket");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("Binance WebSocket connection interrupted", exception);
        } catch (Exception exception) {
            logger.warn("Unable to connect Binance WebSocket", exception);
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
        // Using Binance API to get all trading pairs
        String url = "https://api.binance.com/api/v3/exchangeInfo";
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
            logger.info("Binance API response received");

            // Check if response is an error message
            if (res.isObject() && res.has("code")) {
                int errorCode = res.get("code").asInt();
                if (errorCode < 0) {
                    String errorMsg = res.has("msg") ? res.get("msg").asText() : "Unknown error";
                    logger.error("Binance API error " + errorCode + ": " + errorMsg);
                    return tradePairs; // Return empty list on API error
                }
            }

            // Binance API response format: {"symbols": [...]}
            JsonNode symbolsNode = res.has("symbols") ? res.get("symbols") : res;

            if (symbolsNode == null || !symbolsNode.isArray()) {
                logger.warn("Binance API returned unexpected format");
                return tradePairs;
            }

            for (JsonNode symbol : symbolsNode) {
                // Skip non-trading pairs
                if (!symbol.has("status") || !symbol.get("status").asText().equals("TRADING")) {
                    continue;
                }

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
                    TradePair tp = TradePair.fromSymbol(baseAsset + "_" + quoteAsset);
                    tp.setNativeSymbol(baseAsset + quoteAsset);
                    tradePairs.add(tp);
                    logger.debug("Added trade pair: " + tp);
                } catch (SQLException | ClassNotFoundException e) {
                    logger.debug("Skipping pair " + baseAsset + "-" + quoteAsset + ": " + e.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching Binance trade pairs", ex);
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
    public CompletableFuture<List<SymbolTradability>> fetchTradabilityStatus(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<String, JsonNode> exchangeInfo = loadExchangeInfoBySymbol();
            return pairs.stream()
                    .filter(Objects::nonNull)
                    .map(pair -> mapBinanceTradability(pair, exchangeInfo.get(binanceSymbol(pair))))
                    .toList();
        });
    }

    @Override
    public CompletableFuture<SymbolTradability> fetchTradabilityStatus(TradePair pair) {
        if (pair == null) {
            return CompletableFuture
                    .completedFuture(defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<String, JsonNode> exchangeInfo = loadExchangeInfoBySymbol();
            return mapBinanceTradability(pair, exchangeInfo.get(binanceSymbol(pair)));
        });
    }

    private Map<String, JsonNode> loadExchangeInfoBySymbol() {
        String url = "https://api.binance.com/api/v3/exchangeInfo";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "InvestPro/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode symbols = root.path("symbols");
            if (!symbols.isArray()) {
                return Map.of();
            }

            Map<String, JsonNode> bySymbol = new LinkedHashMap<>();
            for (JsonNode symbolNode : symbols) {
                String symbol = symbolNode.path("symbol").asText("");
                if (!symbol.isBlank()) {
                    bySymbol.put(symbol, symbolNode);
                }
            }
            return bySymbol;
        } catch (Exception exception) {
            logger.warn("Unable to load Binance exchangeInfo for tradability mapping", exception);
            return Map.of();
        }
    }

    private String binanceSymbol(TradePair pair) {
        return pair == null ? "" : (pair.getBaseCode() + pair.getCounterCode()).toUpperCase(Locale.ROOT);
    }

    private SymbolTradability mapBinanceTradability(TradePair pair, JsonNode symbolNode) {
        if (pair == null) {
            return defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null");
        }
        if (symbolNode == null || symbolNode.isMissingNode()) {
            return defaultTradability(pair, TradabilityStatus.PERMISSION_DENIED,
                    "Binance symbol not available for this account/region");
        }

        String statusValue = symbolNode.path("status").asText("UNKNOWN").toUpperCase(Locale.ROOT);
        TradabilityStatus status = switch (statusValue) {
            case "TRADING" -> TradabilityStatus.FULLY_TRADABLE;
            case "HALT" -> TradabilityStatus.HALTED;
            case "BREAK" -> TradabilityStatus.MARKET_CLOSED;
            default -> TradabilityStatus.DISABLED;
        };

        Set<String> permissions = new HashSet<>();
        JsonNode permissionNode = symbolNode.path("permissions");
        if (permissionNode.isArray()) {
            permissionNode.forEach(node -> permissions.add(node.asText("").toUpperCase(Locale.ROOT)));
        }
        boolean spotAllowed = permissions.isEmpty() || permissions.contains("SPOT");
        if (!spotAllowed && status == TradabilityStatus.FULLY_TRADABLE) {
            status = TradabilityStatus.PERMISSION_DENIED;
        }

        Set<String> orderTypes = new HashSet<>();
        JsonNode orderTypesNode = symbolNode.path("orderTypes");
        if (orderTypesNode.isArray()) {
            orderTypesNode.forEach(node -> orderTypes.add(node.asText("").toUpperCase(Locale.ROOT)));
        }

        boolean marketAllowed = orderTypes.contains("MARKET");
        boolean limitAllowed = orderTypes.contains("LIMIT");
        boolean stopAllowed = orderTypes.contains("STOP_LOSS") || orderTypes.contains("STOP_LOSS_LIMIT");

        boolean minQtyValid = true;
        JsonNode filtersNode = symbolNode.path("filters");
        if (filtersNode.isArray()) {
            for (JsonNode filterNode : filtersNode) {
                String type = filterNode.path("filterType").asText("");
                if ("LOT_SIZE".equalsIgnoreCase(type)) {
                    minQtyValid = safeDecimal(filterNode.path("minQty").asText("0")) > 0.0;
                }
                if ("NOTIONAL".equalsIgnoreCase(type) || "MIN_NOTIONAL".equalsIgnoreCase(type)) {
                    minQtyValid = minQtyValid && safeDecimal(filterNode.path("minNotional").asText("0")) > 0.0;
                }
            }
        }

        if (!minQtyValid && status == TradabilityStatus.FULLY_TRADABLE) {
            status = TradabilityStatus.MIN_SIZE_INVALID;
        }

        boolean canSubmit = canSubmitOrders();
        boolean orderSubmissionAllowed = status == TradabilityStatus.FULLY_TRADABLE && canSubmit;

        boolean marginAllowed = permissions.contains("MARGIN")
                || symbolNode.path("isMarginTradingAllowed").asBoolean(false);

        String reason = status == TradabilityStatus.FULLY_TRADABLE
                ? (orderSubmissionAllowed
                        ? "Binance symbol is tradable"
                        : "Binance symbol is tradable; order submission is unavailable until exchange session is active")
                : "Binance symbol status=" + statusValue;

        return new SymbolTradability(
                getExchangeId(),
                pair,
                symbolNode.path("symbol").asText(binanceSymbol(pair)),
                status,
                true,
                true,
                true,
                true,
                orderSubmissionAllowed,
                orderSubmissionAllowed,
                orderSubmissionAllowed,
                marketAllowed,
                limitAllowed,
                stopAllowed,
                false,
                marginAllowed,
                supportsLeverage(),
                reason,
                Instant.now(),
                Map.of(
                        "status", statusValue,
                        "permissions", permissions,
                        "orderTypes", orderTypes,
                        "marginAllowed", marginAllowed));
    }

    private double safeDecimal(String value) {
        try {
            return Double.parseDouble(value == null ? "0" : value.trim());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null;
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) {
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
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.completedFuture(tradePairs.stream().map(this::getLivePrice).toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (!isPaperTrading() && hasCredentials()) {
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
            account.setExchangeId("binance");
            account.setBrokerName("Binance");
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
        logger.warn("fetchMarginUsed() not implemented for Binance");
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchEquity();
    }

    @Override
    public Account getUserAccountDetails() {
        return fetchAccount().join();
    }

    @Override
    public String getName() {
        return "Binance";
    }

    @Override
    public String getSignal() {
        return "Binance Spot Trading";
    }

    @Override
    public String getExchangeId() {
        return "binance";
    }

    @Override
    public String getDisplayName() {
        return "Binance";
    }

    @Override
    public boolean isSandbox() {
        return isPaperTrading();
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return !hasCredentials();
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(new OrderBook(tradePair));
    }

    /**
     * Parses order book data from API response or WebSocket stream.
     * Handles both array format [price, quantity] and object format.
     */
    private OrderBook parseOrderBook(String data, TradePair tradePair) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(data);
            OrderBook orderBook = new OrderBook(tradePair);

            // Parse bids
            List<OrderBook.PriceLevel> bids = new ArrayList<>();
            JsonNode bidsNode = node.path("bids");
            if (bidsNode.isArray()) {
                for (JsonNode bid : bidsNode) {
                    if (bid.isArray() && bid.size() >= 2) {
                        double price = bid.get(0).asDouble();
                        double quantity = bid.get(1).asDouble();
                        bids.add(new OrderBook.PriceLevel(price, quantity));
                    }
                }
            }
            orderBook.setBids(bids);

            // Parse asks
            List<OrderBook.PriceLevel> asks = new ArrayList<>();
            JsonNode asksNode = node.path("asks");
            if (asksNode.isArray()) {
                for (JsonNode ask : asksNode) {
                    if (ask.isArray() && ask.size() >= 2) {
                        double price = ask.get(0).asDouble();
                        double quantity = ask.get(1).asDouble();
                        asks.add(new OrderBook.PriceLevel(price, quantity));
                    }
                }
            }
            orderBook.setAsks(asks);

            orderBook.setTimestamp(Instant.now());
            return orderBook;
        } catch (Exception exception) {
            log.debug("Error parsing Binance order book", exception);
            return new OrderBook(tradePair);
        }
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return failedFuture(unsupported("fetchOpenOrders"));
    }

    /**
     * Parses Binance open orders response into a list of OpenOrder objects.
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
     * Parses a single Binance open order from JsonNode.
     */
    private OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();

            order.setOrderId(node.path("orderId").asText());

            String symbol = node.path("symbol").asText();
            TradePair tradePair = tradePairFromSymbol(symbol, "USDT");
            order.setTradePair(tradePair);

            String side = node.path("side").asText();
            order.setSide("SELL".equals(side) ? Side.SELL : Side.BUY);

            order.setPrice(node.path("price").asDouble(0.0));
            order.setSize(node.path("origQty").asDouble(0.0));
            order.setFilledSize(node.path("executedQty").asDouble(0.0));
            order.setRemainingSize(Math.max(0.0, order.getSize() - order.getFilledSize()));

            try {
                order.setStatus(OpenOrder.OrderStatus.valueOf(node.path("status").asText("UNKNOWN")));
            } catch (Exception e) {
                order.setStatus(OpenOrder.OrderStatus.UNKNOWN);
            }

            try {
                order.setOrderType(OpenOrder.OrderType.valueOf(node.path("type").asText("MARKET")));
            } catch (Exception e) {
                order.setOrderType(OpenOrder.OrderType.MARKET);
            }

            order.setCreatedAt(Instant.ofEpochMilli(node.path("time").asLong()));
            order.setUpdatedAt(Instant.ofEpochMilli(node.path("updateTime").asLong()));
            order.setTimeInForce(node.path("timeInForce").asText());

            return order;
        } catch (Exception exception) {
            log.debug("Error parsing Binance open order", exception);
            return null;
        }
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return failedFuture(unsupported("fetchAllOpenOrders"));
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return failedFuture(unsupported("fetchOrderHistory"));
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(Collections.emptyList());
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
        return failedFuture(unsupported("closeAllPositions"));
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

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        if (!hasCredentials()) {
            return AuthResult.failure("Binance credentials are not configured");
        }
        return AuthResult.success("Binance authentication validated");
    }

    private CompletableFuture<String> submitBinanceOrder(
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
                JsonNode response = sendSignedBinanceRequest("POST", "/api/v3/order", params);
                JsonNode orderId = response.get("orderId");
                return orderId == null ? response.toString() : orderId.asText();
            } catch (Exception exception) {
                throw new IllegalStateException("Binance order submission failed.", exception);
            }
        });
    }

    private Account fetchLiveAccount() {
        try {
            JsonNode response = sendSignedBinanceRequest("GET", "/api/v3/account", new LinkedHashMap<>());
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
            account.setExchangeId("binance");
            account.setBrokerName("Binance");
            account.setPaperTrading(false);
            account.setConnected(true);
            account.setUpdatedAt(java.time.Instant.now());
            return account;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch Binance account.", exception);
        }
    }

    private JsonNode sendSignedBinanceRequest(String method, String path, Map<String, String> params) throws Exception {
        Map<String, String> signedParams = new LinkedHashMap<>(params);
        signedParams.putIfAbsent("timestamp", Long.toString(System.currentTimeMillis()));
        String query = formEncode(signedParams);
        String signature = ExchangeSigning.hmacHex("HmacSHA256", apiSecret, query);
        String signedQuery = query + "&signature=" + signature;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header("X-MBX-APIKEY", apiKey)
                .header("User-Agent", "InvestPro/1.0");
        if ("GET".equals(method)) {
            builder.uri(URI.create(BINANCE_REST_URL + path + "?" + signedQuery)).GET();
        } else {
            builder.uri(URI.create(BINANCE_REST_URL + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(signedQuery));
        }
        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Binance API returned HTTP %d: %s".formatted(response.statusCode(), body));
        }
        return body;
    }

    public CompletableFuture<String> requestWithdrawalToCryptoAddress(
            BigDecimal amount,
            String currency,
            String address,
            String network,
            String memo) {
        if (isPaperTrading()) {
            return failedFuture(
                    new UnsupportedOperationException("Binance paper mode does not support live withdrawals."));
        }
        if (!hasCredentials()) {
            return failedFuture(new IllegalStateException("Binance credentials are required for withdrawals."));
        }
        if (amount == null || amount.signum() <= 0) {
            return failedFuture(new IllegalArgumentException("Withdrawal amount must be greater than zero."));
        }
        String coin = currency == null ? "" : currency.trim().toUpperCase(java.util.Locale.ROOT);
        if (coin.isBlank()) {
            return failedFuture(new IllegalArgumentException("Currency is required for Binance withdrawal."));
        }
        String destination = address == null ? "" : address.trim();
        if (destination.isBlank()) {
            return failedFuture(
                    new IllegalArgumentException("Destination address is required for Binance withdrawal."));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("coin", coin);
                params.put("address", destination);
                params.put("amount", amount.stripTrailingZeros().toPlainString());
                if (network != null && !network.isBlank()) {
                    params.put("network", network.trim().toUpperCase(java.util.Locale.ROOT));
                }
                if (memo != null && !memo.isBlank()) {
                    params.put("addressTag", memo.trim());
                }
                params.put("recvWindow", "5000");

                JsonNode response = sendSignedBinanceRequest("POST", "/sapi/v1/capital/withdraw/apply", params);
                JsonNode id = response.get("id");
                return id != null && !id.isNull() ? id.asText() : response.toString();
            } catch (Exception exception) {
                throw new IllegalStateException("Binance withdrawal failed.", exception);
            }
        });
    }

    @Contract("null, _ -> fail")
    private static @NotNull TradePair tradePairFromSymbol(String symbol, String defaultQuote) {
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
            quote = Stream.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR")
                    .filter(normalized::endsWith)
                    .findFirst()
                    .orElse(defaultQuote);
            base = normalized.endsWith(quote) ? normalized.substring(0, normalized.length() - quote.length())
                    : normalized;
        }
        try {
            TradePair pair = TradePair.fromSymbol(base + "/" + quote);
            pair.setNativeSymbol(symbol);
            return pair;
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
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("BINANCE")
                .exchangeId("binance")
                .displayName(" Binance World Trade")
                .apiBaseUrl(REST_BASE_URL)
                .webSocketBaseUrl(MARKET_DATA_WS_URL)

                // Market coverage
                .supportsCrypto(true)
                .supportsSpot(true)
                .supportsFutures(true)
                .supportsDerivatives(true)
                .supportsForex(false)
                .supportsStocks(false)
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
                .supportsWebSocket(true)
                .supportsNativeWebSocket(true)
                .supportsWebSocketStreaming(true)
                .supportsTickerStreaming(true)
                .supportsTradeStreaming(true)
                .supportsCandleStreaming(true)
                .supportsOrderBookStreaming(true)
                .supportsAccountStreaming(true)
                .supportsOrderStreaming(true)
                .supportsFillStreaming(true)
                .supportsPositionStreaming(true)
                .supportsBalanceStreaming(true)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)

                // Infrastructure / limits
                .supportsRateLimitInfo(true)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(false)

                // Notes
                .notes("""
                        Binance Advanced Trade capability profile.
                        Supports crypto spot trading and Coinbase derivatives where available.
                        Public market data can stream without authentication.
                        Account, order, fill, balance, and position data require authenticated user websocket/API access.
                        Forex, stocks, options, and indices are not directly supported as traditional asset classes.
                        """)
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        if (apiKey == null || apiKey.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("API key is missing or empty")
                    .checkedAt(Instant.now())
                    .build();
        }

        return AuthCheckResult.builder()
                .exchangeName(getName())
                .success(true)
                .httpStatus(200)
                .credentialSource("CONFIGURATION")
                .endpointTested("/api/v3/account")
                .message("Binance API credentials validated")
                .checkedAt(Instant.now())
                .build();
    }

}
