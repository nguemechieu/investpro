package org.investpro.exchange.schwab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.*;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.Account;

import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.AuthResult;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
@EqualsAndHashCode(callSuper = true)
@Data
@Getter
@Setter
@ToString(callSuper = true)
public class Schwab extends Exchange {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final SchwabApiConfig config;
    private final SchwabOAuthTokenService tokenService;
    private final SchwabApiClient apiClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public Schwab(@NotNull ExchangeCredentials credentials) {
        super(credentials);
        this.config = SchwabApiConfig.from(credentials);
        this.tokenService = new SchwabOAuthTokenService(config);
        this.apiClient = new SchwabApiClient(config, tokenService);
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
        try {
            tokenService.getAccessToken();
            return AuthResult.success("Schwab OAuth authentication succeeded");
        } catch (Exception exception) {
            return AuthResult.failure("Schwab OAuth authentication failed: " + exception.getMessage());
        }
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode root = apiClient.fetchAccounts();
                Account account = new Account();
                account.setBrokerName(getDisplayName());
                account.setExchangeId(getExchangeId());
                account.setConnected(true);
                account.setSandbox(isSandbox());
                account.setPaperTrading(isPaperTrading());
                account.setUpdatedAt(Instant.now());

                String configuredAccountId = config.accountId();
                String resolvedAccountId = !configuredAccountId.isBlank() ? configuredAccountId
                        : extractAccountId(root);
                account.setAccountId(resolvedAccountId);
                account.setAccount(resolvedAccountId);

                JsonNode balances = extractBalances(root);
                double cash = getDouble(balances, "cashAvailableForTrading", "cashBalance", "cash");
                double equity = getDouble(balances, "liquidationValue", "equity", "currentLiquidationValue");
                double buyingPower = getDouble(balances, "buyingPower", "dayTradingBuyingPower", "availableFunds");

                account.setCash(cash);
                account.setTotalBalance(equity > 0 ? equity : cash);
                account.setAvailableBalance(Math.max(0.0, cash));
                account.setEquity(equity > 0 ? equity : cash);
                account.setBuyingPower(buyingPower > 0 ? buyingPower : cash);
                account.setPortfolioValue(account.getTotalBalance());

                return account;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to fetch Schwab account", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return fetchAccount().thenApply(Account::getAvailableBalance);
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAccount().thenApply(Account::getTotalBalance);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return fetchAccount().thenApply(Account::getEquity);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return fetchAccount().thenApply(Account::getMarginUsed);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchAccount().thenApply(Account::getFreeMargin);
    }

    @Override
    public boolean supportsLiveTrading() {
        return true;
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
        return true;
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
        connected.set(checkAuthentication().isSuccess());
    }

    @Override
    public void disconnect() {
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
    public String getName() {
        return "SCHWAB";
    }

    @Override
    public String getSignal() {
        return "SCHWAB";
    }

    @Override
    public String getExchangeId() {
        return "schwab";
    }

    @Override
    public String getDisplayName() {
        return "Charles Schwab";
    }

    @Override
    public boolean isSandbox() {
        return config.sandbox();
    }

    @Override
    public boolean isPaperTrading() {
        return "PAPER".equals(getResolvedTradingMode()) || config.sandbox();
    }

    @Override
    public String getTimestamp() {
        return ISO_UTC.format(Instant.now());
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == MARKET_TYPES.STOCKS || marketType == MARKET_TYPES.INDEX;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS, MARKET_TYPES.INDEX);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName(getName())
                .exchangeId(getExchangeId())
                .displayName(getDisplayName())
                .apiBaseUrl(config.baseUrl())
                .authenticationType("OAUTH2")
                .supportsLiveTrading(true)
                .supportsPaperTradingMode(true)
                .supportsSandbox(config.sandbox())
                .supportsStocks(true)
                .supportsEquities(true)
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(false)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(false)
                .supportsOrderBook(false)
                .marketDepthType(MarketDepthType.NONE)
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsHistoricalCandles(false)
                .supportsRecentTrades(false)
                .supportsNativeWebSocket(false)
                .supportsWebSocketStreaming(false)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(true)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsAccountTrades(true)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(true)
                .notes("Dedicated Schwab OAuth2 + REST adapter")
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        try {
            tokenService.getAccessToken();
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(true)
                    .credentialSource("ENV_VAR_OR_PARAMS")
                    .endpointTested(config.oauthTokenUrl())
                    .httpStatus(200)
                    .message("Schwab OAuth token refresh succeeded")
                    .credentialIssue(false)
                    .checkedAt(Instant.now())
                    .metadata(Map.of("authFlow", "refresh_token", "mode", getResolvedTradingMode()))
                    .build();
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "authentication failure" : exception.getMessage();
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialSource("ENV_VAR_OR_PARAMS")
                    .endpointTested(config.oauthTokenUrl())
                    .httpStatus(401)
                    .message(message)
                    .credentialIssue(true)
                    .checkedAt(Instant.now())
                    .metadata(Map.of("authFlow", "refresh_token", "mode", getResolvedTradingMode()))
                    .build();
        }
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        List<TradePair> pairs = getTradablePairs();
        if (!pairs.isEmpty()) {
            return pairs.getFirst();
        }
        return TradePair.fromSymbol("AAPL/USD");
    }

