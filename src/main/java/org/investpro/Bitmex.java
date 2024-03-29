package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Bitmex extends Exchange {
    public Bitmex(String text, String text1) {
        super(text, text1);
    }

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return null;
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public double getTradingFee() throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public Account getAccounts() {
        return null;
    }

    @Override
    public List<String> getTradePair() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    @Override
    Boolean isConnected() {
        return null;
    }

    @Override
    public String getSymbol() {
        return null;
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public List<Order> getPendingOrders() {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant) {
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
    public String getTimestamp() {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        return null;
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return null;
    }

    @Override
    public String getSignal() {
        return null;
    }

    @Override
    public void connect() {

    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        return null;
    }

    @Override
    public String getOrderId() {
        return null;
    }

    @Override
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        return null;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return null;
    }

    @Override
    public JsonNode getUserAccountDetails() {
        return null;
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void getPositionBook(TradePair tradePair) throws IOException, InterruptedException {

    }

    @Override
    public void getOpenOrder(@NotNull TradePair tradePair) {

    }
}
