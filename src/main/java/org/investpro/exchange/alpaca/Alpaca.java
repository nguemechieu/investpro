package org.investpro.exchange.alpaca;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.AlpacaWebSocket;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.trading.*;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Getter
@Setter
@Slf4j
public class Alpaca extends Exchange {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String ALPACA_LIVE_URL = "https://api.alpaca.markets";

    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;
    private AlpacaWebSocket alpacaWebSocket;
    private ExchangeCredentials exchangeCredentials;
    private volatile boolean connected;
    private volatile JsonNode accountPermissions;
    private volatile long accountPermissionsExpires;
    private Map<String, JsonNode> assetMetadata = Map.of();
    private long assetMetadataExpires;
    private final org.investpro.exchange.infrastructure.PollingExchangeStreamer polling =
            new org.investpro.exchange.infrastructure.PollingExchangeStreamer(this);

    protected HttpResponse<String> executeHttpRequest(HttpRequest request) throws java.io.IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class AlpacaHttpException extends IllegalStateException {
        private final int status;
        AlpacaHttpException(int status) {
            super("Alpaca HTTP " + status + (status == 401 ? ": verify live API key and secret" :
                    status == 403 ? ": account or market-data permission required" :
                    status == 429 ? ": rate limit reached; retry later" : ""));
            this.status = status;
        }
    }

    private JsonNode readJson(String path) {
        try {
            HttpResponse<String> response = executeHttpRequest(alpacaRequest(path).GET().build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 401) { connected = false; accountPermissions = null; }
                throw new AlpacaHttpException(response.statusCode());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Alpaca request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Alpaca request failed", exception);
        }
    }

    private synchronized Map<String, JsonNode> loadAssets() {
        if (System.currentTimeMillis() < assetMetadataExpires) return assetMetadata;
        JsonNode assets = readJson("/v2/assets?status=active&asset_class=us_equity");
        if (!assets.isArray()) throw new IllegalStateException("Alpaca returned an invalid asset list");
        Map<String, JsonNode> next = new LinkedHashMap<>();
        for (JsonNode asset : assets) {
            String symbol = asset.path("symbol").asText("");
            if (!symbol.isBlank()) next.put(symbol, asset);
        }
        assetMetadata = Map.copyOf(next);
        assetMetadataExpires = System.currentTimeMillis() + 300_000;
        return assetMetadata;
    }

    private synchronized JsonNode loadAccountPermissions() {
        if (accountPermissions == null || System.currentTimeMillis() >= accountPermissionsExpires) {
            accountPermissions = readJson("/v2/account");
            accountPermissionsExpires = System.currentTimeMillis() + 30_000;
        }
        return accountPermissions;
    }

    static boolean accountCanTrade(JsonNode account) {
        return account != null && "ACTIVE".equalsIgnoreCase(account.path("status").asText(""))
                && !account.path("trading_blocked").asBoolean(false)
                && !account.path("account_blocked").asBoolean(false)
                && !account.path("trade_suspended_by_user").asBoolean(false);
    }

    public Alpaca(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        this.exchangeCredentials = exchangeCredentials;
        initializePaperTradingAccount();

        try {
            this.alpacaWebSocket = createWebSocketClient();
        } catch (Exception ex) {
            log.error("Failed to initialize Alpaca websocket client", ex);
        }

    }

    private AlpacaWebSocket createWebSocketClient() {

        return new AlpacaWebSocket(URI.create("wss://stream.data.alpaca.markets/v2/iex"), new Draft_6455());
    }

    private void initializePaperTradingAccount() {
        // Initialize with $25,000 USD for paper trading (Alpaca requirement)
        balances.put("USD", 25000.0);
    }

    @Override
    public String getName() {
        return "ALPACA";
    }

    @Override
    public String getSignal() {
        return "";
    }

    @Override
    public String getExchangeId() {
        return "alpaca";
    }

    @Override
    public String getDisplayName() {
        return "Alpaca";
    }

    @Override
    public boolean isSandbox() {
        return false;
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsLocalPaper()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return !hasCredentials() || Boolean.parseBoolean(System.getenv().getOrDefault("ALPACA_PAPER", "false"));
    }