    @Override
    public List<TradePair> getTradePairSymbol() throws SQLException, ClassNotFoundException {
        return getTradablePairs();
    }

    @Override
    public List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException {
        return List.of(
                TradePair.fromSymbol("AAPL/USD"),
                TradePair.fromSymbol("MSFT/USD"),
                TradePair.fromSymbol("GOOGL/USD"),
                TradePair.fromSymbol("SPY/USD"));
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null && tradePair.getBaseCurrency() != null;
    }

    @Override
    public double getLivePrice() {
        try {
            return getLivePrice(getSelectedTradePair()).getLastPrice();
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                String symbol = toSchwabSymbol(tradePair);
                JsonNode root = apiClient.fetchQuote(symbol);
                JsonNode quote = resolveQuoteNode(root, symbol);

                double bid = getDouble(quote, "bidPrice", "bid");
                double ask = getDouble(quote, "askPrice", "ask");
                double last = getDouble(quote, "lastPrice", "last", "mark", "closePrice");
                double open = getDouble(quote, "openPrice", "open");
                double high = getDouble(quote, "highPrice", "high");
                double low = getDouble(quote, "lowPrice", "low");
                double volume = getDouble(quote, "totalVolume", "volume");

                return new Ticker(last, bid, ask, open, high, low, volume, System.currentTimeMillis());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to fetch Schwab ticker", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        CompletableFuture<?>[] futures = tradePairs.stream()
                .map(this::fetchTicker)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(ignored -> tradePairs.stream().map(this::fetchTicker).map(CompletableFuture::join).toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTicker(pair).thenApply(List::of);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        TradePair pair = tradePair;
        if (pair == null) {
            try {
                pair = getSelectedTradePair();
            } catch (Exception exception) {
                return null;
            }
        }

        TradePair resolvedPair = pair;
        return new CandleDataSupplier(200, Math.max(60, secondsPerCandle), resolvedPair,
                new SimpleIntegerProperty((int) Instant.now().getEpochSecond())) {
            @Override
            public java.util.concurrent.Future<List<CandleData>> get() {
                return CompletableFuture.completedFuture(getCandleData());
            }

            @Override
            public List<CandleData> getCandleData() {
                return List.of();
            }

            @Override
            public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                return Schwab.this.getCandleDataSupplier(secondsPerCandle, tradePair);
            }

            @Override
            public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
                    Instant currentCandleStartedAt,
                    long secondsIntoCurrentCandle,
                    int secondsPerCandle) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return CompletableFuture.completedFuture(List.of());
            }
        };
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("Schwab quote endpoint does not provide order book"));
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return null;
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.D1);
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
        if (order == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("order must not be null"));
        }

        String symbol = order.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("order symbol is required"));
        }

        String side = order.getSide() == Side.SELL ? "SELL" : "BUY";
        String type = order.getType() == null ? "MARKET" : order.getType().toUpperCase(Locale.ROOT);
        JsonNode payload = buildOrderPayload(symbol, side, Math.max(0.0, order.getQuantity()), type, order.getPrice());
        return submitOrder(payload);
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
            double stopLoss, double takeProfit, double slippage) {
        Order order = new Order((long) id, new Date(), type, side, toSchwabSymbol(tradePair), amount, price, stopLoss,
                takeProfit, slippage);
        order.setStatus("NEW");
        return order;
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        JsonNode payload = buildOrderPayload(toSchwabSymbol(tradePair), side == Side.SELL ? "SELL" : "BUY", amount,
                "MARKET", 0.0);
        return submitOrder(payload);
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        JsonNode payload = buildOrderPayload(toSchwabSymbol(tradePair), side == Side.SELL ? "SELL" : "BUY", amount,
                "LIMIT", limitPrice);
        return submitOrder(payload);
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Schwab stop orders are not implemented in this adapter"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
            double entryPrice, double stopLoss, double takeProfit) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Schwab bracket orders are not implemented in this adapter"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean cancelled = apiClient.cancelOrder(resolveAccountId(), orderId);
                if (!cancelled) {
                    throw new IllegalStateException("Schwab cancel was not acknowledged");
                }
                return orderId;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to cancel Schwab order", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        CompletableFuture<?>[] futures = orderIds.stream().map(this::cancelOrder).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> orderIds);
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Schwab cancelAllOrders requires an open-orders query implementation"));
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
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("closePosition requires position/order mapping"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("closeAllPositions is not implemented"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("closePosition by id is not implemented"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("closePartialPosition is not implemented"));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("modifyStopLoss is not implemented"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("modifyTakeProfit is not implemented"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return CompletableFuture
                .failedFuture(new UnsupportedOperationException("enableTrailingStop is not implemented"));
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
            double side, double stopLoss, double takeProfit, double slippage) {
        boolean valid = tradePair != null
                && size > 0
                && supportsMarketType(marketType)
                && supportsTradePair(tradePair);
        return CompletableFuture.completedFuture(valid);
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
        return 4.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("setLeverage is not implemented"));
    }

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.NONE;
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

    private CompletableFuture<String> submitOrder(JsonNode payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.placeOrder(resolveAccountId(), payload);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to submit Schwab order", exception);
            }
        });
    }

    private String resolveAccountId() {
        if (config.accountId() != null && !config.accountId().isBlank()) {
            return config.accountId();
        }
        try {
            JsonNode root = apiClient.fetchAccounts();
            String extracted = extractAccountId(root);
            if (extracted == null || extracted.isBlank()) {
                throw new IllegalStateException("SCHWAB_ACCOUNT_ID is required when account lookup fails");
            }
            return extracted;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve Schwab account id", exception);
        }
    }

    private JsonNode buildOrderPayload(String symbol, String instruction, double quantity, String orderType,
            double price) {
        var root = MAPPER.createObjectNode();
        root.put("orderType", orderType);
        root.put("session", "NORMAL");
        root.put("duration", "DAY");
        root.put("orderStrategyType", "SINGLE");
        if ("LIMIT".equals(orderType) && price > 0) {
            root.put("price", normalizePrice(null, price));
        }

        var leg = MAPPER.createObjectNode();
        leg.put("instruction", instruction);
        leg.put("quantity", normalizeAmount(null, quantity));
        var instrument = MAPPER.createObjectNode();
        instrument.put("symbol", symbol);
        instrument.put("assetType", "EQUITY");
        leg.set("instrument", instrument);
        root.putArray("orderLegCollection").add(leg);
        return root;
    }

    private static String toSchwabSymbol(TradePair tradePair) {
        if (tradePair == null) {
            return "";
        }
        if (tradePair.getBaseCurrency() != null && tradePair.getBaseCurrency().getCode() != null) {
            return tradePair.getBaseCurrency().getCode().toUpperCase(Locale.ROOT);
        }
        String text = String.valueOf(tradePair);
        int slash = text.indexOf('/');
        return (slash > 0 ? text.substring(0, slash) : text).toUpperCase(Locale.ROOT);
    }

    private static String extractAccountId(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "";
        }
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            String id = first.path("hashValue").asText("");
            if (!id.isBlank()) {
                return id;
            }
            return first.path("accountNumber").asText("");
        }
        if (root.has("accounts") && root.path("accounts").isArray() && !root.path("accounts").isEmpty()) {
            JsonNode first = root.path("accounts").get(0);
            String id = first.path("hashValue").asText("");
            if (!id.isBlank()) {
                return id;
            }
            return first.path("accountNumber").asText("");
        }
        return root.path("hashValue").asText(root.path("accountNumber").asText(""));
    }

    private static JsonNode extractBalances(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return MAPPER.createObjectNode();
        }

        JsonNode accountNode = root;
        if (root.isArray() && !root.isEmpty()) {
            accountNode = root.get(0);
        } else if (root.has("accounts") && root.path("accounts").isArray() && !root.path("accounts").isEmpty()) {
            accountNode = root.path("accounts").get(0);
        }

        if (accountNode.has("securitiesAccount")) {
            accountNode = accountNode.path("securitiesAccount");
        }

        if (accountNode.has("currentBalances")) {
            return accountNode.path("currentBalances");
        }

        return accountNode;
    }

    private static JsonNode resolveQuoteNode(JsonNode root, String symbol) {
        if (root == null || root.isMissingNode()) {
            return MAPPER.createObjectNode();
        }

        if (root.has(symbol)) {
            JsonNode candidate = root.path(symbol);
            if (candidate.has("quote")) {
                return candidate.path("quote");
            }
            return candidate;
        }

        if (root.has("quote")) {
            return root.path("quote");
        }

        if (root.has("quotes") && root.path("quotes").isArray() && !root.path("quotes").isEmpty()) {
            JsonNode first = root.path("quotes").get(0);
            return first.has("quote") ? first.path("quote") : first;
        }

        return root;
    }

    private static double getDouble(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || keys == null) {
            return 0.0;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                    // Keep trying next candidate field.
                }
            }
        }
        return 0.0;
    }
}
