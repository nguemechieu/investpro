package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.*;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.models.ExchangeAccessMode;
import org.investpro.exchange.models.ExchangeFeature;
import org.investpro.models.Account;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.trading.*;
import org.investpro.market.MarketDataEngine;
import org.investpro.market.ExchangeMarketDataAdapter;
import org.investpro.trading.market.MarketInstrumentService;
import org.investpro.trading.tradability.ExchangeInstrumentService;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.trading.tradability.session.ExchangeSessionService;
import org.investpro.trading.tradability.session.SessionState;
import org.investpro.service.AuthResult;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
@EqualsAndHashCode(callSuper = false)
@Data
@Slf4j
public abstract class Exchange implements
        ExchangeIdentity,
        ExchangeConnection,
        MarketDataProvider,
        AccountProvider,
        OrderExecutionProvider,
        PositionProvider,
        TradeHistoryProvider,
        StreamingProvider,
        ExchangeCapabilities,
        PrecisionProvider {
    @Override
    public String toString() { return "Exchange[credentials=<redacted>]"; }

    private String telegramToken;
    private String emailNotification;
    private String userSelectedTradingMode;
    private ExchangeCredentials credentials;
    private final LocalPaperExecution localPaperExecution = new LocalPaperExecution();
    private volatile boolean authenticatedSessionConnected;
    @EqualsAndHashCode.Exclude
    private String botTradingMode;

    /** Manual desk execution follows validated authentication, independently of bot mode. */
    public boolean isDeskPaperTrading() {
        return !authenticatedSessionConnected || isPaperTrading() || !supportsLiveTrading();
    }

    public OrderExecutionProvider deskOrderExecution() {
        return isDeskPaperTrading() ? localPaperExecution.provider() : this;
    }

    public CompletableFuture<Account> deskTradingAccount() {
        return isDeskPaperTrading() ? CompletableFuture.completedFuture(localPaperAccount()) : fetchAccount();
    }

    public boolean isBotPaperTrading() {
        if (botTradingMode == null || botTradingMode.isBlank()) return isPaperTrading();
        return !"LIVE".equalsIgnoreCase(botTradingMode);
    }

    public boolean canSubmitBotOrders() {
        return isBotPaperTrading() || (authenticatedSessionConnected && canSubmitLiveOrders());
    }

    public OrderExecutionProvider botOrderExecution() {
        if (isBotPaperTrading()) return localPaperExecution.provider();
        if (!canSubmitBotOrders()) throw new IllegalStateException("Bot live trading requires an authenticated exchange.");
        return this;
    }

    public OrderExecutionProvider orderExecution() {
        return isPaperTrading() ? localPaperExecution.provider() : this;
    }

    public Account localPaperAccount() {
        return localPaperExecution.account(getExchangeId());
    }

    public OrderExecutionProvider localPaperOrderExecution() {
        return localPaperExecution.provider();
    }

    public List<Position> localPaperPositions() {
        return localPaperExecution.positions();
    }

    public void updateLocalPaperMarketPrice(TradePair pair, double price) {
        localPaperExecution.updateMarketPrice(pair, price);
    }

    public CompletableFuture<String> closeLocalPaperPosition(TradePair pair) {
        return localPaperExecution.close(pair);
    }

    public CompletableFuture<Account> tradingAccount() {
        return isPaperTrading() ? CompletableFuture.completedFuture(localPaperAccount()) : fetchAccount();
    }

    protected MarketDataEngine marketDataEngine;
    protected ExchangeMarketDataAdapter marketDataAdapter;

    protected Exchange(@NotNull ExchangeCredentials credentials) {
        this.credentials = credentials;

        this.userSelectedTradingMode = credentials.paramOrDefault("tradingMode", credentials.sandbox() ? "PAPER" : "LIVE");
        log.debug("{} {} created ", this.getClass().getSimpleName(), this);
    }

    public String getResolvedTradingMode() {
        String selected = userSelectedTradingMode;
        if (selected == null || selected.isBlank()) {
            selected = credentials != null && credentials.sandbox() ? "PAPER" : "LIVE";
        }

        String normalized = selected.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PAPER")
                || "SANDBOX".equals(normalized)
                || "DEMO".equals(normalized)
                || "TEST".equals(normalized)
                || "TESTNET".equals(normalized)
                || "PRACTICE".equals(normalized)) {
            return "PAPER";
        }
        return "LIVE";
    }

    /** Current adapter authentication state; implementations must not perform network I/O. */
    public boolean hasPrivateAuthentication() {
        var result = checkAuthentication();
        return result != null && result.isSuccess();
    }

    public ExchangeAccessMode getAccessMode() {
        return hasPrivateAuthentication()
                ? ExchangeAccessMode.AUTHENTICATED
                : ExchangeAccessMode.PUBLIC_DATA_ONLY;
    }

    /** Combines the existing static feature profile with current authentication state. */
    public boolean canUseCapability(ExchangeFeature feature) {
        var capability = getCapability();
        if (feature == null || capability == null || !capability.supports(feature)) {
            return false;
        }
        boolean privateFeature = switch (feature) {
            case ACCOUNT_INFO, BALANCES, POSITIONS, OPEN_ORDERS, ORDER_HISTORY,
                    ACCOUNT_TRADES, FILLS, ORDER_VALIDATION, LIVE_TRADING,
                    MARKET_ORDERS, LIMIT_ORDERS, STOP_ORDERS, STOP_LIMIT_ORDERS,
                    TRAILING_STOP_ORDERS, BRACKET_ORDERS, STOP_LOSS_TAKE_PROFIT,
                    STREAMING_ACCOUNT, STREAMING_BALANCES, STREAMING_POSITIONS,
                    STREAMING_ORDERS, STREAMING_FILLS -> true;
            default -> false;
        };
        boolean authenticatedMarketData = switch (feature) {
            case TICKER, TICKERS, RECENT_TRADES, HISTORICAL_CANDLES, STREAMING_PRICES,
                    ORDER_BOOK, FULL_ORDER_BOOK, TOP_OF_BOOK, DISTRIBUTION_BOOK,
                    STREAMING_TICKER, STREAMING_ORDER_BOOK, STREAMING_TRADES, STREAMING_CANDLES ->
                    capability.isRequiresAuthenticationForMarketData();
            default -> false;
        };
        return !(privateFeature || authenticatedMarketData) || hasPrivateAuthentication();
    }

    /** Paper, sandbox and practice settings select in-process simulation, never a broker environment. */
    protected boolean modeRequestsLocalPaper() {
        return "PAPER".equals(getResolvedTradingMode());
    }

    protected boolean modeRequestsLiveNetwork() {
        return "LIVE".equals(getResolvedTradingMode());
    }

    /**
     * Wire this exchange to a MarketDataEngine for central market data management.
     * Call this from TradingDesk or exchange initialization code.
     */
    public void setMarketDataEngine(@NotNull MarketDataEngine engine) {
        this.marketDataEngine = engine;
        this.marketDataAdapter = new ExchangeMarketDataAdapter(engine, getName());
        log.info("{} connected to MarketDataEngine", getName());
    }

    public void setTokens(String telegramToken) {
        setTelegramToken(telegramToken);
    }

    public TradePair getSelecTradePair() throws Exception {
        return getSelectedTradePair();
    }

    public void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double stopLoss,
            double takeProfit) {
        buy(tradePair, marketType, size, 0.0, stopLoss, takeProfit, 0.0);
    }

    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double stopLoss,
            double takeProfit) {
        sell(tradePair, marketType, size, 0.0, stopLoss, takeProfit, 0.0);
    }

    public void cancelALL() {
        cancelAllOrders();
    }

    public Order createOrder(
            long id,
            @NotNull TradePair tradePair,
            String type,
            double price,
            double amount,
            Side side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        Instant timestamp = now() == null ? Instant.now() : now();

        return new Order(
                id,
                Date.from(timestamp),
                type,
                side,
                tradePair.toString('/'),
                amount,
                price,
                stopLoss,
                takeProfit,
                slippage);
    }

    public boolean canSubmitLiveOrders() {
        return supportsLiveTrading()
                && (authenticatedSessionConnected || Boolean.TRUE.equals(isConnected()))
                && !isPaperTrading();
    }

    public boolean canSubmitOrders() {
        if (isPaperTrading()) {
            return supportsPaperTradingMode();
        }
        return canSubmitLiveOrders();
    }

    public int getDisplayPrecision(TradePair pair) {
        if (pair == null) {
            return 5;
        }
        String symbol = pair.toString('/');
        if (symbol.matches("(?i).*[A-Z]{3}/[A-Z]{3}.*")) {
            return 5;
        }
        return 2;
    }

    public CompletableFuture<List<SymbolTradability>> fetchTradabilityStatus(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.allOf(
                pairs.stream()
                        .map(this::fetchTradabilityStatus)
                        .toArray(CompletableFuture[]::new))
                .thenApply(unused -> pairs.stream()
                        .map(pair -> fetchTradabilityStatus(pair).join())
                        .toList());
    }

    public CompletableFuture<SymbolTradability> fetchTradabilityStatus(TradePair pair) {
        if (pair == null) {
            return CompletableFuture
                    .completedFuture(defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }

        boolean supportsPair = supportsTradePair(pair);
        boolean marketOpen = marketDataEngine == null || marketDataEngine.isTradableNow(pair);
        TradabilityStatus status = supportsPair
                ? (marketOpen ? TradabilityStatus.FULLY_TRADABLE : TradabilityStatus.MARKET_CLOSED)
                : TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "exchange-default");
        metadata.put("productStatus", status.name());
        SessionState sessionState = new ExchangeSessionService().sessionState(this, pair, status, metadata);

        boolean orderAllowed = supportsPair && sessionState.orderSubmissionOpen() && sessionState.openNewPositionsAllowed();
        boolean liveAllowed = supportsPair && marketOpen && orderAllowed;
        metadata.put("accountPermission", canSubmitOrders());
        metadata.put("connected", Boolean.TRUE.equals(isConnected()));
        metadata.put("requiresActiveSession", sessionState.requiresActiveSession());
        metadata.put("session.marketDataAvailable", sessionState.marketDataAvailable());
        metadata.put("session.orderSubmissionOpen", sessionState.orderSubmissionOpen());
        metadata.put("session.cancelAllowed", sessionState.cancelAllowed());
        metadata.put("session.reduceOnly", sessionState.reduceOnly());
        metadata.put("session.openNewPositionsAllowed", sessionState.openNewPositionsAllowed());
        metadata.put("session.reason", sessionState.reason());

        SymbolTradability tradability = new SymbolTradability(
                getExchangeId(),
                pair,
                pair.toString('/'),
                status,
                supportsPair,
                supportsPair,
                supportsPair,
                supportsPair,
                liveAllowed,
                liveAllowed,
                orderAllowed,
                supportsPair,
                supportsPair,
                supportsPair && supportsStopLossTakeProfit(),
                false,
                supportsPair && supportsLeverage(),
                supportsPair && supportsLeverage(),
                supportsPair ? (sessionState.reason())
                        : "Instrument is not supported by this exchange",
                Instant.now(),
                metadata);

        return CompletableFuture.completedFuture(tradability);
    }

    public CompletableFuture<List<MarketInstrument>> fetchMarketInstruments() {
        try {
            return CompletableFuture.completedFuture(
                    MarketInstrumentService.legacyTradePairsToInstruments(this, getTradePairSymbol()));
        } catch (Exception exception) {
            CompletableFuture<List<MarketInstrument>> future = new CompletableFuture<>();
            future.completeExceptionally(exception);
            return future;
        }
    }

    protected SymbolTradability defaultTradability(TradePair pair, TradabilityStatus status, String reason) {
        boolean supportsPair = pair != null && supportsTradePair(pair);
        boolean marketOpen = pair == null || marketDataEngine == null || marketDataEngine.isTradableNow(pair);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "exchange-default-helper");
        metadata.put("productStatus", status == null ? "" : status.name());
        SessionState sessionState = new ExchangeSessionService().sessionState(this, pair, status, metadata);
        boolean orderAllowed = supportsPair && sessionState.orderSubmissionOpen() && sessionState.openNewPositionsAllowed();
        boolean liveAllowed = supportsPair && marketOpen && orderAllowed;
        metadata.put("accountPermission", canSubmitOrders());
        metadata.put("connected", Boolean.TRUE.equals(isConnected()));
        metadata.put("requiresActiveSession", sessionState.requiresActiveSession());
        metadata.put("session.orderSubmissionOpen", sessionState.orderSubmissionOpen());
        metadata.put("session.reason", sessionState.reason());

        return new SymbolTradability(
                getExchangeId(),
                pair,
                pair == null ? "" : pair.toString('/'),
                status,
                supportsPair,
                supportsPair,
                true,
                true,
                liveAllowed,
                liveAllowed,
                orderAllowed,
                supportsPair,
                supportsPair,
                supportsPair && supportsStopLossTakeProfit(),
                false,
                supportsPair && supportsLeverage(),
                supportsPair && supportsLeverage(),
                reason == null || reason.isBlank() ? sessionState.reason() : reason,
                Instant.now(),
                metadata);
    }

    /**
     * Default batch implementation: fetch order books for multiple trading pairs.
     * Can be overridden by subclasses for optimized batch operations.
     *
     * @param tradePairs list of trading pairs
     * @return CompletableFuture containing list of order books
     */
    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBooks(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.allOf(
                tradePairs.stream()
                        .map(this::fetchOrderBook)
                        .toArray(CompletableFuture[]::new))
                .thenApply(v -> tradePairs.stream()
                        .map(p -> {
                            try {
                                return new OrderBook(p);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to create OrderBook for " + p, e);
                            }
                        })
                        .toList());
    }

    protected UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(
                "%s does not support %s".formatted(getName(), methodName));
    }

    public CompletableFuture<List<Order>> fetchAllOrders() {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<List<Order>> fetchPendingOrders() {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<String> replaceOrder(String orderId, Order newOrder) {
        return failedFuture(unsupported("replaceOrder"));
    }

    public CompletableFuture<String> updateOrderClientExtensions(String orderId, Map<String, String> extensions) {
        return failedFuture(unsupported("updateOrderClientExtensions"));
    }

    public CompletableFuture<List<Account>> listAccounts() {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<Account> getAccountDetails(String accountID) {
        return failedFuture(unsupported("getAccountDetails"));
    }

    public CompletableFuture<String> configureAccount(String accountID, Map<String, Object> config) {
        return failedFuture(unsupported("configureAccount"));
    }

    public CompletableFuture<JsonNode> getAccountChanges(String accountID, String sinceTransactionID) {
        return failedFuture(unsupported("getAccountChanges"));
    }

    public CompletableFuture<Optional<Trade>> getTrade(String tradeID) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    public CompletableFuture<String> closeTrade(String tradeID, double units) {
        return failedFuture(unsupported("closeTrade"));
    }

    public CompletableFuture<String> updateTradeClientExtensions(String tradeID, Map<String, String> extensions) {
        return failedFuture(unsupported("updateTradeClientExtensions"));
    }

    public CompletableFuture<String> manageTradeOrders(String tradeID, double stopLoss, double takeProfit) {
        return failedFuture(unsupported("manageTradeOrders"));
    }

    public CompletableFuture<List<Position>> getAllPositions() {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<Optional<Position>> getPositionDetails(String instrument) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    public CompletableFuture<List<JsonNode>> getTransactions(int maxCount, String sinceTransactionID) {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<JsonNode> getTransaction(String transactionID) {
        return failedFuture(unsupported("getTransaction"));
    }

    public CompletableFuture<List<JsonNode>> getTransactionIdRange(String fromTransactionID, String toTransactionID) {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<List<JsonNode>> getTransactionsSinceId(String sinceTransactionID) {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<Void> streamTransactions(
            Consumer<JsonNode> onMessage,
            Consumer<Throwable> onError) {
        return failedFuture(unsupported("streamTransactions"));
    }

    public CompletableFuture<Map<String, JsonNode>> getLatestCandles(List<String> instruments) {
        return failedFuture(new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support batch candle retrieval"));
    }

    public CompletableFuture<List<JsonNode>> getInstrumentCandles(String instrument, String granularity,
            String from, String to, int count) {
        return failedFuture(new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support instrument candle retrieval"));
    }

    protected static <T> @NotNull CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    public abstract void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage);

    public abstract void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage);

    public abstract AuthResult AuthCheckResult(String selectedExchange);

    /**
     * Returns the instrument service for this exchange, or null if not overridden.
     * Override in exchange adapters to provide pair discovery and tradability
     * checks.
     */
    public ExchangeInstrumentService instrumentService() {
        return null;
    }

}
