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
import org.investpro.service.AuthResult;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Date;
import java.util.List;
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

    protected @Nullable MarketDataEngine marketDataEngine;
    protected @Nullable ExchangeMarketDataAdapter marketDataAdapter;

    protected Exchange(@NotNull ExchangeCredentials credentials) {
        this.credentials = credentials;

        this.userSelectedTradingMode = credentials.sandbox() ? "PAPER" : "LIVE";
        log.debug(this.getClass().getSimpleName() + " created ", this);
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
                        .map(pair -> new OrderBook(pair))
                        .toList());
    }

    protected UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(
                "%s does not support %s".formatted(getName(), methodName));
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
}