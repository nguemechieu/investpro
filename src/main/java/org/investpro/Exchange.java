package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.ChoiceBox;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class Exchange {

    private static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    static ChoiceBox<String> symbolsChoiceBox = new ChoiceBox<>();


    protected String apiKey;
    protected String apiSecret;

    private String tokens;

    public void setTokens(String tokens) {
        this.tokens = tokens;
    }


    public Exchange(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public abstract TradePair getSelecTradePair() throws SQLException, ClassNotFoundException;

    public void buy(TradePair btcUsd, MARKET_TYPES marketType, double sizes, double stoploss, double takeProfit) {
    }

    public void sell(TradePair btcUsd, MARKET_TYPES marketType, double sizes, double stopLoss, double takeProfit) {
    }

    public void cancelALL() {
    }

    public void autoTrading(@NotNull Boolean auto, String signal) {

        logger.info("auto trading enabled");

    }




    public abstract ExchangeWebSocketClient getWebsocketClient();

    abstract Boolean isConnected();

    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle);


    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant);

    public abstract String getTimestamp();

    public abstract CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair);

    public abstract CompletableFuture<String> createOrder(Orders order) throws JsonProcessingException;

    public abstract CompletableFuture<String> cancelOrder(String orderId);

    public abstract String getSignal();

    public abstract void connect();


    public abstract List<TradePair> getTradePairSymbol();

    public abstract CompletableFuture<String> getOrderBook(TradePair tradePair);

    public abstract Ticker getLivePrice(TradePair tradePair);

    public abstract JsonNode getUserAccountDetails();

    public abstract double getSize();

    public abstract double getLivePrice();

    public abstract String getName();

    public String getTelegramToken() {
        return tokens;
    }
}