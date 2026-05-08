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
import org.investpro.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.bitfinex.BitfinexCandleDataSupplier;
import org.investpro.exchange.websocket.BitfinexWebSocketClient;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Bitfinex extends Exchange {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Bitfinex.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BITFINEX_WS_URL = "wss://api.bitfinex.com/ws/2";
    private static final String BITFINEX_REST_URL = "https://api.bitfinex.com";
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

    public Bitfinex(String s, String s1) {
        super(s, s1);
        initializePaperTradingAccount();

        try {
            this.websocketClient = createWebSocketClient();
        } catch (Exception ex) {
            logger.error("Failed to initialize Bitfinex websocket client", ex);
        }
    }

    private void initializePaperTradingAccount() {
        // Initialize with $10,000 USD for paper trading
        balances.put("USD", 10000.0);
        balances.put("BTC", 0.0);
        balances.put("ETH", 0.0);
        logger.info("Bitfinex paper trading account initialized with $10,000 USD");
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? "USD"
                : currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Constructor with Telegram token and email notification support
     */
    public Bitfinex(String apiKey, String apiSecret, String telegramToken, String emailNotification) {
        this(apiKey, apiSecret);
        this.setTelegramToken(telegramToken);
        this.setEmailNotification(emailNotification);
    }

    private ExchangeWebSocketClient createWebSocketClient() throws Exception {
        return new BitfinexWebSocketClient(URI.create(BITFINEX_WS_URL), new org.java_websocket.drafts.Draft_6455());
    }

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return getSelectedTradePair();
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
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
        return CompletableFuture.completedFuture(List.of());
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
        List<TradePair> pairs = getTradePairSymbol();
        return pairs.isEmpty() ? TradePair.of("BTC", "USD") : pairs.get(0);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair) {
        return new BitfinexCandleDataSupplier(i, tradePair);
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        if (order == null) {
            return failedFuture(new IllegalArgumentException("order must not be null"));
        }
        String type = order.getType() == null ? "MARKET" : order.getType().trim().toUpperCase(java.util.Locale.ROOT);
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        if ("LIMIT".equals(type)) {
            return createLimitOrder(tradePairFromSymbol(order.getSymbol(), "USD"), side, order.getQuantity(),
                    order.getPrice());
        }
        return createMarketOrder(tradePairFromSymbol(order.getSymbol(), "USD"), side, order.getQuantity());
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (hasCredentials()) {
            return submitBitfinexOrder(tradePair, side, amount, 0.0, "EXCHANGE MARKET");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            double fillPrice = 50000.0;
            if (side == Side.BUY) {
                double cost = amount * fillPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient balance");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient " + tradePair.getBaseCode());
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USD", balances.getOrDefault("USD", 0.0) + (amount * fillPrice));
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
            logger.info("[PAPER] Bitfinex market order {}: {} {}", orderId, side, amount);
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        if (hasCredentials()) {
            return submitBitfinexOrder(tradePair, side, amount, limitPrice, "EXCHANGE LIMIT");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            if (side == Side.BUY) {
                double cost = amount * limitPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient balance");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient " + tradePair.getBaseCode());
                }
                balances.put(tradePair.getBaseCode(), baseBalance - amount);
                balances.put("USD", balances.getOrDefault("USD", 0.0) + (amount * limitPrice));
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
            logger.info("[PAPER] Bitfinex limit order {}: {} {} @${}", orderId, side, amount, limitPrice);
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
            double entryPrice, double stopLoss, double takeProfit) {
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
        return failedFuture(unsupported("cancelAllOrders"));
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return failedFuture(unsupported("fetchOrder"));
    }

    @Override
    public String getSignal() {
        return "Bitfinex Spot Trading";
    }

    @Override
    public String getExchangeId() {
        return "bitfinex";
    }

    @Override
    public String getDisplayName() {
        return "Bitfinex";
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
    public void connect() {
        if (hasCredentials()) {
            try {
                fetchAccount().join();
                connected.set(true);
                logger.info("Connected to Bitfinex REST API");
                return;
            } catch (Exception exception) {
                connected.set(false);
                logger.warn("Unable to connect Bitfinex REST API", exception);
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
                logger.info("Connected to Bitfinex WebSocket");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("Bitfinex WebSocket connection interrupted", exception);
        } catch (Exception exception) {
            logger.warn("Unable to connect Bitfinex WebSocket", exception);
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
        // Using Bitfinex API to get all trading pairs
        String url = "https://api-pub.bitfinex.com/v2/conf/pub:list:pair:all";
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
            logger.info("Bitfinex API response received");

            // Bitfinex API returns a map with exchange pairs array
            // Response format: {"exchange": ["tBTCUSD", "tETHUSD", ...], "margin": [...],
            // ...}
            if (!res.isObject()) {
                logger.warn("Bitfinex API returned unexpected format");
                return tradePairs;
            }

            // Get the exchange pairs (spot market pairs start with 't')
            JsonNode exchangePairs = res.has("exchange") ? res.get("exchange") : null;

            if (exchangePairs == null || !exchangePairs.isArray()) {
                logger.warn("Bitfinex API did not return exchange pairs array");
                return tradePairs;
            }

            for (JsonNode pairNode : exchangePairs) {
                String pairString = pairNode.asText();

                // Bitfinex pairs are formatted as "tBTCUSD", "tETHBTC", etc.
                if (pairString == null || !pairString.startsWith("t") || pairString.length() < 7) {
                    continue;
                }

                // Remove the leading 't'
                String pair = pairString.substring(1);

                // Try to split the pair intelligently
                // Common pattern: 3-letter base currency, 3-letter quote currency
                if (pair.length() < 6) {
                    logger.debug("Skipping pair with invalid length: " + pair);
                    continue;
                }

                CryptoCurrency baseCurrency, counterCurrency;
                String baseAsset = pair.substring(0, 3);
                String quoteAsset = pair.substring(3);

                try {
                    // Create currencies
                    baseCurrency = new CryptoCurrency(baseAsset, baseAsset, baseAsset, 8, baseAsset, baseAsset);
                    counterCurrency = new CryptoCurrency(quoteAsset, quoteAsset, quoteAsset, 8, quoteAsset, quoteAsset);

                    TradePair tp = new TradePair(baseCurrency, counterCurrency);
                    tradePairs.add(tp);
                    logger.debug("Added trade pair: " + tp);
                } catch (SQLException | ClassNotFoundException e) {
                    logger.debug("Skipping pair " + baseAsset + "-" + quoteAsset + ": " + e.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching Bitfinex trade pairs", ex);
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
        return fetchOrderBook(tradePair);
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        if (tradePair == null)
            return Ticker.empty();
        Ticker ticker = new Ticker();
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
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(tradePairs.stream().map(this::getLivePrice).toList());
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
            account.setTotalBalance(balances.getOrDefault("USD", 0.0));
            account.setAvailableBalance(balances.getOrDefault("USD", 0.0));
            account.setEquity(equity);
            account.setBalances(new java.util.LinkedHashMap<>(balances));
            account.setExchangeId("bitfinex");
            account.setBrokerName("Bitfinex");
            account.setPaperTrading(true);
            account.setConnected(true);
            account.setUpdatedAt(java.time.Instant.now());
            return account;
        });
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(normalizeCurrency(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAvailableBalance(currencyCode);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(balances.values().stream().mapToDouble(Double::doubleValue).sum());
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
    public Account getUserAccountDetails() {
        return fetchAccount().join();
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public String getName() {
        return "Bitfinex";
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(new OrderBook(tradePair));
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
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(new ArrayList<>());
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
        return CompletableFuture.completedFuture("Closed all Bitfinex paper positions.");
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
    public void autoTrading(@NotNull Boolean auto, String signal) {

    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
            double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(tradePair != null && size > 0 && supportsMarketType(marketType));
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
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
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
        return true;
    }

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.WEBSOCKET;
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

    private CompletableFuture<String> submitBitfinexOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice,
            String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> payload = new LinkedHashMap<>();
                payload.put("type", type);
                payload.put("symbol", bitfinexSymbol(tradePair));
                payload.put("amount", decimal(side == Side.SELL ? -amount : amount));
                if ("EXCHANGE LIMIT".equals(type)) {
                    payload.put("price", decimal(limitPrice));
                }
                JsonNode response = sendAuthenticatedBitfinexRequest("/v2/auth/w/order/submit", payload);
                JsonNode orderId = response.at("/4/0/0");
                return orderId.isMissingNode() ? response.toString() : orderId.asText();
            } catch (Exception exception) {
                throw new IllegalStateException("Bitfinex order submission failed.", exception);
            }
        });
    }

    private Account fetchLiveAccount() {
        try {
            JsonNode response = sendAuthenticatedBitfinexRequest("/v2/auth/r/wallets", Map.of());
            Map<String, Double> liveBalances = new LinkedHashMap<>();
            Map<String, Double> availableBalances = new LinkedHashMap<>();
            if (response.isArray()) {
                for (JsonNode wallet : response) {
                    if (wallet.isArray() && wallet.size() >= 3) {
                        String currency = wallet.get(1).asText().toUpperCase(java.util.Locale.ROOT);
                        double balance = wallet.get(2).asDouble(0.0);
                        double available = wallet.size() > 4 && !wallet.get(4).isNull()
                                ? wallet.get(4).asDouble(balance)
                                : balance;
                        if (balance != 0.0 || available != 0.0) {
                            liveBalances.put(currency, balance);
                            availableBalances.put(currency, available);
                        }
                    }
                }
            }
            Account account = new Account();
            account.setTotalBalance(availableBalances.getOrDefault("USD", 0.0));
            account.setAvailableBalance(availableBalances.getOrDefault("USD", 0.0));
            account.setEquity(liveBalances.values().stream().mapToDouble(Double::doubleValue).sum());
            account.setBalances(liveBalances);
            account.setAvailableBalances(availableBalances);
            account.setExchangeId("bitfinex");
            account.setBrokerName("Bitfinex");
            account.setPaperTrading(false);
            account.setConnected(true);
            account.setUpdatedAt(java.time.Instant.now());
            return account;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch Bitfinex account.", exception);
        }
    }

    private JsonNode sendAuthenticatedBitfinexRequest(String path, Map<String, String> payload) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        String nonce = Long.toString(System.currentTimeMillis() * 1000);
        String signaturePayload = "/api" + path + nonce + body;
        String signature = ExchangeSigning.hmacHex("HmacSHA384", apiSecret, signaturePayload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BITFINEX_REST_URL + path))
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", signature)
                .header("Content-Type", "application/json")
                .header("User-Agent", "InvestPro/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode responseBody = OBJECT_MAPPER.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Bitfinex API returned HTTP %d: %s".formatted(response.statusCode(), responseBody));
        }
        return responseBody;
    }

    private static String bitfinexSymbol(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        return "t" + (tradePair.getBaseCode() + tradePair.getCounterCode()).toUpperCase(java.util.Locale.ROOT);
    }

    private static TradePair tradePairFromSymbol(String symbol, String defaultQuote) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("order symbol must not be blank");
        }
        String normalized = symbol.trim()
                .replace("-", "/")
                .toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("T") && !normalized.contains("/")) {
            normalized = normalized.substring(1);
        }
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
        if (!Double.isFinite(value) || value == 0.0) {
            throw new IllegalArgumentException("Order amount and price values must be non-zero finite numbers.");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
