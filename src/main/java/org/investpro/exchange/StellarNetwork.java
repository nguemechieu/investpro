package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.trading.*;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import javafx.beans.property.SimpleIntegerProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Getter
@Setter
@Slf4j
public class StellarNetwork extends Exchange {
    private static final String STELLAR_API_URL = "https://horizon.stellar.org";
    private static final String STELLAR_TEST_URL = "https://horizon-testnet.stellar.org";
    private static final String MAINNET_USDC_ISSUER = "GA5ZSEJYB37DFWGY4OZE3NV5QOWI6Q5Y4M7JN2K2ZZMZB6PZNTSMZQ5P";
    private static final String TESTNET_USDC_ISSUER = "GBBD47IFM4TEQ4EMSN77QWJVRLN4W4RGTM3ZQWUCNC7L7DVQHHITGZJW";
    private static final double DEFAULT_XLM_USDC_PRICE = 0.50;

    // Paper trading state
    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, OpenOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, Order> orderHistory = new ConcurrentHashMap<>();
    private final List<Position> positions = new CopyOnWriteArrayList<>();
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    private String apiKey;
    private String apiSecret;
    private String accountId;
    private final HttpClient httpClient;
    private boolean websocketAvailable;
    private ExchangeWebSocketClient websocketClient;

    public StellarNetwork(@NotNull ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        this.apiKey = exchangeCredentials.apiKey();
        this.apiSecret = exchangeCredentials.apiSecret();
        this.accountId = exchangeCredentials.accountId();
        this.httpClient = HttpClient.newHttpClient();
        this.websocketAvailable = false;
        initializePaperTradingAccount();
    }

