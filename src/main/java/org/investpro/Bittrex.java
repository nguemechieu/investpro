package org.investpro;

import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Bittrex extends Exchange {
    TradePair tradePair;


    public Bittrex(String apikey, String s, String s1) {
        super(null);
    }


    //
//     "Bittrex",
//             "https://bittrex.com/api/v1.1/public/getmarketsummaries",
//             "https://bittrex.com/api/v1.1/public/getcurrencies",
//             "https://bittrex.com/api/v1.1/public/getorderbook",
//             "https://bittrex.com/api/v1.1/public/getticker",
//             "https://bittrex.com/api/v1.1/public/getticker24hr",
//             "https://bittrex.com/api/v1.1/public/gettradehistory",

    @Override
    public String getName() {
        return
                "Bittrex";
    }


    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle() {
        return null;
    }


    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return
                new HashSet<>(Arrays.asList(
                        60, 60 * 5, 60 * 15, 3600, 3600 * 4, 3600 * 12, 3600 * 24,
                        3600 * 24 * 7, 3600 * 24 * 30, 3600 * 24 * 30 * 7,
                        3600 * 24 * 7 * 4, 3600 * 24 * 365
                ));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return

                null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {

    }

    @Override
    public void onMessage(String message) {
        System.out.println(message);

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println(code + " " + reason);

    }

    @Override
    public void onError(@NotNull Exception ex) {
        ex.printStackTrace();

    }

    @Override
    public String getSymbol() {
        return null;
    }

    @Override
    public double getLivePrice(TradePair tradePair) {
        return 0;
    }

    @Override
    public ArrayList<Double> getVolume() {
        return null;
    }

    @Override
    public String getOpen() {
        return null;
    }

    @Override
    public String getHigh() {
        return null;
    }

    @Override
    public String getLow() {
        return null;
    }

    @Override
    public String getClose() {
        return null;
    }

    @Override
    public String getTimestamp() {
        return null;
    }

    @Override
    public String getTradeId() {
        return null;
    }

    @Override
    public String getOrderId() {
        return null;
    }

    @Override
    public String getTradeType() {
        return null;
    }

    @Override
    public String getSide() {
        return null;
    }

    @Override
    public String getExchange() {
        return null;
    }

    @Override
    public String getCurrency() {
        return null;
    }

    @Override
    public String getAmount() {
        return null;
    }

    @Override
    public String getFee() {
        return null;
    }

    @Override
    public String getAvailable() {
        return null;
    }

    @Override
    public String getBalance() {
        return null;
    }

    @Override
    public String getPending() {
        return null;
    }

    @Override
    public String getTotal() {
        return null;
    }

    @Override
    public String getDeposit() {
        return null;
    }

    @Override
    public String getWithdraw() {
        return null;
    }

    @Override
    public void deposit(Double value) {

    }

    @Override
    public void withdraw(Double value) {

    }

    @Override
    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException {

    }

    @Override
    public @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException {
        return new ArrayList<>();
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

    }


    @Override
    public void closeAllOrders() {

    }



    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public Node getAllOrders() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public Account getAccounts() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void getPositionBook(TradePair tradePair) throws IOException, InterruptedException {

    }

    @Override
    public void getOpenOrder(TradePair tradePair) {

    }

    @Override
    public void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException {

    }

    @Override
    public List<Order> getPendingOrders() {
        return null;
    }

    @Override
    public @NotNull List<Account> getAccount() throws IOException, InterruptedException {
        return null;
    }


    @Override
    public List<String> getTradePair() {
        return new ArrayList<>(

        );
    }

    @Override
    public void cancelOrder(long orderID) throws IOException, InterruptedException {
        System.out.println(orderID);


    }

    @Override
    public void cancelAllOrders() {

    }

    @Override
    public void cancelAllOpenOrders() {

    }

    @Override
    public ListView<Order> getOrderView() {
        return new ListView<>();
    }

    @Override
    public List<OrderBook> getOrderBook(TradePair tradePair) {
        return null;
    }

    public void createOrder(double price, ENUM_ORDER_TYPE type, Side side, double quantity, double stopLoss, double takeProfit) {
    }

    public void CancelOrder(long orderID) {
        System.out.println(orderID);

    }

    public void createOrder(TradePair tradePair, Side sell, ENUM_ORDER_TYPE market, double quantity, int i, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {
    }

    public void closeAll() {
    }

    public void createOrder(@NotNull TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {

    }

    @Override
    public ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException {
        return null;
    }

    @Override
    double getLiveTickerPrices() throws IOException, InterruptedException {
        return 0;
    }
}
