package org.investpro.exchange;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.*;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.investpro.market.MarketDataEngine;
import org.investpro.market.ExchangeMarketDataAdapter;
import org.investpro.trading.tradability.ExchangeInstrumentService;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.service.AuthResult;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
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

    private String telegramToken;
    private String emailNotification;
    private String userSelectedTradingMode;
    private ExchangeCredentials credentials;

    protected  MarketDataEngine marketDataEngine;
    protected  ExchangeMarketDataAdapter marketDataAdapter;

    protected Exchange(@NotNull ExchangeCredentials credentials) {
        this.credentials = credentials;

        this.userSelectedTradingMode = credentials.sandbox() ? "PAPER" : "LIVE";
        log.debug(this.getClass().getSimpleName() + " created ", this);
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


    protected boolean modeRequestsPaperNetwork() {
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
                && Boolean.TRUE.equals(isConnected())
                && !isPaperTrading();
    }

    public boolean canSubmitOrders() {
        return Boolean.TRUE.equals(isConnected())
                && (canSubmitLiveOrders() || (supportsPaperTradingMode() && isPaperTrading()));
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
            return CompletableFuture.completedFuture(defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }

        boolean supportsPair = supportsTradePair(pair);
        boolean marketOpen = marketDataEngine == null || marketDataEngine.isTradableNow(pair);
        TradabilityStatus status = supportsPair
                ? (marketOpen ? TradabilityStatus.FULLY_TRADABLE : TradabilityStatus.MARKET_CLOSED)
                : TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE;

        boolean liveAllowed = supportsPair && marketOpen && canSubmitOrders();
        boolean orderAllowed = supportsPair && canSubmitOrders();

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
                supportsPair ? (marketOpen ? "Tradable by default exchange policy" : "Market/session closed")
                        : "Instrument is not supported by this exchange",
                Instant.now(),
                Map.of("source", "exchange-default"));

        return CompletableFuture.completedFuture(tradability);
    }

    public boolean canTradeSymbol(TradePair pair) {
        SymbolTradability status = fetchTradabilityStatus(pair).join();
        return status != null && status.isFullyTradable();
    }

    public boolean canBotTradeSymbol(TradePair pair) {
        SymbolTradability status = fetchTradabilityStatus(pair).join();
        return status != null && status.canBeUsedForBotTrading();
    }

    protected SymbolTradability defaultTradability(TradePair pair, TradabilityStatus status, String reason) {
        boolean supportsPair = pair != null && supportsTradePair(pair);
        boolean marketOpen = pair == null || marketDataEngine == null || marketDataEngine.isTradableNow(pair);
        boolean orderAllowed = supportsPair && canSubmitOrders();
        boolean liveAllowed = supportsPair && marketOpen && orderAllowed;

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
                reason == null ? "" : reason,
                Instant.now(),
                Map.of("source", "exchange-default-helper"));
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
                        .map(OrderBook::new)
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
     * Override in exchange adapters to provide pair discovery and tradeability checks.
     */
    public ExchangeInstrumentService instrumentService() {
        return null;
    }

    /**
     * Returns the full list of tradeable pairs for this exchange asynchronously.
     * Default delegates to {@link #getTradablePairs()}.
     */
    public CompletableFuture<List<TradePair>> getTradeablePairsAsync() {
        return CompletableFuture.supplyAsync(this::getTradablePairs);
    }

    /**
     * Checks if a specific pair is tradeable for this account.
     * Default delegates to {@link #fetchTradabilityStatus(TradePair)}.
     */
    public CompletableFuture<Boolean> isTradeablePair(TradePair pair) {
        return fetchTradabilityStatus(pair)
                .thenApply(st -> st != null && st.status() == TradabilityStatus.FULLY_TRADABLE);
    }
}
