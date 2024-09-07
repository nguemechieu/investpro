package org.investpro;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.investpro.Coinbase.CoinbaseCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.Coinbase.client;
import static org.investpro.Coinbase.requestBuilder;

public class BinanceUs extends Exchange {
    public BinanceUs(String apikey, String apiSecret) {
        super(

                apikey, apiSecret
        );
    }

    @Override
    public Account getAccounts() throws IOException {
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
    public List<TradePair> getTradePairs() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/exchangeInfo")).build();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(String.format("Failed to fetch data: HTTP error code %d", response.statusCode()));
        }


        return OBJECT_MAPPER.readValue(response.body(), new TypeReference<List<TradePair>>() {
        });


    }
}
