package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Data;
import org.investpro.models.Account;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;

import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
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
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Data
public class BinanceUs extends Exchange {
    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BINANCE_US_WS_URL = "wss://stream.binance.us:9443/ws";
    private static final String BINANCE_US_REST_URL = "https://api.binance.us";

    // Properly configured HTTP client with timeouts and connection pooling
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 100L;
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    private static final String REST_BASE_URL = "";
    private static final String MARKET_DATA_WS_URL = "";
    private ExchangeWebSocketClient websocketClient;
    protected final ExchangeStreamConsumer liveTradeConsumers = new UiExchangeStreamConsumer();

    private final AtomicBoolean connected = new AtomicBoolean(
            false);
    private volatile String listenKey; // Listen key for user stream subscriptions
    private volatile ScheduledExecutorService listenKeyHeartbeatExecutor;
    private volatile long signedRestCooldownUntilMs;
    private volatile long lastSignedRestRequestMs;
    private volatile long serverTimeOffsetMs;
    private volatile long lastServerTimeSyncMs;
    private long publicRestCooldownUntilMs;
    private volatile long lastOrderBookRequestMs;
    private static final long SIGNED_REST_MIN_INTERVAL_MS = 1_200L;
    private static final long SIGNED_REST_429_COOLDOWN_MS = 65_000L;
    private static final long BINANCE_US_RECV_WINDOW_MS = 30_000L;
    private static final long BINANCE_US_TIME_SYNC_TTL_MS = 5 * 60_000L;
    private static final long PUBLIC_REST_MIN_INTERVAL_MS = 2_000L;
    private static final long PUBLIC_REST_429_COOLDOWN_MS = 45_000L;

    // Paper trading state
    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    private final List<Position> positions = new CopyOnWriteArrayList<>();
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    private String apiKey;
    private String apiSecret;
    private ExchangeCredentials credential;

