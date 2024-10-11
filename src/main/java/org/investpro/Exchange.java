package org.investpro;

import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


/**
 * An abstract base class for {@code Exchange} implementations without WebSocket.
 * Provides common methods and behaviors for HTTP-based communication.
 */
public abstract class Exchange {

    protected static final Logger logger = LoggerFactory.getLogger(Exchange.class);


    protected String apiKey;
    protected static String apiSecret;
    public LiveTradesConsumer liveTradesConsumer;
    protected String message;

    /**
     * Constructor with API key and secret.
     */
    protected Exchange(String apiKey, String apiSecret) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        Exchange.apiSecret = Objects.requireNonNull(apiSecret, "API secret must not be null");
        logger.info("Exchange initialized with API key and secret.");
    }

    public abstract CompletableFuture<List<Fee>> getTradingFee() throws IOException, InterruptedException;

    /**
     * Fetch user accounts asynchronously.
     */
    public abstract CompletableFuture<List<Account>> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;


    /**
     * Create an order on the exchange.
     */
    public abstract void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType,
                                     double price, double size, Date timestamp, double stopLoss, double takeProfit)
            throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    /**
     * Cancel an order on the exchange.
     */
    public abstract CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Get exchange message or status.
     */
    public abstract String getExchangeMessage();

    /**
     * Fetch recent trades for a trade pair until a specific timestamp.
     */
    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) throws NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Get the supplier for candle data (e.g., candlestick chart data).
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    /**
     * Fetch candle data for the current in-progress candle, used for live syncing.
     */
    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);


    /**
     * Retrieve the list of pending orders.
     */
    public abstract List<Order> getPendingOrders() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Fetch the order book for a specific trade pair.
     */
    public abstract CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;
    public abstract Position getPositions() throws IOException, InterruptedException, ExecutionException;

    /**
     * Retrieve open orders for a specific trade pair.
     */
    public abstract List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Retrieve a list of orders from the exchange.
     */
    public abstract ObservableList<Order> getOrders() throws IOException, InterruptedException, SQLException, ClassNotFoundException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException;

    /**
     * Retrieve the available trade pairs from the exchange.
     */
    public abstract CompletableFuture<ArrayList<TradePair>> getTradePairs() throws Exception;

    /**
     * Clear live trades consumer data.
     */
    public void clear() {
        liveTradesConsumer.clear();
    }

    /**
     * Add an exchange to the live trades consumer.
     */
    public void add(Exchange exchange) {
        liveTradesConsumer.add(exchange);
    }


    /**
     * Streaming trades is no longer supported without WebSocket.
     * This method should be overridden if the derived class needs to implement streaming over other mechanisms.
     */
    public abstract void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer);


    /**
     * Stop streaming live trades for a specific trade pair.
     */
    public abstract void stopStreamLiveTrades(TradePair tradePair);

    // Get candlestick data
    // WebSocket client for live updates
    // Stream live prices
    public abstract List<PriceData> streamLivePrices(@NotNull TradePair symbol);

    // Stream live candlestick data
    public abstract List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds);

    // Stream live order book
    public abstract List<OrderBook> streamOrderBook(@NotNull TradePair tradePair);
    // Cancel all orders
    public abstract CompletableFuture<String> cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    public abstract boolean supportsStreamingTrades(TradePair tradePair);

    //  Get Crypto Deposit History GET /sapi/v1/capital/deposit/hisrec (HMAC SHA256)
    public abstract ArrayList<CryptoDeposit> getCryptosDeposit() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    //  Get Crypto Withdraw History GET /sapi/v1/capital/withdraw/history (HMAC SHA256)
    public abstract ArrayList<CryptoWithdraw> getCryptosWithdraw() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;
    public abstract List<Trade> getLiveTrades(List<TradePair> tradePairs);


}