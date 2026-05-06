package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import  org.investpro.data.Account;
import  org.investpro.data.InProgressCandleData;
import  org.investpro.models.trading.Order;
import  org.investpro.models.trading.OrderBook;
import  org.investpro.models.trading.OpenOrder;
import  org.investpro.models.trading.Position;
import  org.investpro.models.trading.Ticker;
import  org.investpro.models.trading.Trade;
import  org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategySignal;
import  org.investpro.utils.CandleDataSupplier;
import  org.investpro.utils.MARKET_TYPES;
import  org.investpro.utils.Side;
import  org.investpro.exchange.websocket.ExchangeWebSocketClient;
import  org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import  org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import  org.investpro.exchange.infrastructure.OrderCommandConsumer;
import  org.investpro.exchange.infrastructure.StreamTransport;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Base exchange/broker contract for InvestPro.
 *
 * Every concrete broker adapter must explicitly declare:
 * - identity
 * - supported markets
 * - connection behavior
 * - market data behavior
 * - account/balance behavior
 * - order behavior
 * - position behavior
 * - streaming behavior
 * - capability flags
 * - timeframe mapping
 *
 * This prevents silent mistakes where an exchange compiles but returns
 * false, null, empty, or unsupported behavior accidentally.
 */
@Getter
@Setter
public abstract class Exchange {

    protected String apiKey;
    protected String apiSecret;

