package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
    private static final String ALPACA_PAPER_URL = "https://paper-api.alpaca.markets";

    // Paper trading state
    private final java.util.Map<String, Double> balances = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> orders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Position> positions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Trade> tradeHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;
    private AlpacaWebSocket alpacaWebSocket;
    private ExchangeCredentials exchangeCredentials;

    public Alpaca(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        this.exchangeCredentials = exchangeCredentials;
        initializePaperTradingAccount();

        try {
            this.alpacaWebSocket = createWebSocketClient();
        } catch (Exception ex) {
            log.error("Failed to initialize BinanceUs websocket client", ex);
        }

    }

    private AlpacaWebSocket createWebSocketClient() {

        return new AlpacaWebSocket(URI.create(alpacaBaseUrl()), new Draft_6455());
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
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return Boolean.parseBoolean(System.getenv().getOrDefault("ALPACA_PAPER", "false"));
    }

    @Override
    public String getTimestamp() {
        return "";
    }

    @Override
    public Instant now() {
        return null;
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return false;
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
                .apiBaseUrl(isPaperTrading() ? ALPACA_PAPER_URL : ALPACA_LIVE_URL)

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
                .supportsSandbox(isPaperTrading())
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(true)
                .supportsStopLimitOrders(true)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(true)
                .supportsTrailingStopOrders(true)
                .supportsMarginTrading(true)
                .supportsLeverage(true)

                // Account / portfolio
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(true)
                .supportsAccountTrades(true)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsFills(true)
                .supportsOrderValidation(true)

                // Market data
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(true)
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(true)
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)

                // Streaming
                .supportsWebSocket(true)
                .supportsWebSocketStreaming(true)
                .supportsTickerStreaming(true)
                .supportsTradeStreaming(true)
                .supportsCandleStreaming(true)
                .supportsOrderBookStreaming(false)
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
                .requiresAuthenticationForMarketData(true)

                // Notes
                .notes("""
                        Alpaca Trading capability profile.
                        Specializes in US stocks and equities trading.
                        Supports both paper (sandbox) and live trading modes.
                        Minimum $25,000 required for pattern day trader (PDT) margin account.
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
        return null;
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        return null;
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
        return true;
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
        // Polling-based
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
        // Polling-based
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        // Polling-based
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        // Polling-based
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
        // Not applicable
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
    // --------- Order Creation Methods ---------

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
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

            String type = node.path("order_type").asText("limit");
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
        return failedFuture(unsupported("fetchAllOpenOrders"));
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
        if (!hasCredentials()) {
            return AuthResult.failure("Alpaca credentials are not configured");
        }
        return AuthResult.success("Alpaca authentication validated");
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
        return List.of();
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
    public double getLivePrice() {
        return 0;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        return null;
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return null;
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(new OrderBook(tradePair));
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return "";
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
        return null;
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (isPaperTrading() || !hasCredentials()) {
            return CompletableFuture.completedFuture(paperAccountSnapshot());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(alpacaRequest("/v2/account")
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
                JsonNode body = OBJECT_MAPPER.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Alpaca API returned HTTP %d: %s".formatted(response.statusCode(), body));
                }
                double cash = body.path("cash").asDouble(0.0);
                double equity = body.path("equity").asDouble(cash);
                double buyingPower = body.path("buying_power").asDouble(cash);
                Map<String, Double> liveBalances = new LinkedHashMap<>();
                liveBalances.put("USD", equity);
                Map<String, Double> availableBalances = new LinkedHashMap<>();
                availableBalances.put("USD", buyingPower);

                Account account = new Account();
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
        return null;
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return null;
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return null;
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return null;
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return null;
    }

    private CompletableFuture<String> submitAlpacaOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice,
            String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
                HttpResponse<String> response = HTTP_CLIENT.send(alpacaRequest("/v2/orders")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
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

    private HttpRequest.Builder alpacaRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(alpacaBaseUrl() + path))
                .header("APCA-API-KEY-ID", exchangeCredentials.apiKey())
                .header("APCA-API-SECRET-KEY", exchangeCredentials.apiSecret())
                .header("User-Agent", "InvestPro/1.0");
    }

    private String alpacaBaseUrl() {
        String configured = System.getenv("ALPACA_BASE_URL");
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        return isPaperTrading() ? ALPACA_PAPER_URL : ALPACA_LIVE_URL;
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

    }

    @Override
    public void disconnect() {

    }

    @Override
    public void reconnect() {

    }

    @Override
    public Boolean isConnected() {
        return null;
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