    @Override
    public String getTimestamp() {
        return "";
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == MARKET_TYPES.STOCKS;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("ALPACA")
                .exchangeId("alpaca")
                .displayName("Alpaca Trading")
                .apiBaseUrl(ALPACA_LIVE_URL)

                // Market coverage - Alpaca specializes in US equities/stocks
                .supportsCrypto(false)
                .supportsSpot(true)
                .supportsStocks(true)
                .supportsEquities(true)
                .supportsFutures(false)
                .supportsDerivatives(false)
                .supportsForex(false)
                .supportsOptions(false)
                .supportsIndices(false)
                .supportsCommodities(false)

                // Trading support
                .supportsLiveTrading(!isPaperTrading())
                .supportsPaperTradingMode(true)
                .supportsSandbox(false)
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(false)
                .supportsStopLimitOrders(false)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(false)
                .supportsTrailingStopOrders(true)
                .supportsMarginTrading(true)
                .supportsLeverage(false)

                // Account / portfolio
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(true)
                .supportsAccountTrades(false)
                .supportsOpenOrders(true)
                .supportsOrderHistory(false)
                .supportsFills(false)
                .supportsOrderValidation(false)

                // Market data
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(false)
                // Historical bars use the configured IEX/SIP feed.
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(false)
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)

                // Streaming
                .supportsWebSocket(false)
                .supportsWebSocketStreaming(false)
                .supportsTickerStreaming(true)
                .supportsTradeStreaming(false)
                .supportsCandleStreaming(true)
                .supportsOrderBookStreaming(false)
                .supportsAccountStreaming(true)
                .supportsOrderStreaming(true)
                .supportsFillStreaming(false)
                .supportsPositionStreaming(true)
                .supportsBalanceStreaming(true)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)

                // Infrastructure / limits
                .supportsRateLimitInfo(true)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(true)

                // Notes
                .notes("""
                        Alpaca Trading capability profile.
                        Specializes in US stocks and equities trading.
                        Supports local paper simulation and live trading.
                        Account restrictions and market-data subscriptions are enforced by Alpaca.
                        Market data and trading require authenticated access.
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
                    .message("Alpaca credentials are not configured")
                    .checkedAt(Instant.now())
                    .build();
        }

        try {
            accountPermissions = readJson("/v2/account");
            connected = true;
        } catch (AlpacaHttpException exception) {
            connected = false;
            accountPermissions = null;
            return AuthCheckResult.builder().exchangeName(getName()).success(false)
                    .httpStatus(exception.status).credentialIssue(exception.status == 401 || exception.status == 403)
                    .endpointTested("/v2/account").message(exception.getMessage()).checkedAt(Instant.now()).build();
        } catch (RuntimeException exception) {
            connected = false;
            accountPermissions = null;
            return AuthCheckResult.builder().exchangeName(getName()).success(false).credentialIssue(false)
                    .endpointTested("/v2/account").message("Unable to reach Alpaca account endpoint")
                    .checkedAt(Instant.now()).build();
        }
        return AuthCheckResult.builder()
                .exchangeName(getName())
                .success(true)
                .httpStatus(200)
                .credentialSource("CONFIGURATION")
                .endpointTested("/v2/account")
                .message("Alpaca API credentials validated")
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
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        java.util.Objects.requireNonNull(order, "order");
        try {
            String symbol = order.getSymbol();
            TradePair pair = new TradePair(symbol.replaceFirst("[/_-]USD$", ""), "USD");
            String type = order.getType() == null ? "market" : order.getType().toLowerCase(java.util.Locale.ROOT);
            return switch (type) {
                case "market" -> createMarketOrder(pair, order.getSide(), order.getQuantity());
                case "limit" -> createLimitOrder(pair, order.getSide(), order.getQuantity(), order.getPrice());
                default -> failedFuture(unsupported("order type " + type));
            };
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    // --------- Capability Methods ---------

    @Override
    public boolean supportsLiveTrading() {
        return hasCredentials() && !isPaperTrading();
    }

    @Override
    public boolean canSubmitLiveOrders() {
        return connected && super.canSubmitLiveOrders() && accountCanTrade(accountPermissions);
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
    public boolean supportsTrailingStopOrders() {
        return true;
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
        // Alpaca uses polling for streaming
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
                streamCandles(pair, subscription.getSecondsPerCandle(), consumer);
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
        polling.stopAll();
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        polling.streamTicker(tradePair, consumer);
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        // Not supported
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        polling.streamCandles(tradePair, secondsPerCandle, consumer);
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        polling.streamAccount(consumer);
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        polling.streamBalances(consumer);
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        polling.streamOrders(consumer);
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        polling.streamPositions(consumer);
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        polling.stopTicker(tradePair);
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
        // Stop polling
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        // Not applicable
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        polling.stopCandles(tradePair, secondsPerCandle);
    }

    @Override
    public void stopAccountStream() {
        polling.stopAccount();
    }

    @Override
    public void stopBalancesStream() {
        polling.stopAccount();
    }

    @Override
    public void stopOrdersStream() {
        polling.stopOrders();
    }

    @Override
    public void stopFillsStream() {
        // Stop polling
    }

    @Override
    public void stopPositionsStream() {
        polling.stopPositions();
    }

    // --------- Streaming Capability Methods ---------

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
        return true;
    }

    @Override
    public boolean supportsTradeStreaming() {
        return false;
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
        return false;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return true;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return true;
    }
    // --------- Order Creation Methods ---------

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (tradePair == null || side == null || amount <= 0 || !Double.isFinite(amount)) {
            return failedFuture(new IllegalArgumentException("A pair, side and positive finite quantity are required"));
        }
        if (!isPaperTrading() && !hasCredentials()) return failedFuture(new IllegalStateException("Live Alpaca credentials are required"));
        if (!isPaperTrading() && hasCredentials()) {
            return submitAlpacaOrder(tradePair, side, amount, 0.0, "market");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            double fillPrice = 150.0;
            if (side == Side.BUY) {
                double cost = amount * fillPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient funds");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient shares");
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
            return orderId;
        });
    }

    private boolean hasCredentials() {
        return exchangeCredentials != null
                && exchangeCredentials.apiKey() != null
                && !exchangeCredentials.apiKey().isBlank()
                && exchangeCredentials.apiSecret() != null
                && !exchangeCredentials.apiSecret().isBlank();
    }

    @Override
    public CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice) {
        if (tradePair == null || side == null || amount <= 0 || !Double.isFinite(amount)
                || limitPrice <= 0 || !Double.isFinite(limitPrice)) {
            return failedFuture(new IllegalArgumentException("A pair, side, positive finite quantity and limit price are required"));
        }
        if (!isPaperTrading() && !hasCredentials()) return failedFuture(new IllegalStateException("Live Alpaca credentials are required"));
        if (!isPaperTrading() && hasCredentials()) {
            return submitAlpacaOrder(tradePair, side, amount, limitPrice, "limit");
        }
        return CompletableFuture.supplyAsync(() -> {
            String orderId = "ORDER-" + (nextOrderId++) + "-" + System.currentTimeMillis();
            if (side == Side.BUY) {
                double cost = amount * limitPrice;
                Double balance = balances.getOrDefault("USD", 0.0);
                if (balance < cost) {
                    throw new RuntimeException("Insufficient funds");
                }
                balances.put("USD", balance - cost);
                balances.put(tradePair.getBaseCode(),
                        balances.getOrDefault(tradePair.getBaseCode(), 0.0) + amount);
            } else {
                Double baseBalance = balances.getOrDefault(tradePair.getBaseCode(), 0.0);
                if (baseBalance < amount) {
                    throw new RuntimeException("Insufficient shares");
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
        return CompletableFuture.supplyAsync(() -> {
            if (isPaperTrading()) { orders.remove(orderId); return orderId; }
            sendMutation("/v2/orders/" + java.net.URLEncoder.encode(orderId, java.nio.charset.StandardCharsets.UTF_8), "DELETE");
            return orderId;
        });
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        var futures = orderIds.stream().map(this::cancelOrder).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return fetchAllOpenOrders().thenCompose(open -> cancelOrders(open.stream().map(OpenOrder::getOrderId).toList()))
                .thenApply(ids -> "Cancelled " + ids.size() + " orders");
    }

    // --------- Order Query Methods ---------

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return failedFuture(unsupported("fetchOrder"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        if (isPaperTrading()) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> parseOpenOrders(readJson("/v2/orders?status=open&limit=500"))
                .stream().filter(order -> tradePair == null || alpacaSymbol(tradePair).equals(order.getTradePair().getBaseCode())).toList());
    }

    /**
     * Parses Alpaca open orders response into a list of OpenOrder objects.
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
     * Parses a single Alpaca open order from JsonNode.
     */
    private OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();

            order.setOrderId(node.path("id").asText(""));

            String symbol = node.path("symbol").asText();
            if (!symbol.isEmpty()) {
                order.setTradePair(new TradePair(symbol, "USD"));
            }

            String side = node.path("side").asText("buy");
            order.setSide("sell".equalsIgnoreCase(side) ? Side.SELL : Side.BUY);

            order.setPrice(parseDouble(node.path("limit_price").asText("0"), 0.0));
            order.setSize(parseDouble(node.path("qty").asText("0"), 0.0));
            order.setFilledSize(parseDouble(node.path("filled_qty").asText("0"), 0.0));
            order.setRemainingSize(Math.max(0.0, order.getSize() - order.getFilledSize()));

            String status = node.path("status").asText("PENDING");
            try {
                order.setStatus(OpenOrder.OrderStatus.valueOf(status.toUpperCase()));
            } catch (Exception e) {
                order.setStatus(OpenOrder.OrderStatus.PENDING);
            }

            String type = node.path("type").asText("limit");
            try {
                order.setOrderType(OpenOrder.OrderType.valueOf(type.toUpperCase()));
            } catch (Exception e) {
                order.setOrderType(OpenOrder.OrderType.LIMIT);
            }

            String createdAt = node.path("created_at").asText("");
            if (!createdAt.isEmpty()) {
                try {
                    order.setCreatedAt(Instant.parse(createdAt));
                } catch (Exception e) {
                    // Keep default
                }
            }

            String updatedAt = node.path("updated_at").asText(createdAt);
            if (!updatedAt.isEmpty()) {
                try {
                    order.setUpdatedAt(Instant.parse(updatedAt));
                } catch (Exception e) {
                    // Keep default
                }
            }

            return order;
        } catch (Exception exception) {
            log.debug("Error parsing Alpaca open order", exception);
            return null;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return fetchOpenOrders(null).thenApply(open -> {
            if (open.size() >= 500) throw new IllegalStateException("Alpaca open-order limit reached; review all orders in the broker dashboard before bulk cancellation");
            return open;
        });
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return failedFuture(unsupported("fetchOrderHistory"));
    }

    // --------- Position Methods ---------

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return fetchAllPositions().thenApply(all -> all.stream()
                .filter(position -> tradePair == null || position.getTradePair().equals(tradePair)).toList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        if (isPaperTrading()) return CompletableFuture.completedFuture(List.copyOf(positions));
        return CompletableFuture.supplyAsync(() -> {
            List<Position> result = new ArrayList<>();
            JsonNode response = readJson("/v2/positions");
            if (!response.isArray()) throw new IllegalStateException("Invalid Alpaca positions response");
            for (JsonNode node : response) {
                try {
                    Position position = new Position(new TradePair(node.path("symbol").asText(), "USD"),
                            "short".equals(node.path("side").asText()) ? Side.SELL : Side.BUY,
                            Math.abs(node.path("qty").asDouble()), node.path("avg_entry_price").asDouble());
                    position.setPositionId(node.path("asset_id").asText());
                    position.setCurrentPrice(node.path("current_price").asDouble());
                    position.setUnrealizedPnl(node.path("unrealized_pl").asDouble());
                    result.add(position);
                } catch (SQLException | ClassNotFoundException exception) {
                    throw new IllegalStateException("Unable to map Alpaca position", exception);
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return fetchPositions(tradePair).thenApply(all -> all.stream().findFirst());
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
        if (!isPaperTrading()) return failedFuture(unsupported("live fill history"));
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
        if (!isPaperTrading()) return failedFuture(unsupported("live fill history"));
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
        if (!isPaperTrading()) return failedFuture(unsupported("live fill history"));
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
        // Not implemented for Alpaca yet
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
        // Not implemented for Alpaca yet
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        AuthCheckResult result = checkAuthentication();
        return result.isSuccess() ? AuthResult.success(result.getMessage()) : AuthResult.failure(result.getMessage());
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
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return null;
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        var assets = loadAssets().values();
        List<TradePair> pairs = new ArrayList<>();
        for (JsonNode asset : assets) {
            String symbol = asset.path("symbol").asText("");
            if (symbol.isBlank()) continue;
            try {
                TradePair pair = new TradePair(symbol, "USD");
                pair.setNativeSymbol(symbol);
                pair.setExchangeId(getExchangeId());
                pairs.add(pair);
            } catch (SQLException | ClassNotFoundException exception) {
                throw new IllegalStateException("Unable to create Alpaca instrument " + symbol, exception);
            }
        }
        return pairs;
    }

    @Override
    public List<TradePair> getTradablePairs() {
        List<TradePair> pairs = getTradePairSymbol();
        return fetchTradabilityStatus(pairs).join().stream().filter(SymbolTradability::isFullyTradable)
                .map(SymbolTradability::tradePair).toList();
    }

    @Override
    public CompletableFuture<List<SymbolTradability>> fetchTradabilityStatus(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> pairs.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::mapAlpacaTradability)
                .toList());
    }

    @Override
    public CompletableFuture<SymbolTradability> fetchTradabilityStatus(TradePair pair) {
        if (pair == null) {
            return CompletableFuture
                    .completedFuture(defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }
        return CompletableFuture.supplyAsync(() -> mapAlpacaTradability(pair));
    }

    private SymbolTradability mapAlpacaTradability(TradePair pair) {
        if (!supportsTradePair(pair)) return defaultTradability(pair, TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE,
                "This Alpaca adapter supports US equities quoted in USD");
        String assetSymbol = alpacaAssetSymbol(pair);
        try {
            JsonNode body = loadAssets().get(assetSymbol);
            if (body == null) return defaultTradability(pair, TradabilityStatus.INACTIVE, "Alpaca asset is not listed: " + assetSymbol);
            JsonNode account = loadAccountPermissions();
            boolean tradable = body.path("tradable").asBoolean(false);
            boolean active = "active".equalsIgnoreCase(body.path("status").asText(""));
            boolean marginable = body.path("marginable").asBoolean(false);
            boolean shortable = body.path("shortable").asBoolean(false);
            boolean fractionable = body.path("fractionable").asBoolean(false);

            TradabilityStatus status = tradable && active
                    ? TradabilityStatus.FULLY_TRADABLE
                    : (active ? TradabilityStatus.DISABLED : TradabilityStatus.INACTIVE);

            boolean orderSubmissionAllowed = status == TradabilityStatus.FULLY_TRADABLE && canSubmitLiveOrders() && accountCanTrade(account);
            if (!accountCanTrade(account) && status == TradabilityStatus.FULLY_TRADABLE) {
                status = TradabilityStatus.PERMISSION_DENIED;
            }

            String reason = status == TradabilityStatus.PERMISSION_DENIED
                    ? "Alpaca account is not active or trading is blocked/suspended; review your account restrictions"
                    : status == TradabilityStatus.FULLY_TRADABLE
                    ? (orderSubmissionAllowed ? "Alpaca asset is tradable" : "Alpaca asset is available; connect your live account to submit orders")
                    : "Alpaca asset is restricted: status=%s, tradable=%s"
                            .formatted(body.path("status").asText("unknown"), tradable);

            return new SymbolTradability(
                    getExchangeId(),
                    pair,
                    body.path("symbol").asText(assetSymbol),
                    status,
                    true,
                    true,
                    true,
                    true,
                    orderSubmissionAllowed,
                    orderSubmissionAllowed,
                    orderSubmissionAllowed,
                    true,
                    true,
                    false,
                    shortable && account.path("shorting_enabled").asBoolean(false),
                    marginable,
                    marginable && account.path("multiplier").asDouble(1) > 1,
                    reason,
                    Instant.now(),
                    Map.of(
                            "assetStatus", body.path("status").asText("unknown"),
                            "tradable", tradable,
                            "fractionable", fractionable,
                            "marginable", marginable,
                            "shortable", shortable));
        } catch (Exception exception) {
            log.debug("Unable to load Alpaca asset tradability for {}", assetSymbol, exception);
            return defaultTradability(pair, TradabilityStatus.UNKNOWN,
                    "Unable to verify Alpaca asset tradability for " + assetSymbol);
        }
    }

    private String alpacaAssetSymbol(TradePair pair) {
        if (pair == null || pair.getBaseCode() == null) {
            return "";
        }
        return pair.getBaseCode().trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null && "USD".equalsIgnoreCase(tradePair.getCounterCode());
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return fetchTicker(tradePair).join();
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode snapshot = readData("/v2/stocks/" + alpacaSymbol(tradePair) + "/snapshot?feed=" + dataFeed());
            JsonNode quote = snapshot.path("latestQuote");
            JsonNode trade = snapshot.path("latestTrade");
            JsonNode day = snapshot.path("dailyBar");
            return new Ticker(trade.path("p").asDouble(), quote.path("bp").asDouble(), quote.path("ap").asDouble(),
                    day.path("o").asDouble(), day.path("h").asDouble(), day.path("l").asDouble(),
                    day.path("v").asDouble(), Instant.parse(trade.path("t").asText(Instant.now().toString())).toEpochMilli());
        });
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        var futures = tradePairs.stream().map(this::fetchTicker).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTicker(pair).thenApply(List::of);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new AlpacaCandleDataSupplier(this, secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode bars = readData("/v2/stocks/" + alpacaSymbol(tradePair) + "/bars?timeframe="
                    + supportsTimeframe(secondsPerCandle) + "&start=" + currentCandleStartedAt
                    + "&limit=1&feed=" + dataFeed()).path("bars");
            if (!bars.isArray()) throw new IllegalStateException("Invalid Alpaca candle response");
            if (bars.isEmpty()) return Optional.empty();
            JsonNode bar = bars.get(0);
            int open = Math.toIntExact(Instant.parse(bar.path("t").asText()).getEpochSecond());
            if (open != currentCandleStartedAt.getEpochSecond()) return Optional.empty();
            return Optional.of(new InProgressCandleData(open, bar.path("o").asDouble(), bar.path("h").asDouble(),
                    bar.path("l").asDouble(), (int) Instant.now().getEpochSecond(), bar.path("c").asDouble(), bar.path("v").asDouble()));
        });
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return failedFuture(unsupported("fetchRecentTradesUntil"));
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
        return switch (secondsPerCandle) {
            case 60 -> "1Min";
            case 300 -> "5Min";
            case 900 -> "15Min";
            case 1800 -> "30Min";
            case 3600 -> "1Hour";
            case 14400 -> "4Hour";
            case 86400 -> "1Day";
            default -> throw new IllegalArgumentException("Unsupported Alpaca candle interval: " + secondsPerCandle);
        };
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
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (isPaperTrading()) {
            return CompletableFuture.completedFuture(paperAccountSnapshot());
        }
        if (!hasCredentials()) return failedFuture(new IllegalStateException("Live Alpaca credentials are required"));

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = executeHttpRequest(alpacaRequest("/v2/account")
                        .GET()
                        .build());
                JsonNode body = OBJECT_MAPPER.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Alpaca API returned HTTP %d: %s".formatted(response.statusCode(), body));
                }
                accountPermissions = body;
                connected = true;
                double cash = body.path("cash").asDouble(0.0);
                double equity = body.path("equity").asDouble(cash);
                double buyingPower = body.path("buying_power").asDouble(cash);
                Map<String, Double> liveBalances = new LinkedHashMap<>();
                liveBalances.put("USD", equity);
                Map<String, Double> availableBalances = new LinkedHashMap<>();
                availableBalances.put("USD", buyingPower);

                Account account = new Account();
                account.setAccountId(body.path("id").asText());
                account.setTotalBalance(equity);
                account.setAvailableBalance(buyingPower);
                account.setEquity(equity);
                account.setCash(cash);
                account.setBuyingPower(buyingPower);
                account.setBalances(liveBalances);
                account.setAvailableBalances(availableBalances);
                account.setExchangeId("alpaca");
                account.setBrokerName("Alpaca");
                account.setPaperTrading(isPaperTrading());
                account.setConnected(true);
                account.setUpdatedAt(Instant.now());
                return account;
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to fetch Alpaca account.", exception);
            }
        });
    }

    private Account paperAccountSnapshot() {
        double equity = balances.values().stream().mapToDouble(Double::doubleValue).sum();
        Account account = new Account();
        account.setTotalBalance(equity);
        account.setAvailableBalance(balances.getOrDefault("USD", equity));
        account.setEquity(equity);
        account.setCash(balances.getOrDefault("USD", 0.0));
        account.setBuyingPower(balances.getOrDefault("USD", 0.0));
        account.setBalances(new LinkedHashMap<>(balances));
        account.setAvailableBalances(new LinkedHashMap<>(balances));
        account.setExchangeId("alpaca");
        account.setBrokerName("Alpaca");
        account.setPaperTrading(true);
        account.setConnected(true);
        account.setUpdatedAt(Instant.now());
        return account;
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return fetchAccount().thenApply(account -> account.getAvailableBalances().getOrDefault(currencyCode, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAccount().thenApply(account -> account.getBalances().getOrDefault(currencyCode, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return fetchAccount().thenApply(Account::getEquity);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.supplyAsync(() -> isPaperTrading() ? 0.0 : readJson("/v2/account").path("initial_margin").asDouble());
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchAccount().thenApply(Account::getBuyingPower);
    }

    private CompletableFuture<String> submitAlpacaOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice,
            String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                accountPermissions = readJson("/v2/account");
                SymbolTradability tradability = mapAlpacaTradability(tradePair);
                if (!tradability.isFullyTradable()) throw new IllegalStateException(tradability.reason());
                if (side == null) throw new IllegalArgumentException("Order side is required");
                JsonNode asset = loadAssets().get(alpacaSymbol(tradePair));
                if (amount != Math.rint(amount) && !asset.path("fractionable").asBoolean(false)) {
                    throw new IllegalArgumentException("Alpaca asset does not support fractional quantities");
                }
                Map<String, String> payload = new LinkedHashMap<>();
                payload.put("symbol", alpacaSymbol(tradePair));
                payload.put("qty", decimal(amount));
                payload.put("side", side == Side.SELL ? "sell" : "buy");
                payload.put("type", type);
                payload.put("time_in_force", "day");
                if ("limit".equals(type)) {
                    payload.put("limit_price", decimal(limitPrice));
                }
                String body = OBJECT_MAPPER.writeValueAsString(payload);
                HttpResponse<String> response = executeHttpRequest(alpacaRequest("/v2/orders")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build());
                JsonNode responseBody = OBJECT_MAPPER.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Alpaca API returned HTTP %d: %s".formatted(response.statusCode(), responseBody));
                }
                JsonNode id = responseBody.get("id");
                return id == null ? responseBody.toString() : id.asText();
            } catch (Exception exception) {
                throw new IllegalStateException("Alpaca order submission failed.", exception);
            }
        });
    }

    @Override
    public CompletableFuture<String> createTrailingStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double trailingAmount,
            boolean trailingPercent) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }
        if (amount <= 0 || !Double.isFinite(amount)) {
            return failedFuture(new IllegalArgumentException("Trailing stop amount must be greater than zero"));
        }
        if (trailingAmount <= 0 || !Double.isFinite(trailingAmount)) {
            return failedFuture(new IllegalArgumentException("Trailing stop distance must be greater than zero"));
        }
        if (isPaperTrading()) return failedFuture(unsupported("local trailing stop"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                accountPermissions = readJson("/v2/account");
                if (!mapAlpacaTradability(tradePair).isFullyTradable()) {
                    throw new IllegalStateException("Alpaca trailing stop requires a connected, tradable account and asset");
                }
                if (side == null || amount != Math.rint(amount)) {
                    throw new IllegalArgumentException("Alpaca trailing stops require a side and whole-share quantity");
                }
                Map<String, String> payload = new LinkedHashMap<>();
                payload.put("symbol", alpacaSymbol(tradePair));
                payload.put("qty", decimal(amount));
                payload.put("side", side == Side.SELL ? "sell" : "buy");
                payload.put("type", "trailing_stop");
                payload.put("time_in_force", "day");
                payload.put(trailingPercent ? "trail_percent" : "trail_price", decimal(trailingAmount));

                String body = OBJECT_MAPPER.writeValueAsString(payload);
                HttpResponse<String> response = executeHttpRequest(alpacaRequest("/v2/orders")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build());
                JsonNode responseBody = OBJECT_MAPPER.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Alpaca trailing stop API returned HTTP %d: %s".formatted(response.statusCode(), responseBody));
                }
                JsonNode id = responseBody.get("id");
                return id == null ? responseBody.toString() : id.asText();
            } catch (Exception exception) {
                throw new IllegalStateException("Alpaca trailing stop submission failed.", exception);
            }
        });
    }

    private HttpRequest.Builder alpacaRequest(String path) {
        return HttpRequest.newBuilder()
                .timeout(java.time.Duration.ofSeconds(15))
                .uri(URI.create(alpacaBaseUrl() + path))
                .header("APCA-API-KEY-ID", exchangeCredentials.apiKey().strip())
                .header("APCA-API-SECRET-KEY", exchangeCredentials.apiSecret().strip())
                .header("User-Agent", "InvestPro/1.0");
    }

    static String dataFeed() {
        String feed = System.getenv().getOrDefault("ALPACA_DATA_FEED", "iex").strip().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("iex", "sip", "delayed_sip").contains(feed)) throw new IllegalArgumentException("Unsupported ALPACA_DATA_FEED");
        return feed;
    }

    JsonNode readData(String path) {
        try {
            HttpRequest request = alpacaRequest(path).uri(URI.create("https://data.alpaca.markets" + path)).GET().build();
            HttpResponse<String> response = executeHttpRequest(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new AlpacaHttpException(response.statusCode());
            return OBJECT_MAPPER.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Alpaca market data interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Alpaca market data failed", exception);
        }
    }

    private String alpacaBaseUrl() {
        String configured = System.getenv("ALPACA_BASE_URL");
        if (configured != null && !configured.isBlank()) {
            if (configured.toLowerCase(java.util.Locale.ROOT).contains("paper-api")) {
                throw new IllegalStateException("Remote Alpaca paper endpoints are disabled. Use local PAPER mode and remove ALPACA_BASE_URL's paper endpoint.");
            }
            return configured.strip();
        }
        return ALPACA_LIVE_URL;
    }

    private static String alpacaSymbol(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        return tradePair.getBaseCode().toUpperCase(java.util.Locale.ROOT);
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Order amount and price values must be positive.");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override
    public void connect() {
        AuthCheckResult result = checkAuthentication();
        if (!result.isSuccess()) throw new IllegalStateException(result.getMessage());
    }

    @Override
    public void disconnect() {
        connected = false;
        accountPermissions = null;
        polling.stopAll();
        if (alpacaWebSocket != null && alpacaWebSocket.isOpen()) alpacaWebSocket.close();
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public Boolean isConnected() {
        return connected;
    }

    private void sendMutation(String path, String method) {
        if (!hasCredentials() || !connected) throw new IllegalStateException("Alpaca connection required");
        try {
            HttpResponse<String> response = executeHttpRequest(alpacaRequest(path)
                    .method(method, HttpRequest.BodyPublishers.noBody()).build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new AlpacaHttpException(response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Alpaca request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Alpaca request failed", exception);
        }
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
}
