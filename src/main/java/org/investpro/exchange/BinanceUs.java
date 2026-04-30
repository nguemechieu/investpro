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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BinanceUs extends Exchange {
    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BINANCE_US_WS_URL = "wss://stream.binance.us:9443/ws";
    private ExchangeWebSocketClient websocketClient;

    public BinanceUs(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        
        try {
            this.websocketClient = createWebSocketClient();
        } catch (Exception ex) {
            logger.error("Failed to initialize BinanceUs websocket client", ex);
        }
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
        return null;
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
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        return null;
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return 0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return 0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return null;
    }

    @Override
    public boolean supportsLiveTrading() {
        return false;
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return false;
    }

    @Override
    public boolean supportsOrderBook() {
        return false;
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
        return false;
    }

    @Override
    public StreamTransport getStreamTransport() {
        return null;
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
        return false;
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
            return websocketClient != null
                    && websocketClient.connectionEstablished != null
                    && websocketClient.connectionEstablished.get();
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
        return null;
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
        logger.warn("createOrder not yet fully implemented for BinanceUs");
        return CompletableFuture.completedFuture(java.util.UUID.randomUUID().toString());
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side, double stopLoss, double takeProfit, double slippage) {
        return null;
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        return null;
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount, double limitPrice) {
        return null;
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return null;
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount, double entryPrice, double stopLoss, double takeProfit) {
        return null;
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
        return false;
    }

    @Override
    public boolean isPaperTrading() {
        return false;
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return false;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of();
    }

    @Override
    public void connect() {
        try {
            if (websocketClient != null && !websocketClient.isOpen()) {
                websocketClient.connect();
                logger.info("Connecting to BinanceUs websocket");
            }
        } catch (Exception exception) {
            logger.warn("Unable to connect BinanceUs websocket", exception);
        }
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void reconnect() {

    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        // Using Binance US API to get all trading pairs
        String url = "https://api.binanceapius.com/api/v3/exchangeInfo";
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
                    return tradePairs;  // Return empty list on API error
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
        return List.of();
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return false;
    }

    @Override
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
        return CompletableFuture.completedFuture("{}");
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        if (tradePair == null) {
            return null;
        }
        try {
            Ticker ticker = new Ticker();

            ticker.setBidPrice(0.0);
            ticker.setAskPrice(0.0);
            ticker.setLastPrice(0.0);
            ticker.setVolume(0.0);
            ticker.setTimestamp(System.currentTimeMillis());
            CompletableFuture<Ticker> tickers = fetchTicker(tradePair);
            tickers.complete(ticker);

            return tickers.get();
        } catch (Exception ex) {
            logger.error("Error getting live price", ex);
            return null;
        }
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return CompletableFuture.completedFuture(getLivePrice(tradePair));
        } catch (Exception ex) {
            logger.error("Error fetching ticker", ex);
            return CompletableFuture.completedFuture(null);
        }
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
                        .toList()
        );
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return null;
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        try {
            return CompletableFuture.completedFuture(getUserAccountDetails());
        } catch (Exception ex) {
            logger.error("Error fetching account", ex);
            return CompletableFuture.completedFuture(null);
        }
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
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
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
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(java.util.Optional.empty());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return CompletableFuture.completedFuture("");
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return CompletableFuture.completedFuture("");
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair cannot be null"));
        }
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        if (tradePair == null || since == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("TradePair and since cannot be null"));
        }
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {

    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {

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