    private String telegramToken;
    private String emailNotification;

    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = safe(apiKey);
        this.apiSecret = safe(apiSecret);
    }

    // ---------------------------------------------------------------------
    // Identity / metadata
    // ---------------------------------------------------------------------

    public abstract String getName();

    public abstract String getSignal();

    public abstract String getExchangeId();

    public abstract String getDisplayName();

    public abstract boolean isSandbox();

    public abstract boolean isPaperTrading();

    public abstract boolean supportsMarketType(MARKET_TYPES marketType);

    public abstract List<MARKET_TYPES> getSupportedMarketTypes();
  public abstract CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity);

    public abstract   CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice);

    public abstract  CompletableFuture<String> closePosition(TradePair symbol, String positionId);

    public abstract   CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity);

    public abstract  CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss);

    public abstract  CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit);
    // ---------------------------------------------------------------------
    // Notification settings
    // ---------------------------------------------------------------------

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = safe(telegramToken);
    }

    public void setTokens(String telegramToken) {
        setTelegramToken(telegramToken);
    }

    public void setEmailNotification(String emailNotification) {
        this.emailNotification = safe(emailNotification);
    }

    // ---------------------------------------------------------------------
    // Connection / lifecycle
    // ---------------------------------------------------------------------

    public abstract void connect();

    public abstract void disconnect();

    public abstract void reconnect();

    public abstract Boolean isConnected();

    public abstract ExchangeWebSocketClient getWebsocketClient();

    public abstract boolean supportsWebSocket();

    public abstract boolean isWebsocketAvailable();

    public abstract String getTimestamp();

    public abstract Instant now();

    // ---------------------------------------------------------------------
    // Selected/default symbol
    // ---------------------------------------------------------------------

    public abstract TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException;

    /**
     * Backward-compatible typo alias.
     * Keep this concrete so old code continues to compile.
     */
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return getSelectedTradePair();
    }

    // ---------------------------------------------------------------------
    // Market discovery
    // ---------------------------------------------------------------------

    public abstract List<TradePair> getTradePairSymbol();

    public abstract List<TradePair> getTradablePairs();

    public abstract boolean supportsTradePair(TradePair tradePair);

    // ---------------------------------------------------------------------
    // Market data
    // ---------------------------------------------------------------------

    public abstract double getLivePrice();

    public abstract Ticker getLivePrice(TradePair tradePair);

    public abstract CompletableFuture<Ticker> fetchTicker(TradePair tradePair);

    public abstract CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs);

    /**
     * Compatibility alias for older code that calls getTicker(pair).
     */
    public abstract CompletableFuture<List<Ticker>> getTicker(TradePair pair);

    public abstract CandleDataSupplier getCandleDataSupplier(
            int secondsPerCandle,
            TradePair tradePair
    );

    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle
    );

    /**
     * Public market trades, not user/account fills.
     */
    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(
            TradePair tradePair,
            Instant stopAt
    );

    /**
     * Raw or adapter-specific order book response.
     */
    public abstract CompletableFuture<?> getOrderBook(TradePair tradePair);

    /**
     * Structured InvestPro order book.
     */
    public abstract CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair);

    public abstract String supportsTimeframe(int secondsPerCandle);

    public abstract double getSize();

    // ---------------------------------------------------------------------
    // Account / balances
    // ---------------------------------------------------------------------

    public abstract Account getUserAccountDetails() throws ExecutionException, InterruptedException;

    public abstract CompletableFuture<Account> fetchAccount();

    public Account getAccount() {
        try {
            return getUserAccountDetails();
        } catch (Exception exception) {
            return null;
        }
    }

    public abstract CompletableFuture<Double> fetchAvailableBalance(String currencyCode);

    public abstract CompletableFuture<Double> fetchTotalBalance(String currencyCode);

    public abstract CompletableFuture<Double> fetchEquity();

    public abstract CompletableFuture<Double> fetchMarginUsed();

    public abstract CompletableFuture<Double> fetchFreeMargin();

    // ---------------------------------------------------------------------
    // Orders
    // ---------------------------------------------------------------------

    public abstract CompletableFuture<String> createOrder(Order order) throws JsonProcessingException;

    public abstract Order createOrder(
            int id,
            TradePair tradePair,
            String type,
            double price,
            double amount,
            Side side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    public abstract CompletableFuture<String> createMarketOrder(
            TradePair tradePair,
            Side side,
            double amount
    );

    public abstract CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice
    );

    public abstract CompletableFuture<String> createStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double stopPrice
    );

    public abstract CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit
    );

    public CompletableFuture<String> submitOrder(Order order) {
        try {
            return createOrder(order);
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    public abstract CompletableFuture<String> cancelOrder(String orderId);

    public abstract CompletableFuture<List<String>> cancelOrders(List<String> orderIds);

    public abstract CompletableFuture<String> cancelAllOrders();

    public abstract CompletableFuture<Optional<Order>> fetchOrder(String orderId);

    public abstract CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair);

    public abstract CompletableFuture<List<OpenOrder>> fetchAllOpenOrders();

    public abstract CompletableFuture<List<Order>> fetchOrderHistory(
            TradePair tradePair,
            Instant since
    );

    // ---------------------------------------------------------------------
    // Positions / portfolio
    // ---------------------------------------------------------------------

    public abstract CompletableFuture<List<Position>> fetchPositions(TradePair tradePair);

    public abstract CompletableFuture<List<Position>> fetchAllPositions();

    public abstract CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair);

    public abstract CompletableFuture<String> closePosition(TradePair tradePair);

    public abstract CompletableFuture<String> closeAllPositions();

    // ---------------------------------------------------------------------
    // Account trades / fills
    // ---------------------------------------------------------------------

    /**
     * User/account trade fills, not public market trades.
     */
    public abstract CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair);

    public abstract CompletableFuture<List<Trade>> fetchAccountTradesSince(
            TradePair tradePair,
            Instant since
    );

    public abstract CompletableFuture<List<Trade>> fetchAccountTradesBetween(
            TradePair tradePair,
            Instant from,
            Instant to
    );

    // ---------------------------------------------------------------------
    // Manual trading API used by UI buttons
    // ---------------------------------------------------------------------

    public abstract void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    public abstract void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    public abstract void autoTrading(@NotNull Boolean auto, String signal);

    public ExchangeStreamSubscription subscribe(String channel, ExchangeStreamConsumer consumer) {
        return new ExchangeStreamSubscription();
    }

    /**
     * Convenience overload.
     */
    public void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double stopLoss,
            double takeProfit
    ) {
        buy(tradePair, marketType, size, 0.0, stopLoss, takeProfit, 0.0);
    }

    /**
     * Convenience overload.
     */
    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double stopLoss,
            double takeProfit
    ) {
        sell(tradePair, marketType, size, 0.0, stopLoss, takeProfit, 0.0);
    }

    /**
     * Backward-compatible alias.
     */
    public void cancelALL() {
        cancelAllOrders();
    }

    /**
     * Convenience local order factory.
     * Adapters can override if they need broker-specific model creation.
     */
    public Order createOrder(
            long id,
            @NotNull TradePair tradePair,
            String type,
            double price,
            double amount,
            Side side,
            double stopLoss,
            double takeProfit,
            double slippage
    ) {
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
                slippage
        );
    }

    // ---------------------------------------------------------------------
    // Risk / validation / precision
    // ---------------------------------------------------------------------

    public abstract CompletableFuture<Boolean> validateOrder(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    public abstract double normalizeAmount(TradePair tradePair, double amount);

    public abstract double normalizePrice(TradePair tradePair, double price);

    public abstract double getMinOrderAmount(TradePair tradePair);

    public abstract double getMinOrderNotional(TradePair tradePair);

    public abstract double getMaxLeverage(TradePair tradePair);

    public abstract CompletableFuture<Double> fetchLeverage(TradePair tradePair);

    public abstract CompletableFuture<String> setLeverage(
            TradePair tradePair,
            double leverage
    );

    // ---------------------------------------------------------------------
    // Capabilities
    // ---------------------------------------------------------------------

    public abstract boolean supportsLiveTrading();

    public abstract boolean supportsPaperTradingMode();

    public abstract boolean supportsOrderBook();

    public abstract boolean supportsPositions();

    public abstract boolean supportsAccountTrades();

    public abstract boolean supportsStopLossTakeProfit();

    public abstract boolean supportsBracketOrders();

    public abstract boolean supportsLeverage();

    public abstract boolean supportsDerivatives();

    public abstract boolean supportsForex();

    public abstract boolean supportsStocks();

    public abstract boolean supportsCrypto();

    // ---------------------------------------------------------------------
    // Streaming contract
    // ---------------------------------------------------------------------

    public abstract StreamTransport getStreamTransport();

    public abstract boolean supportsNativeWebSocket();

    public abstract boolean supportsHttpStreaming();

    public abstract boolean supportsPollingFallback();

    public abstract void connectStream();

    public abstract void disconnectStream();

    public abstract boolean isStreamConnected();

    public abstract void reconnectStream();

    public abstract void stream(
            ExchangeStreamSubscription subscription,
            ExchangeStreamConsumer consumer
    );

    public abstract void stopStreaming(
            ExchangeStreamSubscription subscription
    );

    public CompletableFuture<List<Trade>> fetchTrades(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    public CompletableFuture<List<Position>> fetchPositions() {
        return CompletableFuture.completedFuture(List.of());
    }

    public void subscribeToOrderUpdates(TradePair tradePair, OrderCommandConsumer consumer) {
    }

    public void subscribeToBalance(ExchangeStreamConsumer consumer) {
        streamBalances(consumer);
    }

    public void subscribeToCandles(TradePair tradePair, ExchangeStreamConsumer consumer) {
        streamCandles(tradePair, 60, consumer);
    }

    public void subscribeToTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        streamTrades(tradePair, consumer);
    }

    public abstract void stopAllStreams();

    public abstract void streamTicker(
            TradePair tradePair,
            ExchangeStreamConsumer consumer
    );

    public abstract void streamTrades(
            TradePair tradePair,
            ExchangeStreamConsumer consumer
    );

    public abstract void streamOrderBook(
            TradePair tradePair,
            ExchangeStreamConsumer consumer
    );

    public abstract void streamCandles(
            TradePair tradePair,
            int secondsPerCandle,
            ExchangeStreamConsumer consumer
    );

    public abstract void streamAccount(ExchangeStreamConsumer consumer);

    public abstract void streamBalances(ExchangeStreamConsumer consumer);

    public abstract void streamOrders(ExchangeStreamConsumer consumer);

    public abstract void streamFills(ExchangeStreamConsumer consumer);

    public abstract void streamPositions(ExchangeStreamConsumer consumer);

    public abstract void stopTickerStream(TradePair tradePair);

    public abstract void stopTradesStream(TradePair tradePair);

    public abstract void stopOrderBookStream(TradePair tradePair);

    public abstract void stopCandlesStream(TradePair tradePair, int secondsPerCandle);

    public abstract void stopAccountStream();

    public abstract void stopBalancesStream();

    public abstract void stopOrdersStream();

    public abstract void stopFillsStream();

    public abstract void stopPositionsStream();

    public abstract boolean supportsAccountStreaming();

    public abstract boolean supportsOrderStreaming();

    public abstract boolean supportsFillStreaming();

    public abstract boolean supportsPositionStreaming();

    public abstract boolean supportsBalanceStreaming();

    public abstract boolean supportsTickerStreaming();

    public abstract boolean supportsOrderBookStreaming();

    public abstract boolean supportsCandleStreaming();

    public abstract boolean supportsTradeStreaming();

    // ---------------------------------------------------------------------
    // Async command helper
    // ---------------------------------------------------------------------

    public void createOrderAsync(
            Order order,
            OrderCommandConsumer consumer
    ) {
        try {
            createOrder(order)
                    .thenAccept(orderId -> {
                        if (consumer != null) {
                            consumer.onAccepted(orderId, order);
                        }
                    })
                    .exceptionally(throwable -> {
                        if (consumer != null) {
                            consumer.onRejected(order, rootMessage(throwable));
                        }
                        return null;
                    });
        } catch (Exception exception) {
            if (consumer != null) {
                consumer.onRejected(order, exception.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
                                                    double price) {
        return CompletableFuture.completedFuture(
                tradePair != null
                        && marketType != null
                        && supportsMarketType(marketType)
                        && size > 0
                        && price >= 0
        );
    }

    public void logExchangeProperties() {
        System.out.printf(
                "Exchange{name='%s', id='%s', displayName='%s', sandbox=%s, paperTrading=%s}%n",
                getName(),
                getExchangeId(),
                getDisplayName(),
                isSandbox(),
                isPaperTrading()
        );
    }

    protected String safe(String value) {
        return value == null ? "" : value.trim();
    }

    protected boolean hasCredentials() {
        return !safe(apiKey).isEmpty() && !safe(apiSecret).isEmpty();
    }

    protected UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(
                "%s does not support %s".formatted(getName(), methodName)
        );
    }

    protected static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    protected String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    public abstract CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) ;



}
