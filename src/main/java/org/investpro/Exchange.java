package org.investpro;

import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Exchange {
    public static TradePair tradePair;
    protected ExchangeWebSocketClient webSocketClient;

    protected Exchange(ExchangeWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }


    /**
     * @return this exchange's {@code ExchangeWebSocketClient} instance, which is responsible for grabbing
     * live-streaming data (such as trades, orders, etc).
     */
    public abstract ExchangeWebSocketClient getWebsocketClient();


    public abstract Set<Integer> getSupportedGranularities();

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */

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
    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle();


    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);

    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);

    public abstract void onOpen(ServerHandshake handshake);

    public abstract void onMessage(String message);

    public abstract void onClose(int code, String reason, boolean remote);

    public abstract void onError(Exception ex);

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

    public abstract void withdraw(Double value) throws IOException, InterruptedException;


    public abstract void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException;

    public abstract void closeAll() throws IOException, InterruptedException;

    public abstract void createOrder(@NotNull TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice);

    public abstract ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException;

    abstract double getLiveTickerPrices() throws IOException, InterruptedException;

    public abstract @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException;

    public abstract void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size,
                                     @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException;

    public abstract void closeAllOrders() throws IOException, InterruptedException;


    public abstract void cancelOrder(long orderID) throws IOException, InterruptedException;

    public abstract void cancelAllOrders() throws IOException, InterruptedException;

    public abstract void cancelAllOpenOrders() throws IOException, InterruptedException;


    @Override
    public String toString() {
        return "Exchange{" +
                "tradePair=" + tradePair +
                ", webSocketClient=" + webSocketClient +
                '}';
    }

    public abstract ListView<Order> getOrderView() throws IOException, InterruptedException, ParseException, URISyntaxException;

    public abstract List<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException;


    public abstract List<String> getTradePair() throws IOException, InterruptedException, ParseException, URISyntaxException, SQLException, ClassNotFoundException;

    public abstract void connect(String text, String text1, String userIdText) throws IOException, InterruptedException;

    public abstract boolean isConnected();

    public abstract Node getAllOrders() throws IOException, InterruptedException;

    public abstract Account getAccounts() throws IOException, InterruptedException;

    public abstract void getPositionBook(TradePair tradePair) throws IOException, InterruptedException;

    public abstract void getOpenOrder(TradePair tradePair) throws IOException, InterruptedException;

    public abstract void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException;

    public ArrayList<CandleData> getCandleData() {
        return
                new ArrayList<>() {{
                    add(
                            new CandleData()
                    );
                }};
    }

    public ArrayList<Object> getIndicatorList() {
        return
                new ArrayList<>() {{
                    add(MA.class);
                    add(EMA.class);
                    add(SMA.class);
                    // add(DEMA.class);


                    // add(WMA.class);

                    //,StochRSI.class,MACD.class,RSI.class

                    add(StochRSI.class);
                    add(MACD.class);
                    add(RSI.class);


                }};
    }

    public abstract List<Order> getPendingOrders() throws IOException, InterruptedException;

    public abstract @NotNull List<Account> getAccount() throws IOException, InterruptedException;
}