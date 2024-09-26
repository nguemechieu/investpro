package org.investpro;

import com.fasterxml.jackson.core.JsonParser;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class Binance extends Exchange {
    public Binance(String text, String text1, TradePair tradePair) {
        super(
                text,
                text1
        );
    }

    @Override
    public CompletableFuture<Account> getAccounts() throws IOException {
        return null;
    }

    @Override
    public Boolean isConnected() {
        return null;
    }

    @Override
    public String getSymbol() {
        return "";
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public String getExchangeMessage() {
        return "";
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public List<Order> getPendingOrders() throws IOException {
        return List.of();
    }

    @Override
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        return null;
    }

    @Override
    public JsonParser getUserAccountDetails() {
        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    @Override
    public void getPositionBook(TradePair tradePair) {

    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) {
        return List.of();
    }

    @Override
    public ObservableList<Order> getOrders() {
        return null;
    }

    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() throws IOException, InterruptedException {
        return null;
    }
}
