package org.investpro;


import javafx.scene.control.ChoiceBox;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class Exchange {

    private static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    static ChoiceBox<String> symbolsChoiceBox = new ChoiceBox<>();


    protected String apiKey;
    protected String apiSecret;


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
        LivesTrade(auto, signal);

    }

    private void LivesTrade(boolean b, String signal) {
        if (b)
            if (Objects.equals(signal, "BUY")) {
                logger.info("BUY");
            } else if (signal.equals("SELL")) {
                logger.info("SELL");

            } else {
                logger.info("NONE");
            }
    }


    public abstract ExchangeWebSocketClient getWebsocketClient();

    public abstract CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant instant, long secondsIntoCurrentCandle, int secondsPerCandle);


    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant instant);

    public abstract CandleDataSupplier getCandleDataSupplier(int i, TradePair tradePair);

    public abstract String getSignal();

    public abstract void connect();


    public abstract List<TradePair> getTradePairSymbol();
}