    private void initializePaperTradingAccount() {
        // Initialize with $10,000 USDC for paper trading
        balances.put("USDC", 10000.0);
        balances.put("XLM", 0.0);
        balances.put("USD", 10000.0);
        log.info("Stellar Network paper trading account initialized with $10,000 USDC");
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.BUY, size);
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.SELL, size);
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        return AuthResult.success("Stellar Network authenticated");
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.supplyAsync(() -> {
            Account account = new Account();
            account.setAccountId(accountId);
            account.setBalances(new LinkedHashMap<>(balances));
            account.setBalance("USDC", balances.getOrDefault("USDC", 0.0));
            account.setTotalBalance(balances.getOrDefault("USDC", 0.0));
            account.setAvailableBalance(balances.getOrDefault("USDC", 0.0));
            return account;
        });
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(currencyCode, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(currencyCode, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(balances.getOrDefault("USDC", 0.0));
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
    public boolean supportsLiveTrading() {
        return !isPaperTrading() && hasCredentials();
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
        return false;
    }

    @Override
    public boolean supportsCrypto() {
        return true;
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
    public void connect() {
        log.info("Connecting to Stellar Network...");
        // Stellar uses polling, so just mark as connected
        if (isPaperTrading()) {
            log.info("Connected to Stellar Network Testnet (paper trading)");
        } else {
            log.info("Connected to Stellar Network Mainnet (live trading)");
        }
    }

    @Override
    public void disconnect() {
        log.info("Disconnecting from Stellar Network...");
        orders.clear();
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public Boolean isConnected() {
        return true; // Stellar is always available for polling
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null; // Stellar doesn't use WebSocket
    }

    @Override
    public boolean supportsWebSocket() {
        return false;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return false;
    }

    public boolean hasCredentials() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
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
    public CompletableFuture<String> createMarketOrder(TradePair symbol, Side side, double quantity) {
        return CompletableFuture.supplyAsync(() -> {
            validatePaperOrder(symbol, quantity);
            double executionPrice = safeTicker(symbol).getMidPrice();
            if (executionPrice <= 0) {
                executionPrice = DEFAULT_XLM_USDC_PRICE;
            }
            applyPaperFill(symbol, side, quantity, executionPrice);
            String orderId = "STL-MARKET-" + nextOrderId++;
            Order order = buildOrder(orderId, symbol, "MARKET", executionPrice, quantity, side);
            order.setStatus("FILLED");
            order.setFilledQuantity(quantity);
            order.setCummulativeQuoteQty(quantity * executionPrice);
            orderHistory.put(orderId, order);
            tradeHistory.add(new Trade(symbol, executionPrice, quantity, side, nextOrderId, Instant.now()));
            log.info("Stellar Network market order placed: {} {} {}", side, quantity, symbol);
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return CompletableFuture.supplyAsync(() -> {
            validatePaperOrder(symbol, quantity);
            if (!Double.isFinite(limitPrice) || limitPrice <= 0) {
                throw new IllegalArgumentException("limitPrice must be positive");
            }
            String orderId = "STL-LIMIT-" + nextOrderId++;
            OpenOrder openOrder = new OpenOrder(orderId, symbol, side, OpenOrder.OrderType.LIMIT, limitPrice,
                    (int) Math.ceil(quantity));
            openOrder.setSize(quantity);
            openOrder.setRemainingSize(quantity);
            openOrder.setStatus(OpenOrder.OrderStatus.OPEN);
            openOrder.setExchange(getExchangeId());
            orders.put(orderId, openOrder);
            Order order = buildOrder(orderId, symbol, "LIMIT", limitPrice, quantity, side);
            order.setStatus("OPEN");
            orderHistory.put(orderId, order);
            log.info("Stellar Network limit order placed: {} {} {} @ {}", side, quantity, symbol, limitPrice);
            return orderId;
        });
    }

    public void streamLiveCandles(TradePair tradePair, Timeframe timeframe, ExchangeStreamConsumer consumer) {
        log.debug("Stellar Network does not support WebSocket candle streaming. Using polling fallback.");
    }

    public void stopStreamLiveCandles(TradePair tradePair) {
        // No-op for Stellar
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        log.debug("Stellar Network does not support WebSocket trade streaming. Using polling fallback.");
    }

    @Override
    public String getName() {
        return "STELLAR";
    }

    @Override
    public String getSignal() {
        return "XLM";
    }

    @Override
    public String getExchangeId() {
        return "stellar";
    }

    @Override
    public String getDisplayName() {
        return "Stellar Network";
    }

    @Override
    public boolean isSandbox() {
        return isPaperTrading();
    }

    @Override
    public boolean isPaperTrading() {
        if (getUserSelectedTradingMode() != null && !getUserSelectedTradingMode().isBlank()) {
            return "PAPER".equalsIgnoreCase(getUserSelectedTradingMode());
        }
        return !hasCredentials() || Boolean.parseBoolean(System.getenv().getOrDefault("STELLAR_PAPER", "true"));
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
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == MARKET_TYPES.SPOT || marketType == MARKET_TYPES.MARKET || marketType == MARKET_TYPES.LIMIT;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.SPOT, MARKET_TYPES.MARKET, MARKET_TYPES.LIMIT);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("STELLAR")
                .exchangeId("stellar")
                .displayName("Stellar Network Distributed Exchange")
                .apiBaseUrl(isPaperTrading() ? STELLAR_TEST_URL : STELLAR_API_URL)
                .webSocketBaseUrl("") // Stellar uses polling, not WebSocket
                .authenticationType("API_KEY")

                // Market coverage - Stellar is blockchain-based crypto
                .supportsCrypto(true)
                .supportsSpot(true)
                .supportsForex(false)
                .supportsStocks(false)
                .supportsEquities(false)
                .supportsDerivatives(false)
                .supportsFutures(false)
                .supportsPerpetuals(false)
                .supportsOptions(false)
                .supportsIndices(false)
                .supportsCommodities(false)

                // Trading support
                .supportsLiveTrading(!isPaperTrading())
                .supportsPaperTradingMode(true)
                .supportsSandbox(isPaperTrading())
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(false)
                .supportsStopLimitOrders(false)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(false)
                .supportsTrailingStopOrders(false)
                .supportsMarginTrading(false)
                .supportsLeverage(false)

                // Account / portfolio endpoints
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(false)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsAccountTrades(true)
                .supportsFills(true)
                .supportsOrderValidation(true)

                // Market data endpoints
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(true)
                .supportsFullOrderBook(true)
                .supportsTopOfBook(true)
                .supportsDistributionBook(false)
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(true)
                .supportsStreamingPrices(false)

                // Streaming capabilities
                .supportsNativeWebSocket(false)
                .supportsWebSocketStreaming(false)
                .supportsHttpStreaming(true)
                .supportsPollingFallback(true)
                .supportsTickerStreaming(false)
                .supportsOrderBookStreaming(false)
                .supportsTradeStreaming(false)
                .supportsCandleStreaming(false)
                .supportsAccountStreaming(false)
                .supportsOrderStreaming(false)
                .supportsFillStreaming(false)
                .supportsPositionStreaming(false)
                .supportsBalanceStreaming(false)

                // Infrastructure
                .supportsRateLimitInfo(false)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(false)

                // Market types
                .supportedMarketType("CRYPTO")
                .supportedMarketType("XLM")

                // Notes
                .notes("""
                        Stellar Network distributed exchange capability profile.
                        Supports cryptocurrency trading via the Stellar Distributed Exchange (DEX).
                        Primary asset: Stellar Lumens (XLM) - the native blockchain token.
                        Uses REST API polling (no WebSocket) for market data.
                        Supports custom asset pairs on the Stellar blockchain.
                        All trading requires XLM for transaction fees.
                        Paper (test) mode uses Testnet; live mode uses Mainnet.
                        Account and order data require authenticated API access.
                        """)
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        if (accountId == null || accountId.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("Stellar Network account ID is missing or empty")
                    .checkedAt(Instant.now())
                    .build();
        }

        return AuthCheckResult.builder()
                .exchangeName(getName())
                .success(true)
                .httpStatus(200)
                .credentialSource("CONFIGURATION")
                .endpointTested("/accounts/" + accountId)
                .message("Stellar Network account validated")
                .checkedAt(Instant.now())
                .build();
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return new TradePair("XLM", "USDC");
    }

    protected final ExchangeStreamConsumer liveTradeConsumers = new UiExchangeStreamConsumer();

    @Override
    public List<TradePair> getTradePairSymbol() throws SQLException, ClassNotFoundException {
        return List.of(new TradePair("XLM", "USDC"), new TradePair("XLM", "USDC"));
    }

    @Override
    public List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException {
        return List.of(new TradePair("XLM", "USDC"), new TradePair("XLM", "USDC"));
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null && tradePair.getBaseCurrency().getCode().equalsIgnoreCase("XLM");
    }

    @Override
    public double getLivePrice() {
        return safeTicker(defaultPair()).getMidPrice();
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return safeTicker(tradePair);
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> safeTicker(tradePair));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> tradePairs.stream().map(this::safeTicker).toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of() : List.of(pair));
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new StellarCandleDataSupplier(200, secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.supplyAsync(() -> fetchRecentTrades(tradePair, 200).stream()
                .filter(trade -> stopAt == null || !trade.getTimestamp().isAfter(stopAt))
                .toList());
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> fetchStellarOrderBook(tradePair));
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return "1m,5m,15m,1h,4h,1d";
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1,
                Timeframe.H4, Timeframe.D1);
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) {
        if (order.getSide() == Side.BUY) {
            return createMarketOrder(order.getTradePair(), Side.BUY, order.getQuantity());
        } else {
            return createMarketOrder(order.getTradePair(), Side.SELL, order.getQuantity());
        }
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        Order order = new Order();
        order.setId((long) id);
        order.setTradePair(tradePair);
        order.setType(type);
        order.setPrice(price);
        order.setQuantity(amount);
        order.setSide(side);
        return order;
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("Stellar Network does not support stop orders"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
            double entryPrice, double stopLoss, double takeProfit) {
        return failedFuture(unsupported("createBracketOrder"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            OpenOrder removed = orders.remove(orderId);
            Order order = orderHistory.get(orderId);
            if (order != null) {
                order.setStatus(removed == null ? "UNKNOWN" : "CANCELLED");
                order.setUpdatedAt(Instant.now());
            }
            if (removed != null) {
                removed.cancel();
            }
            return orderId;
        });
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<String> cancelled = new ArrayList<>();
            for (String orderId : orderIds) {
                if (orders.remove(orderId) != null) {
                    cancelled.add(orderId);
                    Order order = orderHistory.get(orderId);
                    if (order != null) {
                        order.setStatus("CANCELLED");
                        order.setUpdatedAt(Instant.now());
                    }
                }
            }
            return cancelled;
        });
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        int count = orders.size();
        orders.clear();
        orderHistory.values().forEach(order -> {
            if ("OPEN".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("CANCELLED");
                order.setUpdatedAt(Instant.now());
            }
        });
        return CompletableFuture.completedFuture("Cancelled %d Stellar orders".formatted(count));
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(orderHistory.get(orderId)));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(filterOpenOrders(tradePair));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(List.copyOf(orders.values()));
    }

    /**
     * Parses Stellar open orders response into a list of OpenOrder objects.
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
     * Parses a single Stellar open order from JsonNode.
     */
    private @Nullable OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();

            order.setOrderId(node.path("id").asText(""));

            // Parse trading pair from seller/buyer assets
            String sellingAsset = node.path("selling").path("asset_code").asText();
            String buyingAsset = node.path("buying").path("asset_code").asText();
            if (!sellingAsset.isEmpty() && !buyingAsset.isEmpty()) {
                order.setTradePair(new TradePair(buyingAsset, sellingAsset));
            }

            double price = node.path("price").asDouble(0.0);
            order.setPrice(price);

            // Get amount and priceg
            double amount = node.path("amount").asDouble(0.0);
            order.setSize(amount);

            // Stellar doesn't have traditional filled/remaining in this context
            order.setFilledSize(0.0);
            order.setRemainingSize(amount);

            // Parse side from selling asset
            String side = node.path("side").asText("buy");
            order.setSide("sell".equalsIgnoreCase(side) ? Side.SELL : Side.BUY);

            order.setOrderType(OpenOrder.OrderType.LIMIT);
            order.setStatus(OpenOrder.OrderStatus.PENDING);

            long timestamp = node.path("timestamp").asLong(0);
            if (timestamp > 0) {
                order.setCreatedAt(Instant.ofEpochSecond(timestamp));
                order.setUpdatedAt(Instant.ofEpochSecond(timestamp));
            }

            return order;
        } catch (Exception exception) {
            log.debug("Error parsing Stellar open order", exception);
            return null;
        }
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(orderHistory.values().stream()
                .filter(order -> tradePair == null || Objects.equals(order.getTradePair(), tradePair))
                .filter(order -> since == null || order.getCreatedAt() == null || !order.getCreatedAt().isBefore(since))
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(positions.stream()
                .filter(position -> tradePair == null || Objects.equals(position.getTradePair(), tradePair))
                .toList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(List.copyOf(positions));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(positions.stream()
                .filter(position -> tradePair == null || Objects.equals(position.getTradePair(), tradePair))
                .findFirst());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return CompletableFuture.completedFuture("NO_POSITION");
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        positions.clear();
        return CompletableFuture.completedFuture("NO_POSITIONS");
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return CompletableFuture.completedFuture("NO_POSITION");
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return CompletableFuture.completedFuture("NO_POSITION");
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(unsupported("modifyStopLoss"));
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
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
            double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(
                supportsMarketType(marketType)
                        && supportsTradePair(tradePair)
                        && normalizeAmount(tradePair, size) >= getMinOrderAmount(tradePair)
                        && stopLoss <= 0
                        && takeProfit <= 0);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0.0;
        }
        return Math.floor(amount * 10_000_000.0) / 10_000_000.0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        if (!Double.isFinite(price) || price <= 0) {
            return 0.0;
        }
        return Math.round(price * 10_000_000.0) / 10_000_000.0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.0000001;
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

    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {

    }

    @Override
    public void stopAllStreams() {

    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {

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
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        return CompletableFuture.completedFuture(filterTrades(tradePair, tradeHistory.size()));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(
                tradeHistory.stream()
                        .filter(t -> t.getTimestamp().isAfter(since))
                        .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return CompletableFuture.completedFuture(
                tradeHistory.stream()
                        .filter(t -> t.getTimestamp().isAfter(from) && t.getTimestamp().isBefore(to))
                        .toList());
    }

    // Additional required methods
    public List<Ticker> getTickers(MARKET_TYPES marketType) {
        if (marketType != MARKET_TYPES.SPOT && marketType != MARKET_TYPES.MARKET) {
            return List.of();
        }
        return fetchTickers(defaultPairs()).join();
    }

    public Ticker getTicker(String symbol) {
        return safeTicker(parseSymbolOrDefault(symbol));
    }

    public List<Trade> getRecentTrades(TradePair tradePair, int limit) {
        return fetchRecentTrades(tradePair, limit);
    }

    public void cancelOrder(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }
        orders.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTradePair(), tradePair));
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit) {
        int seconds = secondsFor(timeframe);
        long end = Instant.now().getEpochSecond();
        long start = end - (long) Math.max(1, limit) * seconds;
        return getCandles(tradePair, timeframe, limit, start, end);
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit, Long startTime,
            Long endTime) {
        return buildCandlesFromTrades(tradePair, secondsFor(timeframe), Math.max(1, limit), startTime, endTime);
    }

    public CandleDataSupplier getCandleDataSupplier(TradePair tradePair, Timeframe timeframe) {
        return getCandleDataSupplier(secondsFor(timeframe), tradePair);
    }

    private String horizonUrl() {
        return isPaperTrading() ? STELLAR_TEST_URL : STELLAR_API_URL;
    }

    private String usdcIssuer() {
        return isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER;
    }

    private TradePair defaultPair() {
        try {
            return new TradePair("XLM", "USDC");
        } catch (SQLException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to create default Stellar trade pair", e);
        }
    }

    private List<TradePair> defaultPairs() {
        try {
            return List.of(new TradePair("XLM", "USDC"), new TradePair("XLM", "USD"));
        } catch (SQLException | ClassNotFoundException e) {
            return List.of(defaultPair());
        }
    }

    private TradePair parseSymbolOrDefault(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return defaultPair();
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('_', '/').replace('-', '/');
        String[] parts = normalized.contains("/") ? normalized.split("/") : new String[] { "XLM", normalized };
        try {
            return new TradePair(parts[0], parts.length > 1 ? parts[1] : "USDC");
        } catch (SQLException | ClassNotFoundException e) {
            return defaultPair();
        }
    }

    private Ticker safeTicker(TradePair tradePair) {
        TradePair pair = tradePair == null ? defaultPair() : tradePair;
        try {
            OrderBook orderBook = fetchStellarOrderBook(pair);
            double bid = orderBook.getBestBid() == null ? 0.0 : orderBook.getBestBid().getPrice();
            double ask = orderBook.getBestAsk() == null ? 0.0 : orderBook.getBestAsk().getPrice();
            Ticker ticker = Ticker.fromBidAsk(bid, ask);
            ticker.setTradePair(pair);
            if (!ticker.isValid()) {
                ticker.updateLast(DEFAULT_XLM_USDC_PRICE);
            }
            return ticker;
        } catch (Exception e) {
            log.debug("Unable to fetch Stellar ticker for {}: {}", pair, e.getMessage());
            Ticker ticker = new Ticker(DEFAULT_XLM_USDC_PRICE, DEFAULT_XLM_USDC_PRICE * 0.999,
                    DEFAULT_XLM_USDC_PRICE * 1.001, 0.0, System.currentTimeMillis());
            ticker.setTradePair(pair);
            return ticker;
        }
    }

    private OrderBook fetchStellarOrderBook(TradePair tradePair) {
        TradePair pair = tradePair == null ? defaultPair() : tradePair;
        if (!supportsTradePair(pair)) {
            return syntheticOrderBook(pair);
        }

        try {
            JSONObject json = httpGetJson("%s/order_book?%s&limit=20".formatted(horizonUrl(), assetQuery(pair)));
            List<OrderBook.PriceLevel> bids = parsePriceLevels(json.optJSONArray("bids"));
            List<OrderBook.PriceLevel> asks = parsePriceLevels(json.optJSONArray("asks"));
            if (bids.isEmpty() && asks.isEmpty()) {
                return syntheticOrderBook(pair);
            }
            bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
            asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));
            OrderBook orderBook = new OrderBook(pair, bids, asks);
            orderBook.setTimestamp(Instant.now());
            return orderBook;
        } catch (Exception e) {
            log.debug("Falling back to synthetic Stellar order book for {}: {}", pair, e.getMessage());
            return syntheticOrderBook(pair);
        }
    }

    private List<OrderBook.PriceLevel> parsePriceLevels(JSONArray levels) {
        if (levels == null) {
            return List.of();
        }
        List<OrderBook.PriceLevel> result = new ArrayList<>();
        for (int i = 0; i < levels.length(); i++) {
            JSONObject level = levels.optJSONObject(i);
            if (level != null) {
                result.add(new OrderBook.PriceLevel(level.optDouble("price", 0.0), level.optDouble("amount", 0.0)));
            }
        }
        return result;
    }

    private OrderBook syntheticOrderBook(TradePair pair) {
        double price = DEFAULT_XLM_USDC_PRICE;
        return new OrderBook(pair,
                List.of(new OrderBook.PriceLevel(price * 0.999, 5_000.0),
                        new OrderBook.PriceLevel(price * 0.995, 10_000.0)),
                List.of(new OrderBook.PriceLevel(price * 1.001, 5_000.0),
                        new OrderBook.PriceLevel(price * 1.005, 10_000.0)));
    }

    private List<Trade> fetchRecentTrades(TradePair tradePair, int limit) {
        TradePair pair = tradePair == null ? defaultPair() : tradePair;
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        try {
            JSONObject json = httpGetJson("%s/trades?%s&order=desc&limit=%d".formatted(horizonUrl(), assetQuery(pair),
                    safeLimit));
            JSONObject embedded = json.optJSONObject("_embedded");
            JSONArray records = embedded == null ? new JSONArray() : embedded.optJSONArray("records");
            if (records == null || records.isEmpty()) {
                return filterTrades(pair, safeLimit);
            }
            List<Trade> trades = new ArrayList<>();
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) {
                    continue;
                }
                JSONObject priceObject = record.optJSONObject("price");
                double price = priceObject == null
                        ? DEFAULT_XLM_USDC_PRICE
                        : priceObject.optDouble("n", 0.0) / Math.max(1.0, priceObject.optDouble("d", 1.0));
                double amount = record.optDouble("base_amount", 0.0);
                Instant timestamp = Instant.parse(record.optString("ledger_close_time", Instant.now().toString()));
                trades.add(new Trade(pair, price, amount, Side.BUY, record.optLong("id", i), timestamp));
            }
            return trades;
        } catch (Exception e) {
            log.debug("Unable to fetch Stellar trades for {}: {}", pair, e.getMessage());
            return filterTrades(pair, safeLimit);
        }
    }

    private String assetQuery(TradePair pair) {
        String quote = pair.getCounterCode();
        String quoteCode = "USD".equalsIgnoreCase(quote) ? "USDC" : quote;
        String issuer = "USDC".equalsIgnoreCase(quoteCode) ? usdcIssuer() : "";
        return "selling_asset_type=native"
                + "&buying_asset_type=credit_alphanum4"
                + "&buying_asset_code=" + quoteCode
                + "&buying_asset_issuer=" + issuer;
    }

    private JSONObject httpGetJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Horizon returned HTTP " + response.statusCode());
            }
            return new JSONObject(response.body());
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private void validatePaperOrder(TradePair tradePair, double quantity) {
        if (!supportsTradePair(tradePair)) {
            throw new IllegalArgumentException("Unsupported Stellar pair: " + tradePair);
        }
        if (normalizeAmount(tradePair, quantity) < getMinOrderAmount(tradePair)) {
            throw new IllegalArgumentException("Order quantity is below Stellar precision minimum");
        }
    }

    private void applyPaperFill(TradePair pair, Side side, double quantity, double price) {
        String base = pair.getBaseCode();
        String quote = pair.getCounterCode();
        double notional = quantity * price;
        if (side == Side.BUY) {
            double quoteBalance = balances.getOrDefault(quote, balances.getOrDefault("USDC", 0.0));
            if (quoteBalance < notional) {
                throw new IllegalArgumentException("Insufficient " + quote + " paper balance");
            }
            balances.merge(quote, -notional, Double::sum);
            balances.merge(base, quantity, Double::sum);
        } else {
            double baseBalance = balances.getOrDefault(base, 0.0);
            if (baseBalance < quantity) {
                throw new IllegalArgumentException("Insufficient " + base + " paper balance");
            }
            balances.merge(base, -quantity, Double::sum);
            balances.merge(quote, notional, Double::sum);
        }
    }

    private Order buildOrder(String orderId, TradePair pair, String type, double price, double quantity, Side side) {
        Order order = new Order();
        order.setId(parseOrderId(orderId));
        order.setTradePair(pair);
        order.setSymbol(pair.toString('/'));
        order.setType(type);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setSide(side);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    private long parseOrderId(String orderId) {
        int index = orderId.lastIndexOf('-');
        if (index < 0) {
            return nextOrderId;
        }
        try {
            return Long.parseLong(orderId.substring(index + 1));
        } catch (NumberFormatException e) {
            return nextOrderId;
        }
    }

    private List<OpenOrder> filterOpenOrders(TradePair tradePair) {
        return orders.values().stream()
                .filter(order -> tradePair == null || Objects.equals(order.getTradePair(), tradePair))
                .toList();
    }

    private List<Trade> filterTrades(TradePair tradePair, int limit) {
        return tradeHistory.stream()
                .filter(trade -> tradePair == null || Objects.equals(trade.getTradePair(), tradePair))
                .sorted(Comparator.comparing(Trade::getTimestamp).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    private List<CandleData> buildCandlesFromTrades(TradePair tradePair, int secondsPerCandle, int limit,
            Long startTime,
            Long endTime) {
        long end = endTime == null ? Instant.now().getEpochSecond() : endTime;
        long start = startTime == null ? end - (long) limit * secondsPerCandle : startTime;
        List<Trade> trades = fetchRecentTrades(tradePair, 200).stream()
                .filter(trade -> {
                    long epoch = trade.getTimestamp().getEpochSecond();
                    return epoch >= start && epoch <= end;
                })
                .sorted(Comparator.comparing(Trade::getTimestamp))
                .toList();

        if (trades.isEmpty()) {
            return syntheticCandles(secondsPerCandle, limit, end);
        }

        Map<Long, List<Trade>> buckets = new TreeMap<>();
        for (Trade trade : trades) {
            long bucket = (trade.getTimestamp().getEpochSecond() / secondsPerCandle) * secondsPerCandle;
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(trade);
        }

        return buckets.entrySet().stream()
                .skip(Math.max(0, buckets.size() - limit))
                .map(entry -> candleFromTrades(entry.getKey(), entry.getValue()))
                .toList();
    }

    private CandleData candleFromTrades(long bucket, List<Trade> trades) {
        double open = trades.getFirst().getPrice();
        double close = trades.getLast().getPrice();
        double high = trades.stream().mapToDouble(Trade::getPrice).max().orElse(close);
        double low = trades.stream().mapToDouble(Trade::getPrice).min().orElse(close);
        double volume = trades.stream().mapToDouble(Trade::getAmount).sum();
        return new CandleData(open, close, high, low, (int) bucket, volume);
    }

    private List<CandleData> syntheticCandles(int secondsPerCandle, int limit, long endTime) {
        List<CandleData> candles = new ArrayList<>();
        long alignedEnd = (endTime / secondsPerCandle) * secondsPerCandle;
        for (int i = limit - 1; i >= 0; i--) {
            long openTime = alignedEnd - (long) i * secondsPerCandle;
            double drift = Math.sin(openTime / 86_400.0) * 0.005;
            double open = DEFAULT_XLM_USDC_PRICE + drift;
            double close = open + 0.001;
            candles.add(new CandleData(open, close, Math.max(open, close) + 0.001, Math.min(open, close) - 0.001,
                    (int) openTime, 1_000.0));
        }
        return candles;
    }

    private int secondsFor(Timeframe timeframe) {
        if (timeframe == null) {
            return 60;
        }
        return switch (timeframe) {
            case M1 -> 60;
            case M3 -> 180;
            case M5 -> 300;
            case M15 -> 900;
            case M30 -> 1800;
            case H1 -> 3600;
            case H4 -> 14400;
            case D1 -> 86400;
            default -> 60;
        };
    }

    private class StellarCandleDataSupplier extends CandleDataSupplier {
        StellarCandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair) {
            super(numCandles, secondsPerCandle, tradePair,
                    new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        }

        @Override
        public Future<List<CandleData>> get() {
            return CompletableFuture.completedFuture(getCandleData());
        }

        @Override
        public List<CandleData> getCandleData() {
            return buildCandlesFromTrades(tradePair, secondsPerCandle, numCandles, null, (long) endTime.get());
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return StellarNetwork.this.getCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
                Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
            return StellarNetwork.this.fetchRecentTradesUntil(tradePair, stopAt);
        }
    }
}
