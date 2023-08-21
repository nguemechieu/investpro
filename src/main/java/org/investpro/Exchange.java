package org.investpro;

import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public abstract class Exchange {
    protected final ExchangeWebSocketClient webSocketClient;

    protected Exchange(ExchangeWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }


    /**
     * @return this exchange's {@code ExchangeWebSocketClient} instance, which is responsible for grabbing
     * live-streaming data (such as trades, orders, etc).
     */
    public ExchangeWebSocketClient getWebsocketClient() {
        return webSocketClient;
    }

    //    private @Nullable String timestampSignature(
//            String apiKey,
//            String passphrase
//    ) {
//        Objects.requireNonNull(apiKey);
//        Objects.requireNonNull(passphrase);
//
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
//        String stringToSign = timestamp + "\n" + apiKey + "\n" + passphrase;
//
//        try {
//            byte[] hash = MessageDigest.getInstance("SHA-256").digest(stringToSign.getBytes());
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//
//
//    }
    public abstract Set<Integer> getSupportedGranularities();

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */
    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);

    //GET /sapi/v1/asset/query/trading-fee
    public abstract double getTradingFee() throws IOException, InterruptedException;

    public abstract void cancelOrder(@NotNull TradePair tradePair, long orderId) throws IOException, InterruptedException;

    public abstract String getName();

    /**
     * Returns the {@code CandleDataSupplier} implementation that will be used to provide pages of candle data for the
     * given {@code secondsPerCandle} and {@code tradePair}.
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    /**
     * Fetches completed candles (of smaller duration than the current {@code secondsPerCandle}) in the duration of
     * the current live-syncing candle.
     * <p>
     * TThis method only needs to be implemented to support live syncing.
     */
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        throw new UnsupportedOperationException("Exchange: " + this + " does not support fetching candle data" +
                " for in-progress candle");
    }

    public abstract void onOpen(ServerHandshake handshake);

    public abstract void onMessage(String message);

    public abstract void onClose(int code, String reason, boolean remote);

    public abstract void onError(@NotNull Exception ex);

    public abstract String getSymbol();

    public abstract double getLivePrice(TradePair tradePair);

    public abstract ArrayList<Double> getVolume();

    public abstract String getOpen();

    public abstract String getHigh();

    public abstract String getLow();

    public abstract String getClose();

    public abstract String getTimestamp();

    public abstract String getTradeId();

    public abstract String getOrderId();

    public abstract String getTradeType();

    public abstract String getSide();

    public abstract String getExchange();

    public abstract String getCurrency();

    public abstract String getAmount();

    public abstract String getFee();

    public abstract String getAvailable();

    public abstract String getBalance();

    public abstract String getPending();

    public abstract String getTotal();

    public abstract String getDeposit();

    public abstract String getWithdraw();

    public abstract void deposit(Double value);

    public abstract void withdraw(Double value);

    public abstract @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException;

    public abstract void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size,
                                     @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException;

    public abstract void closeAllOrders();

    public abstract List<String> getTradePair() throws IOException, InterruptedException, SQLException, ClassNotFoundException;

    public abstract void connect(String text, String text1, String userIdText);

    public abstract boolean isConnected();

    public abstract void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException;

    public abstract void closeAll() throws IOException, InterruptedException;

    public abstract void createOrder(@NotNull TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice);

    public abstract ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException;

    abstract double getLiveTickerPrices() throws IOException, InterruptedException;

    public abstract Node getAllOrders() throws IOException, InterruptedException;

    public abstract Account getAccounts() throws IOException, InterruptedException;

    public abstract void getPositionBook(TradePair tradePair) throws IOException, InterruptedException;

    public abstract void getOpenOrder(TradePair tradePair);

    public abstract void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException;

    public abstract List<Order> getPendingOrders();

    public abstract @NotNull List<Account> getAccount() throws IOException, InterruptedException;

    public abstract void cancelOrder(long orderID) throws IOException, InterruptedException;

    public abstract void cancelAllOrders();

    public abstract void cancelAllOpenOrders();

    public abstract ListView<Order> getOrderView();

    public abstract List<OrderBook> getOrderBook(TradePair tradePair);
}