    public BinanceUs(ExchangeCredentials credentials) {
        super(credentials);
        this.credential = credentials;

        this.apiKey = credentials.apiKey();
        this.apiSecret = credentials.apiSecret();
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
                : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private ExchangeWebSocketClient createWebSocketClient() {
        return new BinanceWebSocketClient(URI.create(BINANCE_US_WS_URL), new Draft_6455());
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
        if (websocketClient == null) {
            logger.warn("WebSocket client not initialized");
            return;
        }
        try {
            websocketClient.connectBlocking();
            connected.set(true);
            logger.info("BinanceUs WebSocket stream connected");
        } catch (InterruptedException e) {
            logger.error("Failed to connect WebSocket stream", e);
            Thread.currentThread().interrupt();
            connected.set(false);
        }
    }

    @Override
    public void disconnectStream() {
        try {
            stopAllStreams();
            shutdownTradeExecutor();

            if (websocketClient != null) {
                websocketClient.close();
            }

            connected.set(false);
            logger.info("BinanceUs WebSocket stream disconnected");
        } catch (Exception e) {
            connected.set(false);
            logger.error("Failed to disconnect WebSocket stream", e);
        }
    }

    @Override
    public boolean isStreamConnected() {
        return connected.get() && websocketClient != null && websocketClient.isOpen();
    }

    @Override
    public void reconnectStream() {
        disconnectStream();
        try {
            Thread.sleep(1000); // Wait 1 second before reconnecting
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        connectStream();
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
        for (String streamName : new ArrayList<>(activeTradeStreams)) {
            safeUnsubscribe(streamName);
        }
        activeTradeStreams.clear();
        logger.info("BinanceUs: all active market streams stopped");
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        if (!isStreamConnected()) {
            logger.warn("Cannot stream ticker - WebSocket not connected");
            return;
        }
        if (tradePair == null || consumer == null) {
            return;
        }
        String streamName = (binanceSymbol(tradePair) + "@ticker").toLowerCase(Locale.ROOT);
        websocketClient.subscribeStream(streamName, (data) -> {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(data);
                Ticker ticker = new Ticker();
                ticker.setTradePair(tradePair);
                ticker.setLastPrice(node.path("c").asDouble(0.0));
                ticker.setHighPrice(node.path("h").asDouble(0.0));
                ticker.setLowPrice(node.path("l").asDouble(0.0));
                ticker.setVolume(node.path("v").asDouble(0.0));
                ticker.setQuoteAssetVolume(node.path("q").asDouble(0.0));
                ticker.setTradeCount(node.path("n").asLong(0L));
                consumer.onTicker(getName(), tradePair, ticker);
            } catch (Exception e) {
                logger.warn("Error processing ticker stream", e);
            }
        });
        logger.debug("Subscribed to ticker stream: {}", streamName);
    }

    private volatile ExecutorService tradeEventExecutor;

    private final Set<String> activeTradeStreams = ConcurrentHashMap.newKeySet();

    /**
     * Compatibility method for exchange APIs that call streamTrades(...).
     * Internally delegates to subscribeTrades(...) so duplicate protection is
     * centralized.
     */
    public void streamTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {
        subscribeTrades(tradePair, consumer);
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        if (websocketClient == null) {
            logger.warn("Cannot subscribe trade stream - WebSocket client is not initialized");
            return;
        }

        if (!isStreamConnected()) {
            logger.warn("Cannot subscribe trade stream - WebSocket is not connected. pair={}", tradePair);
            return;
        }

        String streamName = buildTradeStreamName(tradePair);

        if (!activeTradeStreams.add(streamName)) {
            logger.debug("Trade stream already subscribed: {}", streamName);
            return;
        }

        logger.info("Subscribing Binance trade stream: {}", streamName);

        websocketClient.subscribeStream(streamName, data -> {
            Trade trade;

            try {
                trade = parseTrade(data, tradePair);
            } catch (Exception exception) {
                logger.warn(
                        "Error parsing Binance trade stream. stream={} pair={} error={}",
                        streamName,
                        tradePair,
                        exception.getMessage(),
                        exception);
                return;
            }

            if (trade == null) {
                return;
            }

            dispatchTradeEvent(streamName, tradePair, trade, consumer);
        });
    }

    private String buildTradeStreamName(@NotNull TradePair tradePair) {
        return (binanceSymbol(tradePair) + "@trade").toLowerCase(Locale.ROOT);
    }

    private void dispatchTradeEvent(
            @NotNull String streamName,
            @NotNull TradePair tradePair,
            @NotNull Trade trade,
            @NotNull ExchangeStreamConsumer consumer) {
        tradeExecutor().execute(() -> {
            try {
                consumer.onTrade(getName(), tradePair, trade);
            } catch (Exception exception) {
                logger.warn(
                        "Error dispatching Binance trade event. stream={} pair={} error={}",
                        streamName,
                        tradePair,
                        exception.getMessage(),
                        exception);
            }
        });
    }

    private ExecutorService tradeExecutor() {
        ExecutorService current = tradeEventExecutor;
        if (current == null || current.isShutdown() || current.isTerminated()) {
            synchronized (this) {
                current = tradeEventExecutor;
                if (current == null || current.isShutdown() || current.isTerminated()) {
                    tradeEventExecutor = Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, "binance-trade-event-dispatcher");
                        thread.setDaemon(true);
                        return thread;
                    });
                    current = tradeEventExecutor;
                }
            }
        }
        return current;
    }

    private void shutdownTradeExecutor() {
        ExecutorService current = tradeEventExecutor;
        if (current != null) {
            current.shutdownNow();
            tradeEventExecutor = null;
        }
    }

    private void safeUnsubscribe(@NotNull String streamName) {
        if (websocketClient == null) {
            return;
        }
        try {
            websocketClient.unsubscribeStream(streamName);
            logger.debug("Unsubscribed from stream: {}", streamName);
        } catch (Exception exception) {
            logger.warn(
                    "Failed to unsubscribe stream. stream={} error={}",
                    streamName,
                    exception.getMessage(),
                    exception);
        }
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        if (!isStreamConnected()) {
            logger.warn("Cannot stream order book - WebSocket not connected");
            return;
        }
        if (tradePair == null || consumer == null) {
            return;
        }
        String streamName = (binanceSymbol(tradePair) + "@depth@100ms").toLowerCase(Locale.ROOT);
        websocketClient.subscribeStream(streamName, (data) -> {
            try {
                OrderBook orderBook = parseOrderBook(data, tradePair);
                consumer.onOrderBook(getName(), tradePair, orderBook);
            } catch (Exception e) {
                logger.warn("Error processing order book stream", e);
            }
        });
        logger.debug("Subscribed to order book stream: {}", streamName);
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        if (!isStreamConnected()) {
            logger.warn("Cannot stream candles - WebSocket not connected");
            return;
        }
        if (tradePair == null || consumer == null) {
            return;
        }
        String interval = supportsTimeframe(secondsPerCandle);
        String streamName = (binanceSymbol(tradePair) + "@klines_" + interval).toLowerCase(Locale.ROOT);
        websocketClient.subscribeStream(streamName, (data) -> {
            try {
                JsonNode node;
                node = OBJECT_MAPPER.readTree(data);
                JsonNode kline = node.path("k");
                if (kline.isEmpty())
                    return;

                CandleData candle = new CandleData(

                        kline.path("o").asDouble(0.0),
                        kline.path("h").asDouble(0.0),
                        kline.path("l").asDouble(0.0),
                        kline.path("c").asDouble(0.0),
                        kline.path("T").asInt(),
                        kline.path("v").asDouble(0.0));
                CandleData.symbol = tradePair;

                consumer.onCandle(getName(), tradePair, candle);
            } catch (Exception e) {
                logger.warn("Error processing candle stream", e);
            }
        });
        logger.debug("Subscribed to candles stream: {}", streamName);
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        logger.debug("Account streaming requires authentication - not available in paper trading mode");
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        logger.debug("Balance streaming requires authentication - not available in paper trading mode");
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        logger.debug("Order streaming requires authentication - not available in paper trading mode");
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        logger.debug("Fill streaming requires authentication - not available in paper trading mode");
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        logger.debug("Position streaming not supported for spot trading");
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        if (websocketClient != null && tradePair != null) {
            String streamName = (binanceSymbol(tradePair) + "@ticker").toLowerCase(Locale.ROOT);
            websocketClient.unsubscribeStream(streamName);
            logger.debug("Unsubscribed from ticker stream: {}", streamName);
        }
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }

        String streamName = buildTradeStreamName(tradePair);

        if (!activeTradeStreams.remove(streamName)) {
            logger.debug("Trade stream was not active: {}", streamName);
            return;
        }

        safeUnsubscribe(streamName);
        logger.debug("Unsubscribed from trades stream: {}", streamName);
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        if (websocketClient != null && tradePair != null) {
            String streamName = (binanceSymbol(tradePair) + "@depth@100ms").toLowerCase(Locale.ROOT);
            websocketClient.unsubscribeStream(streamName);
            logger.debug("Unsubscribed from order book stream: {}", streamName);
        }
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        if (websocketClient != null && tradePair != null) {
            String interval = supportsTimeframe(secondsPerCandle);
            String streamName = (binanceSymbol(tradePair) + "@klines_" + interval).toLowerCase(Locale.ROOT);
            websocketClient.unsubscribeStream(streamName);
            logger.debug("Unsubscribed from candles stream: {}", streamName);
        }
    }

    @Override
    public void stopAccountStream() {
        logger.debug("Account stream not available");
    }

    @Override
    public void stopBalancesStream() {
        logger.debug("Balances stream not available");
    }

    @Override
    public void stopOrdersStream() {
        logger.debug("Orders stream not available");
    }

    @Override
    public void stopFillsStream() {
        logger.debug("Fills stream not available");
    }

    @Override
    public void stopPositionsStream() {
        logger.debug("Positions stream not available");
    }

    @Override
    public boolean supportsAccountStreaming() {
        return false; // Requires authentication and listenKey
    }

    @Override
    public boolean supportsOrderStreaming() {
        return false; // Requires authentication and listenKey
    }

    @Override
    public boolean supportsFillStreaming() {
        return false; // Requires authentication and listenKey
    }

    @Override
    public boolean supportsPositionStreaming() {
        return false;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return false; // Requires authentication and listenKey
    }

    @Override
    public boolean supportsTickerStreaming() {
        return true; // Public WebSocket stream available
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return true; // Public WebSocket stream available
    }

    @Override
    public boolean supportsCandleStreaming() {
        return true; // Public WebSocket stream available
    }

    @Override
    public boolean supportsTradeStreaming() {
        return true; // Public WebSocket stream available
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
                Timeframe.W1);
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
        return websocketClient != null;
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
        // NOTE: Using WebSocket streaming (streamTrades) is preferred to avoid API rate
        // limiting
        // This REST API method should only be used for historical data or when
        // WebSocket is unavailable
        if (!hasCredentials()) {
            logger.debug("Paper trading mode - returning empty trades");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (isSignedRestCoolingDown()) {
            logger.debug("Skipping Binance US recent trades REST poll during rate-limit cooldown");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add significant delay to avoid rate limiting
                Thread.sleep(1000);

                // Validate symbol before building URL
                String symbol = binanceSymbol(tradePair);
                if (symbol == null || symbol.isBlank()) {
                    throw new IllegalArgumentException("Failed to generate valid Binance symbol for " + tradePair);
                }

                Map<String, String> params = new LinkedHashMap<>();
                params.put("symbol", symbol);
                // /api/v3/trades is a PUBLIC endpoint that only accepts symbol and limit
                // Do NOT use sendSignedBinanceUsRequest for this endpoint
                params.put("limit", "500"); // Binance max per request

                // Build unsigned request (public endpoint)
                String query = formEncode(params);
                if (query == null || query.isBlank()) {
                    throw new IllegalArgumentException("Failed to generate valid query string for recent trades");
                }

                String url = BINANCE_US_REST_URL + "/api/v3/trades?" + query;
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("Failed to construct valid URL: " + url);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "InvestPro/1.0")
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = sendWithRetry(request);

                if (response.statusCode() != 200) {
                    if (response.statusCode() == 429 || response.statusCode() == 418) {

                        activatePublicRestCooldown(response);
                    }
                    logger.warn("Failed to fetch recent trades: HTTP {}", response.statusCode());
                    return Collections.emptyList();
                }

                JsonNode responseNode = OBJECT_MAPPER.readTree(response.body());
                List<Trade> trades = new ArrayList<>();

                if (responseNode.isArray()) {
                    for (JsonNode tradeNode : responseNode) {
                        Trade trade = parseTrade(tradeNode.toString(), tradePair);
                        if (trade != null) {
                            trades.add(trade);
                        }
                    }
                }

                logger.debug("Fetched {} recent trades for {}", trades.size(), tradePair);
                return trades;
            } catch (Exception exception) {
                logger.warn("Failed to fetch recent trades (prefer using WebSocket streaming)", exception);
                return Collections.emptyList();
            }
        });
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
        return new BinanceCandleDataSupplier(i, tradePair, BINANCE_US_REST_URL + "/api/v3");
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        Objects.requireNonNull(order, "order must not be null");
        String type = order.getType() == null ? "MARKET" : order.getType().trim().toUpperCase(Locale.ROOT);
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        if ("LIMIT".equals(type)) {
            return createLimitOrder(tradePairFromSymbol(order.getSymbol()), side, order.getQuantity(),
                    order.getPrice());
        }
        return createMarketOrder(tradePairFromSymbol(order.getSymbol()), side, order.getQuantity());
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

        if (!hasCredentials()) {
            // Paper trading: remove from in-memory orders
            orders.remove(orderId);
            logger.info("[PAPER] Order {} canceled", orderId);
            return CompletableFuture.completedFuture(orderId);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("orderId", orderId);
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));

                JsonNode response = sendSignedBinanceUsRequest("DELETE", "/api/v3/order", params);
                JsonNode cancelledOrderId = response.get("orderId");
                String result = cancelledOrderId == null ? orderId : cancelledOrderId.asText();
                logger.info("Order {} canceled successfully", result);
                return result;
            } catch (Exception exception) {
                logger.error("Failed to cancel order {}", orderId, exception);
                throw new IllegalStateException("Failed to cancel order: " + orderId, exception);
            }
        });
    }

    private boolean hasCredentials() {
        return credential.apiKey() != null;
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (!hasCredentials()) {
            // Paper trading: remove all orders
            orderIds.forEach(orders::remove);
            logger.info("[PAPER] {} orders canceled", orderIds.size());
            return CompletableFuture.completedFuture(new ArrayList<>(orderIds));
        }

        return CompletableFuture.supplyAsync(() -> {
            List<String> cancelledOrderIds = new ArrayList<>();
            for (String orderId : orderIds) {
                try {
                    cancelOrder(orderId).join();
                    cancelledOrderIds.add(orderId);
                } catch (Exception exception) {
                    logger.warn("Failed to cancel order {}", orderId, exception);
                }
            }
            logger.info("Canceled {} out of {} orders", cancelledOrderIds.size(), orderIds.size());
            return cancelledOrderIds;
        });
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        if (!hasCredentials()) {
            // Paper trading: clear all orders
            int count = orders.size();
            orders.clear();
            logger.info("[PAPER] All {} orders canceled", count);
            return CompletableFuture.completedFuture("Canceled " + count + " orders");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));

                JsonNode response = sendSignedBinanceUsRequest("DELETE", "/api/v3/openOrders", params);
                int cancelledCount = response.isArray() ? response.size() : 0;
                String result = "Canceled " + cancelledCount + " orders";
                logger.info(result);
                return result;
            } catch (Exception exception) {
                logger.error("Failed to cancel all orders", exception);
                throw new IllegalStateException("Failed to cancel all orders", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        if (!hasCredentials()) {
            logger.debug("Paper trading mode - no live order data");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("orderId", orderId);
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));

                JsonNode response = sendSignedBinanceUsRequest("GET", "/api/v3/order", params);
                Order order = parseOrder(response);
                logger.debug("Fetched order {}", orderId);
                return Optional.ofNullable(order);
            } catch (Exception exception) {
                logger.warn("Failed to fetch order {}", orderId, exception);
                return Optional.empty();
            }
        });
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
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return !hasCredentials();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        if (marketType == null) {
            return false;
        }
        String name = marketType.name().toUpperCase(Locale.ROOT);
        return name.contains("CRYPTO") || name.contains("SPOT");
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return Arrays.stream(MARKET_TYPES.values())
                .filter(this::supportsMarketType)
                .toList();
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("BINANCE_US")
                .exchangeId("binance_us")
                .displayName("Binance US")
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
                        Binance Us  Trade capability profile.
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
                .message("Binance US API credentials validated")
                .checkedAt(Instant.now())
                .build();
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
        if (!isPaperTrading() && hasCredentials()) {
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
            trade.setTimestamp(Instant.now());
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
            trade.setTimestamp(Instant.now());
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
        if (isPaperTrading()) {
            connected.set(true);
            logger.info("Binance US paper mode selected; live network connection skipped.");
            return;
        }
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
                boolean connected = websocketClient.connectBlocking(10, TimeUnit.SECONDS);
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
        stopAllStreams();
        shutdownTradeExecutor();

        if (listenKeyHeartbeatExecutor != null) {
            listenKeyHeartbeatExecutor.shutdownNow();
            listenKeyHeartbeatExecutor = null;
        }

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
    public CompletableFuture<List<SymbolTradability>> fetchTradabilityStatus(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<String, JsonNode> exchangeInfo = loadExchangeInfoBySymbol();
            return pairs.stream()
                    .filter(Objects::nonNull)
                    .map(pair -> mapBinanceUsTradability(pair, exchangeInfo.get(binanceUsSymbol(pair))))
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
            return mapBinanceUsTradability(pair, exchangeInfo.get(binanceUsSymbol(pair)));
        });
    }

    private Map<String, JsonNode> loadExchangeInfoBySymbol() {
        String url = BINANCE_US_REST_URL + "/api/v3/exchangeInfo";

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
            logger.warn("Unable to load Binance US exchangeInfo for tradability mapping", exception);
            return Map.of();
        }
    }

    private String binanceUsSymbol(TradePair pair) {
        return pair == null ? "" : (pair.getBaseCode() + pair.getCounterCode()).toUpperCase(Locale.ROOT);
    }

    private SymbolTradability mapBinanceUsTradability(TradePair pair, JsonNode symbolNode) {
        if (pair == null) {
            return defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null");
        }
        if (symbolNode == null || symbolNode.isMissingNode()) {
            return defaultTradability(pair, TradabilityStatus.PERMISSION_DENIED,
                    "Binance US symbol not available for this account/region");
        }

        String statusValue = symbolNode.path("status").asText("UNKNOWN").toUpperCase(Locale.ROOT);
        TradabilityStatus status = switch (statusValue) {
            case "TRADING" -> TradabilityStatus.FULLY_TRADABLE;
            case "HALT" -> TradabilityStatus.HALTED;
            case "BREAK" -> TradabilityStatus.MARKET_CLOSED;
            default -> TradabilityStatus.DISABLED;
        };

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

        boolean marginAllowed = symbolNode.path("isMarginTradingAllowed").asBoolean(false);

        String reason = status == TradabilityStatus.FULLY_TRADABLE
                ? (orderSubmissionAllowed
                        ? "Binance US symbol is tradable"
                        : "Binance US symbol is tradable; order submission is unavailable until exchange session is active")
                : "Binance US symbol status=" + statusValue;

        return new SymbolTradability(
                getExchangeId(),
                pair,
                symbolNode.path("symbol").asText(binanceUsSymbol(pair)),
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
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.completedFuture(
                tradePairs.stream()
                        .map(this::getLivePrice)
                        .filter(Objects::nonNull)
                        .toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (!isPaperTrading() && hasCredentials()) {
            if (isSignedRestCoolingDown()) {
                logger.debug("Skipping Binance US account REST poll during rate-limit cooldown");
                return CompletableFuture.completedFuture(paperAccountSnapshot(false));
            }
            return CompletableFuture.supplyAsync(this::fetchLiveAccount)
                    .exceptionally(exception -> {
                        Throwable root = rootCause(exception);
                        if (root instanceof BinanceUsRateLimitException || isBinanceRateLimitMessage(root)) {
                            logger.warn(
                                    "Binance US account REST is rate limited or temporarily banned. Using cached account snapshot: {}",
                                    root.getMessage());
                            return paperAccountSnapshot(false);
                        }
                        throw new CompletionException(exception);
                    });
        }
        return CompletableFuture.supplyAsync(() -> paperAccountSnapshot(true));
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

    private Account paperAccountSnapshot(boolean paperTrading) {
        Account account = new Account();
        double equity = balances.values().stream().mapToDouble(Double::doubleValue).sum();
        account.setTotalBalance(balances.getOrDefault("USDT", 0.0));
        account.setAvailableBalance(balances.getOrDefault("USDT", 0.0));
        account.setEquity(equity);
        account.setBalances(new LinkedHashMap<>(balances));
        account.setAvailableBalances(new LinkedHashMap<>(balances));
        account.setExchangeId("binanceus");
        account.setBrokerName("Binance US");
        account.setPaperTrading(paperTrading);
        account.setConnected(true);
        account.setUpdatedAt(Instant.now());
        logger.debug("[{}] Account summary: Equity=${}, Balance=${}",
                paperTrading ? "PAPER" : "CACHED",
                equity,
                balances.getOrDefault("USDT", 0.0));
        return account;
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
        if (isPublicRestCoolingDown()) {
            logger.debug("Skipping Binance US order book REST poll during rate-limit cooldown");
            return CompletableFuture.completedFuture(new OrderBook(tradePair));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                waitForOrderBookSlot();

                // Validate symbol before building URL
                String symbol = binanceSymbol(tradePair);
                if (symbol == null || symbol.isBlank()) {
                    throw new IllegalArgumentException("Failed to generate valid Binance symbol for " + tradePair);
                }

                Map<String, String> params = new LinkedHashMap<>();
                params.put("symbol", symbol);
                params.put("limit", "20");

                // OrderBook endpoint doesn't require signing
                String query = formEncode(params);
                if (query == null || query.isBlank()) {
                    throw new IllegalArgumentException("Failed to generate valid query string for order book");
                }

                String url = BINANCE_US_REST_URL + "/api/v3/depth?" + query;
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("Failed to construct valid URL: " + url);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "InvestPro/1.0")
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = sendWithRetry(request);
                JsonNode body = OBJECT_MAPPER.readTree(response.body());

                if (response.statusCode() != 200) {
                    if (response.statusCode() == 429 || response.statusCode() == 418) {
                        activatePublicRestCooldown(response);
                    }
                    if (response.statusCode() >= 400 && response.statusCode() < 500) {
                        logger.debug("Skipping Binance US order book for {}: HTTP {}",
                                tradePair, response.statusCode());
                    } else {
                        logger.warn("Failed to fetch order book: HTTP {}", response.statusCode());
                    }
                    return new OrderBook(tradePair);
                }

                OrderBook orderBook = parseOrderBook(body.toString(), tradePair);
                logger.debug("Fetched order book for {}", tradePair);
                return orderBook;
            } catch (Exception exception) {
                logger.error("Failed to fetch order book for {}", tradePair, exception);
                return new OrderBook(tradePair);
            }
        });
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        if (!hasCredentials()) {
            logger.debug("Paper trading mode - no live open orders");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (isSignedRestCoolingDown()) {
            logger.debug("Skipping Binance US open-orders REST poll during rate-limit cooldown");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add rate-limiting delay to avoid HTTP 418 bans
                // NOTE: Prefer using WebSocket streaming (streamOrders) instead of polling REST
                // API
                Thread.sleep(1500);

                Map<String, String> params = new LinkedHashMap<>();
                if (tradePair != null) {
                    params.put("symbol", binanceSymbol(tradePair));
                }
                // Use larger recvWindow (10000ms) to handle clock skew between client and
                // Binance server
                // This avoids "Timestamp for this request is outside of the recvWindow" errors
                params.put("recvWindow", "10000");
                // Let sendSignedBinanceUsRequest add timestamp automatically with better clock
                // handling
                // Do not manually set timestamp here to avoid clock skew issues

                JsonNode response = sendSignedBinanceUsRequest("GET", "/api/v3/openOrders", params);
                List<OpenOrder> openOrders = parseOpenOrders(response);

                logger.debug("Fetched {} open orders", openOrders.size());
                return openOrders;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while fetching open orders", e);
                return Collections.emptyList();
            } catch (Exception exception) {
                logger.error("Failed to fetch open orders", exception);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        if (!hasCredentials()) {
            logger.debug("Paper trading mode - no live open orders");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (isSignedRestCoolingDown()) {
            logger.debug("Skipping Binance US all-open-orders REST poll during rate-limit cooldown");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));

                JsonNode response = sendSignedBinanceUsRequest("GET", "/api/v3/openOrders", params);
                List<OpenOrder> openOrders = parseOpenOrders(response);

                logger.debug("Fetched {} total open orders", openOrders.size());
                return openOrders;
            } catch (Exception exception) {
                logger.error("Failed to fetch all open orders", exception);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        if (!hasCredentials()) {
            logger.debug("Paper trading mode - no live order history");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (tradePair == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("symbol", binanceSymbol(tradePair));
                params.put("limit", "500"); // Binance max is 500
                if (since != null) {
                    params.put("startTime", Long.toString(since.toEpochMilli()));
                }
                params.put("recvWindow", "5000");
                params.put("timestamp", Long.toString(System.currentTimeMillis()));

                JsonNode response = sendSignedBinanceUsRequest("GET", "/api/v3/allOrders", params);
                List<Order> orders = new ArrayList<>();

                if (response.isArray()) {
                    for (JsonNode orderNode : response) {
                        Order order = parseOrder(orderNode);
                        if (order != null) {
                            orders.add(order);
                        }
                    }
                }

                logger.debug("Fetched {} historical orders for {}", orders.size(), tradePair);
                return orders;
            } catch (Exception exception) {
                logger.error("Failed to fetch order history for {}", tradePair, exception);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        logger.debug("Binance US spot trading does not support positions");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(new ArrayList<>(positions));
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
        if (tradePair == null || size <= 0) {
            logger.warn("Invalid buy parameters: tradePair={}, size={}", tradePair, size);
            return;
        }
        try {
            // Convert to a limit order with the specified slippage
            double limitPrice = getLivePrice(tradePair).getLastPrice() * (1.0 + Math.abs(slippage) / 100.0);
            createLimitOrder(tradePair, Side.BUY, size, limitPrice).join();
            logger.info("Buy order placed: {} {} at ${}", size, tradePair, limitPrice);
        } catch (Exception e) {
            logger.error("Failed to place buy order: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {
        if (tradePair == null || size <= 0) {
            logger.warn("Invalid sell parameters: tradePair={}, size={}", tradePair, size);
            return;
        }
        try {
            // Convert to a limit order with the specified slippage
            double limitPrice = getLivePrice(tradePair).getLastPrice() * (1.0 - Math.abs(slippage) / 100.0);
            createLimitOrder(tradePair, Side.SELL, size, limitPrice).join();
            logger.info("Sell order placed: {} {} at ${}", size, tradePair, limitPrice);
        } catch (Exception e) {
            logger.error("Failed to place sell order: {}", e.getMessage(), e);
        }
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        if (apiKey == null || apiKey.isBlank()) {
            return AuthResult.failure("Binance US credentials are not configured");
        }
        return AuthResult.success("Binance US authentication validated");
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

    private @NotNull Account fetchLiveAccount() {
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
            account.setUpdatedAt(Instant.now());
            return account;
        } catch (Exception exception) {
            if (exception instanceof BinanceUsRateLimitException || isBinanceRateLimitMessage(exception)) {
                logger.warn("Unable to fetch live Binance US account during rate-limit cooldown: {}",
                        exception.getMessage());
                return paperAccountSnapshot(false);
            }
            throw new RuntimeException("Unable to fetch Binance US account.", exception);
        }
    }

    private JsonNode sendSignedBinanceUsRequest(String method, String path, Map<String, String> params)
            throws Exception {
        try {
            return sendSignedBinanceUsRequestOnce(method, path, params);
        } catch (RuntimeException exception) {
            if (!isTimestampOutsideRecvWindow(exception)) {
                throw exception;
            }

            logger.warn(
                    "Binance US signed request timestamp drift detected on {}. Syncing server time and retrying once.",
                    path);
            syncBinanceUsServerTime(true);
            return sendSignedBinanceUsRequestOnce(method, path, params);
        }
    }

    private JsonNode sendSignedBinanceUsRequestOnce(String method, String path, Map<String, String> params)
            throws Exception {
        waitForSignedRestSlot(path);
        syncBinanceUsServerTime(false);

        Map<String, String> signedParams = new LinkedHashMap<>(params);
        signedParams.put("recvWindow", Long.toString(BINANCE_US_RECV_WINDOW_MS));
        signedParams.put("timestamp", Long.toString(binanceUsTimestamp()));

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
            if (response.statusCode() == 429 || response.statusCode() == 418) {
                activateSignedRestCooldown(response, body, path);
            }
            throw new RuntimeException(
                    "Binance US API returned HTTP %d: %s".formatted(response.statusCode(), body));
        }
        return body;
    }

    private long binanceUsTimestamp() {
        return System.currentTimeMillis() + serverTimeOffsetMs;
    }

    private void syncBinanceUsServerTime(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastServerTimeSyncMs < BINANCE_US_TIME_SYNC_TTL_MS) {
            return;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (!force && now - lastServerTimeSyncMs < BINANCE_US_TIME_SYNC_TTL_MS) {
                return;
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BINANCE_US_REST_URL + "/api/v3/time"))
                        .header("User-Agent", "InvestPro/1.0")
                        .GET()
                        .build();
                long before = System.currentTimeMillis();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                long after = System.currentTimeMillis();

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warn("Unable to sync Binance US server time. HTTP {}: {}",
                            response.statusCode(),
                            response.body());
                    lastServerTimeSyncMs = now;
                    return;
                }

                JsonNode body = OBJECT_MAPPER.readTree(response.body());
                long serverTime = body.path("serverTime").asLong(0L);
                if (serverTime <= 0L) {
                    logger.warn("Unable to sync Binance US server time. Missing serverTime in response: {}", body);
                    lastServerTimeSyncMs = now;
                    return;
                }

                long localMidpoint = before + ((after - before) / 2L);
                serverTimeOffsetMs = serverTime - localMidpoint;
                lastServerTimeSyncMs = after;
                logger.debug("Binance US server time synced. offsetMs={}", serverTimeOffsetMs);
            } catch (Exception exception) {
                lastServerTimeSyncMs = now;
                logger.warn("Unable to sync Binance US server time; using local clock. {}", exception.getMessage());
            }
        }
    }

    private boolean isTimestampOutsideRecvWindow(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("\"code\":-1021")
                            || message.contains("outside of the recvWindow")
                            || message.contains("Timestamp for this request"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isSignedRestCoolingDown() {
        return System.currentTimeMillis() < signedRestCooldownUntilMs;
    }

    private void waitForSignedRestSlot(String path) throws InterruptedException {
        long now = System.currentTimeMillis();
        long cooldownUntil = signedRestCooldownUntilMs;
        if (now < cooldownUntil) {
            long waitMs = cooldownUntil - now;
            logger.warn("Binance US signed REST cooldown active for {}ms. Skipping {}", waitMs, path);
            throw new BinanceUsRateLimitException("Binance US signed REST cooldown active for " + waitMs + "ms");
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            long earliest = lastSignedRestRequestMs + SIGNED_REST_MIN_INTERVAL_MS;
            if (now < earliest) {
                Thread.sleep(earliest - now);
            }
            lastSignedRestRequestMs = System.currentTimeMillis();
        }
    }

    private void activateSignedRestCooldown(HttpResponse<String> response, JsonNode body, String path) {
        long cooldownMs = parseRetryAfterMillis(response)
                .or(() -> parseBinanceBanUntilMillis(body))
                .orElse(SIGNED_REST_429_COOLDOWN_MS);
        signedRestCooldownUntilMs = Math.max(
                signedRestCooldownUntilMs,
                System.currentTimeMillis() + cooldownMs);
        logger.warn(
                "Binance US rate limit on {}. Cooling down signed REST for {}ms. Prefer WebSocket streams for live updates.",
                path,
                cooldownMs);
    }

    private boolean isPublicRestCoolingDown() {
        return System.currentTimeMillis() < publicRestCooldownUntilMs;
    }

    private void waitForOrderBookSlot() throws InterruptedException {
        synchronized (this) {
            long now = System.currentTimeMillis();
            long earliest = lastOrderBookRequestMs + PUBLIC_REST_MIN_INTERVAL_MS;
            if (now < earliest) {
                Thread.sleep(earliest - now);
            }
            lastOrderBookRequestMs = System.currentTimeMillis();
        }
    }

    private void activatePublicRestCooldown(HttpResponse<String> response) {
        long cooldownMs = parseRetryAfterMillis(response)
                .orElse(PUBLIC_REST_429_COOLDOWN_MS);
        publicRestCooldownUntilMs = Math.max(
                publicRestCooldownUntilMs,
                System.currentTimeMillis() + cooldownMs);
        logger.warn(
                "Binance US public REST rate limit on {}. Cooling down public REST for {}ms.",
                "/api/v3/depth",
                cooldownMs);
    }

    private Optional<Long> parseRetryAfterMillis(HttpResponse<String> response) {
        return response.headers()
                .firstValue("Retry-After")
                .flatMap(value -> {
                    try {
                        return Optional.of(Math.max(1_000L, Long.parseLong(value.trim()) * 1_000L));
                    } catch (NumberFormatException ignored) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<Long> parseBinanceBanUntilMillis(JsonNode body) {
        String message = body == null ? "" : body.path("msg").asText("");
        Matcher matcher = Pattern
                .compile("until\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            long banUntilMs = Long.parseLong(matcher.group(1));
            long waitMs = banUntilMs - System.currentTimeMillis();
            return waitMs <= 0 ? Optional.empty() : Optional.of(Math.max(1_000L, waitMs));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean isBinanceRateLimitMessage(Throwable exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        return message.contains("HTTP 429")
                || message.contains("HTTP 418")
                || message.contains("code\":-1003")
                || message.contains("Too much request weight")
                || message.contains("Way too much request weight");
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? exception : current;
    }

    private static class BinanceUsRateLimitException extends RuntimeException {
        BinanceUsRateLimitException(String message) {
            super(message);
        }
    }

    private static String binanceSymbol(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        String baseCode = tradePair.getBaseCode();
        String counterCode = tradePair.getCounterCode();

        if (baseCode == null || baseCode.isBlank()) {
            throw new IllegalArgumentException("TradePair base code is null or blank for " + tradePair);
        }
        if (counterCode == null || counterCode.isBlank()) {
            throw new IllegalArgumentException("TradePair counter code is null or blank for " + tradePair);
        }

        return (baseCode + counterCode).toUpperCase(Locale.ROOT);
    }

    private static TradePair tradePairFromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new RuntimeException("order symbol must not be blank" + "USDT");
        }
        String normalized = symbol.trim().replace("-", "/").toUpperCase(Locale.ROOT);
        String base;
        String quote;
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            base = parts[0];
            quote = parts[1];
        } else {
            quote = Stream.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR", "XLM", "DAI")
                    .filter(normalized::endsWith)
                    .findFirst()
                    .orElse("USDT");
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

    /**
     * Execute HTTP request with exponential backoff retry on transient failures
     */
    private static HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("HttpRequest cannot be null");
        }

        long delayMs = INITIAL_RETRY_DELAY_MS;
        Exception lastException = null;

        for (int attempt = 0; true; attempt++) {
            try {
                // Validate request URI before sending
                if (request.uri() == null) {
                    throw new IllegalArgumentException("HttpRequest URI is null");
                }

                String uriStr = request.uri().toString();
                if (uriStr == null || uriStr.isBlank() || !uriStr.startsWith("https://")) {
                    throw new IllegalArgumentException("Invalid URI format: " + uriStr);
                }

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                // Don't retry on rate limit or auth errors
                if (response.statusCode() == 429 || response.statusCode() == 418 ||
                        response.statusCode() == 401 || response.statusCode() == 403) {
                    return response;
                }

                // Success - return immediately
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }

                // For other HTTP errors, try a few times
                if (attempt < MAX_RETRIES && response.statusCode() >= 500) {
                    delayMs = (long) (delayMs * RETRY_BACKOFF_MULTIPLIER);
                    Thread.sleep(delayMs);
                    continue;
                }

                return response;
            } catch (java.nio.channels.UnresolvedAddressException e) {
                lastException = e;
                logger.error("DNS resolution failed for URI: {}. Attempt {}/{}: {}",
                        request.uri(), attempt + 1, MAX_RETRIES + 1, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(delayMs);
                        delayMs = (long) (delayMs * RETRY_BACKOFF_MULTIPLIER);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                } else {
                    throw new RuntimeException(
                            "Failed to resolve DNS for " + request.uri() + " after " + (MAX_RETRIES + 1) + " attempts",
                            e);
                }
            } catch (java.io.IOException e) {
                lastException = e;

                // Only retry on connection-level errors
                if (attempt < MAX_RETRIES) {
                    logger.debug("HTTP request failed (attempt {}/{}): {}. Retrying in {}ms",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), delayMs);

                    try {
                        Thread.sleep(delayMs);
                        delayMs = (long) (delayMs * RETRY_BACKOFF_MULTIPLIER);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                } else {
                    throw e;
                }
            }
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
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("URL parameter key cannot be null or blank");
                    }
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException(
                                "URL parameter value cannot be null or blank for key: " + key);
                    }

                    return URLEncoder.encode(key, StandardCharsets.UTF_8)
                            + "="
                            + URLEncoder.encode(value, StandardCharsets.UTF_8);
                })
                .collect(Collectors.joining("&"));
    }

    // ========== Stream Helper Methods ==========

    /**
     * Generates a new user stream listen key for receiving account updates
     */
    private @Nullable String generateListenKey() {
        if (listenKey != null && !listenKey.isBlank()) {
            return listenKey; // Reuse existing key
        }

        if (!hasCredentials()) {
            return null;
        }

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("timestamp", Long.toString(System.currentTimeMillis()));

            JsonNode response = sendSignedBinanceUsRequest("POST", "/api/v3/userDataStream", params);
            listenKey = response.path("listenKey").asText();

            if (listenKey != null && !listenKey.isBlank()) {
                startListenKeyHeartbeat();
                logger.debug("Generated new listen key for user stream");
                return listenKey;
            }
        } catch (Exception exception) {
            logger.warn("Failed to generate listen key", exception);
        }
        return null;
    }

    /**
     * Starts a heartbeat task to keep the listen key alive
     */
    private void startListenKeyHeartbeat() {
        if (listenKeyHeartbeatExecutor == null || listenKeyHeartbeatExecutor.isShutdown()) {
            listenKeyHeartbeatExecutor = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "BinanceUS-ListenKeyHeartbeat");
                t.setDaemon(true);
                return t;
            });
        }

        listenKeyHeartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (listenKey != null && !listenKey.isBlank() && hasCredentials()) {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("listenKey", listenKey);
                    params.put("timestamp", Long.toString(System.currentTimeMillis()));

                    sendSignedBinanceUsRequest("PUT", "/api/v3/userDataStream", params);
                    logger.debug("Listen key heartbeat sent");
                }
            } catch (Exception exception) {
                logger.warn("Failed to send listen key heartbeat", exception);
            }
        }, 30, 30, TimeUnit.MINUTES); // Heartbeat every 30 minutes
    }

    // ========== JSON Parsing Helper Methods ==========

    /**
     * Parses a Binance API order response into an OpenOrder object
     */
    private OpenOrder parseOpenOrder(JsonNode orderNode) {
        try {
            if (orderNode == null || !orderNode.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();

            order.setOrderId(orderNode.path("orderId").asText());

            String symbol = orderNode.path("symbol").asText();
            TradePair tradePair = tradePairFromSymbol(symbol);
            order.setTradePair(tradePair);

            String side = orderNode.path("side").asText();
            order.setSide("SELL".equals(side) ? Side.SELL : Side.BUY);

            order.setPrice(orderNode.path("price").asDouble(0.0));
            order.setSize(orderNode.path("origQty").asDouble(0.0));
            order.setStatus(OpenOrder.OrderStatus.valueOf(orderNode.path("status").asText("UNKNOWN")));
            order.setCreatedAt(Instant.ofEpochMilli(orderNode.path("time").asLong()));
            order.setUpdatedAt(Instant.ofEpochMilli(orderNode.path("updateTime").asLong()));
            order.setFilledSize(orderNode.path("executedQty").asDouble(0.0));
            order.setOrderType(OpenOrder.OrderType.valueOf(orderNode.path("type").asText("MARKET")));
            order.setTimeInForce(orderNode.path("timeInForce").asText());

            return order;
        } catch (Exception exception) {
            logger.warn("Error parsing open order", exception);
            return null;
        }
    }

    /**
     * Parses a list of Binance API open order responses into OpenOrder objects.
     * Handles both single order nodes and arrays of order nodes synchronously.
     * 
     * @param responseNode JsonNode containing either a single order or array of
     *                     orders
     * @return List of OpenOrder objects (empty list if parsing fails or no orders)
     */
    private List<OpenOrder> parseOpenOrders(JsonNode responseNode) {
        List<OpenOrder> openOrders = new ArrayList<>();

        try {
            if (responseNode == null) {
                return openOrders;
            }

            if (responseNode.isArray()) {
                for (JsonNode orderNode : responseNode) {
                    OpenOrder order = parseOpenOrder(orderNode);
                    if (order != null) {
                        openOrders.add(order);
                    }
                }
            } else if (responseNode.isObject()) {
                // Handle single order response
                OpenOrder order = parseOpenOrder(responseNode);
                if (order != null) {
                    openOrders.add(order);
                }
            }

            logger.debug("Parsed {} open orders", openOrders.size());
        } catch (Exception exception) {
            logger.error("Failed to parse open orders", exception);
        }

        return openOrders;
    }

    /**
     * Parses a Binance API order response into an Order object
     */
    private Order parseOrder(JsonNode orderNode) {
        try {
            if (orderNode == null || !orderNode.isObject()) {
                return null;
            }

            Order order = new Order();
            order.setId(Long.valueOf(orderNode.path("orderId").asText()));

            String symbol = orderNode.path("symbol").asText();
            TradePair tradePair = tradePairFromSymbol(symbol);
            order.setTradePair(tradePair);

            String side = orderNode.path("side").asText();
            order.setType(side.equals("SELL") ? "SELL" : "BUY");

            order.setPrice(orderNode.path("price").asDouble(0.0));
            order.setQuantity(orderNode.path("origQty").asDouble(0.0));
            order.setStatus(orderNode.path("status").asText("UNKNOWN"));
            order.setCreatedAt(Instant.ofEpochMilli(orderNode.path("time").asLong()));
            order.setUpdatedAt(Instant.ofEpochMilli(orderNode.path("updateTime").asLong()));
            order.setFilledQuantity(orderNode.path("executedQty").asDouble(0.0));
            order.setCummulativeQuoteQty(orderNode.path("cummulativeQuoteQty").asDouble(0.0));
            order.setType(orderNode.path("type").asText("MARKET"));
            order.setTimeInForce(Instant.parse(orderNode.path("timeInForce").asText()));

            return order;
        } catch (Exception exception) {
            logger.warn("Error parsing order", exception);
            return null;
        }
    }

    /**
     * Parses trade data from WebSocket stream
     */
    private Trade parseTrade(String data, TradePair tradePair) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(data);
            Trade trade = new Trade();
            trade.setTradePair(tradePair);
            trade.setPrice(node.path("p").asDouble(0.0));
            trade.setAmount(node.path("q").asDouble(0.0));
            trade.setFee(node.path("q").asDouble(0.0) * 0.001); // Assume 0.1% fee
            trade.setTransactionType(node.path("m").asBoolean(false) ? Side.SELL : Side.BUY);
            trade.setTimestamp(Instant.ofEpochMilli(node.path("T").asLong()));
            trade.setLocalTradeId(node.path("t").asLong());
            return trade;
        } catch (Exception exception) {
            logger.debug("Error parsing trade", exception);
            return null;
        }
    }

    /**
     * Parses order book data from WebSocket stream or API response
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
            logger.debug("Error parsing order book", exception);
            return new OrderBook(tradePair);
        }
    }

    /**
     * Parses account data from WebSocket stream
     */
    private @Nullable Account parseAccount(JsonNode eventNode) {
        try {
            Account account = new Account();
            account.setExchangeId("binanceus");
            account.setBrokerName("Binance US");

            // Parse balances from the balances array
            Map<String, Double> balanceMap = new LinkedHashMap<>();
            Map<String, Double> availableMap = new LinkedHashMap<>();

            JsonNode balances = eventNode.path("B");
            if (balances.isArray()) {
                for (JsonNode balance : balances) {
                    String asset = balance.path("a").asText();
                    double free = balance.path("f").asDouble(0.0);
                    double locked = balance.path("l").asDouble(0.0);
                    balanceMap.put(asset, free + locked);
                    availableMap.put(asset, free);
                }
            }

            account.setBalances(balanceMap);
            account.setAvailableBalances(availableMap);
            account.setTotalBalance(availableMap.getOrDefault("USDT", 0.0));
            account.setAvailableBalance(availableMap.getOrDefault("USDT", 0.0));
            account.setEquity(balanceMap.values().stream().mapToDouble(Double::doubleValue).sum());
            account.setConnected(true);
            account.setUpdatedAt(Instant.now());

            return account;
        } catch (Exception exception) {
            logger.error("Error parsing account", exception);
            return null;
        }
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
