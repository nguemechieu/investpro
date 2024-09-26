package org.investpro;

import com.fasterxml.jackson.core.JsonParser;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * An abstract base class for {@code Exchange} implementations.
 */
public abstract class Exchange {
    protected String apiKey;
    protected String apiSecret;
    public LiveTradesConsumer liveTradesConsumer;
    protected ExchangeWebSocketClient webSocketClient;
    protected TradePair tradePair;

    /**
     * Constructor with WebSocket client, API key, and API secret.
     */
    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        this.apiSecret = Objects.requireNonNull(apiSecret, "API secret must not be null");



    }


    /**
     * Abstract method to get user accounts.
     */
    public abstract CompletableFuture<Account> getAccounts() throws IOException, InterruptedException;

    /**
     * Abstract method to check connection status.
     */
    public abstract Boolean isConnected();

    /**
     * Abstract method to get the symbol of the exchange.
     */
    public abstract String getSymbol();

    /**
     * Abstract method to create an order.
     */
    public abstract void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType,
                                     double price, double size, Date timestamp, double stopLoss, double takeProfit)
            throws IOException, InterruptedException;

    /**
     * Abstract method to cancel an order.
     */
    public abstract CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException;

    public abstract String getExchangeMessage();

    /**
     * Abstract method to fetch recent trades until a specific time.
     */
    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);

    /**
     * Abstract method to get a candle data supplier.
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    /**
     * Fetches completed candles during the current live-syncing candle.
     */
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        throw new UnsupportedOperationException(
                String.format("Exchange: %s does not support fetching candle data for in-progress candle", this)
        );
    }

    /**
     * Abstract method to get pending orders.
     */
    public abstract List<Order> getPendingOrders() throws IOException, InterruptedException;

    public abstract CompletableFuture<String> getOrderBook(TradePair tradePair) throws IOException, InterruptedException;

    /**
     * Abstract method to get user account details.
     */
    public abstract JsonParser getUserAccountDetails();

    /**
     * Abstract method to connect to the exchange.
     */
    public abstract void connect(String text, String text1, String userIdText) throws IOException, InterruptedException;

    /**
     * Abstract method to get a position book for a trade pair.
     */
    public abstract void getPositionBook(TradePair tradePair) throws IOException, InterruptedException;

    public abstract List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException;

    /**
     * Abstract method to get orders.
     */
    public abstract ObservableList<Order> getOrders() throws IOException, InterruptedException;


    public abstract CompletableFuture<ArrayList<TradePair>> getTradePairs() throws IOException, InterruptedException;

    public void clear() {
        liveTradesConsumer.clear();
    }

    public void add(Exchange exchange) {
        liveTradesConsumer.add(exchange);
    }

    public @NotNull List<Trade> getLiveTrades() {
        return liveTradesConsumer.getLiveTrades();
    }
}
