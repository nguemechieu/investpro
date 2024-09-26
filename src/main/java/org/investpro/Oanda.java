package org.investpro;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;


public class Oanda extends Exchange {

HttpClient client=HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
    .connectTimeout(Duration.ofSeconds(10))
            .build();
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Logger logger = LoggerFactory.getLogger(Oanda.class);
    String apiSecret;

     HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    ///instruments/\{tradePair.toString('_')}/candles?";

    static String account_id;

    String url = "https://api-trade.oanda.com/api/v3";

    private Order order;
    //    Accounts	Get a single account's holds	Get Account	Look for the hold object
    private JsonNode res;
    private String message;


    public Oanda(String account_id, String apiSecret) {
        super(account_id, apiSecret);

        apiSecret = "8bf45b37af06b42c5ee42adb4525f339-975adff698b1158504abc2c216e450f5";
        requestBuilder.header("Authorization", "Bearer  %s".formatted(apiSecret));
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Content-Type",
                "application/json");

        Oanda.account_id = account_id;
        this.apiSecret=apiSecret;

    }


    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiSecret);
    }


    @Override
    public CompletableFuture<Account> getAccounts() throws IOException {

// Log the account details before deserialization
        logger.info("Account details before deserialization: {}", res);

// Deserialize the JSON response into an Account object
        JsonNode data = OBJECT_MAPPER.readTree(getUserAccountDetails());
        JsonParser pa = data.traverse();
        Account account = OBJECT_MAPPER.readValue(pa, Account.class);

// Log the deserialized account object
        logger.info("Deserialized Account: {}", account);
        return CompletableFuture.completedFuture(account);
        //{"guaranteedStopLossOrderMode":"DISABLED","hedgingEnabled":false,"id":"001-001-2783446-006","createdTime":"2020-10-24T21:33:42.629182495Z","currency":"USD","createdByUserID":2783446,"alias":"bigbossmanager","marginRate":"0.02","lastTransactionID":"1682519","balance":"0.6477","openTradeCount":0,"openPositionCount":0,"pendingOrderCount":0,"pl":"-1764.7817","resettablePL":"-1764.7817","resettablePLTime":"0","financing":"-165.8606","commission":"0.0000","dividendAdjustment":"0","guaranteedExecutionFees":"0.0000","orders":[],"positions":[{"instrument":"EUR_USD","long":{"units":"0","pl":"-553.2230","resettablePL":"-553.2230","financing":"-62.9359","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-94.9188","resettablePL":"-94.9188","financing":"2.0844","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-648.1418","resettablePL":"-648.1418","financing":"-60.8515","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_GBP","long":{"units":"0","pl":"-37.8990","resettablePL":"-37.8990","financing":"-1.8537","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-3.6504","resettablePL":"-3.6504","financing":"-0.0920","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-41.5494","resettablePL":"-41.5494","financing":"-1.9457","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_AUD","long":{"units":"0","pl":"10.1882","resettablePL":"10.1882","financing":"-2.6699","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"23.7393","resettablePL":"23.7393","financing":"-0.0679","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"33.9275","resettablePL":"33.9275","financing":"-2.7378","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CAD","long":{"units":"0","pl":"-21.3483","resettablePL":"-21.3483","financing":"-5.7842","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"13.6967","resettablePL":"13.6967","financing":"-0.2957","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-7.6516","resettablePL":"-7.6516","financing":"-6.0799","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_SGD","long":{"units":"0","pl":"-53.3320","resettablePL":"-53.3320","financing":"-0.8666","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.1159","resettablePL":"-2.1159","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-55.4479","resettablePL":"-55.4479","financing":"-0.8666","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CHF","long":{"units":"0","pl":"-18.5522","resettablePL":"-18.5522","financing":"-1.6526","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"8.0871","resettablePL":"8.0871","financing":"-1.0198","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-10.4651","resettablePL":"-10.4651","financing":"-2.6724","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_JPY","long":{"units":"0","pl":"8.5820","resettablePL":"8.5820","financing":"-1.9131","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"21.5460","resettablePL":"21.5460","financing":"-0.8309","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"30.1280","resettablePL":"30.1280","financing":"-2.7440","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_NZD","long":{"units":"0","pl":"72.7653","resettablePL":"72.7653","financing":"-2.0675","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-60.3937","resettablePL":"-60.3937","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"12.3716","resettablePL":"12.3716","financing":"-2.0675","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_HKD","long":{"units":"0","pl":"-1.1542","resettablePL":"-1.1542","financing":"-0.0948","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.2919","resettablePL":"0.2919","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.8623","resettablePL":"-0.8623","financing":"-0.0948","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_CZK","long":{"units":"0","pl":"-9.5592","resettablePL":"-9.5592","financing":"-0.5163","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0939","resettablePL":"-0.0939","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-9.6531","resettablePL":"-9.6531","financing":"-0.5163","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_DKK","long":{"units":"0","pl":"-0.3945","resettablePL":"-0.3945","financing":"-0.2124","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.3945","resettablePL":"-0.3945","financing":"-0.2124","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_NOK","long":{"units":"0","pl":"0.2218","resettablePL":"0.2218","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.1036","resettablePL":"0.1036","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.3254","resettablePL":"0.3254","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_PLN","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0208","resettablePL":"0.0208","financing":"-0.0016","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.0208","resettablePL":"0.0208","financing":"-0.0016","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_SEK","long":{"units":"0","pl":"-0.5198","resettablePL":"-0.5198","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.4391","resettablePL":"0.4391","financing":"-0.0856","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0807","resettablePL":"-0.0807","financing":"-0.0856","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"EUR_ZAR","long":{"units":"0","pl":"0.3323","resettablePL":"0.3323","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.3323","resettablePL":"0.3323","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CAD","long":{"units":"0","pl":"21.3984","resettablePL":"21.3984","financing":"-11.7618","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-259.8663","resettablePL":"-259.8663","financing":"-19.1421","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-238.4679","resettablePL":"-238.4679","financing":"-30.9039","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_SGD","long":{"units":"0","pl":"-3.3738","resettablePL":"-3.3738","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.8729","resettablePL":"-0.8729","financing":"-0.0378","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-4.2467","resettablePL":"-4.2467","financing":"-0.0378","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CHF","long":{"units":"0","pl":"-4.7705","resettablePL":"-4.7705","financing":"0.1150","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"13.6308","resettablePL":"13.6308","financing":"-0.5456","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"8.8603","resettablePL":"8.8603","financing":"-0.4306","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_JPY","long":{"units":"0","pl":"-61.4188","resettablePL":"-61.4188","financing":"-1.6503","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-40.7466","resettablePL":"-40.7466","financing":"-2.8272","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-102.1654","resettablePL":"-102.1654","financing":"-4.4775","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_HKD","long":{"units":"0","pl":"-0.1508","resettablePL":"-0.1508","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.1943","resettablePL":"-0.1943","financing":"-0.0248","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.3451","resettablePL":"-0.3451","financing":"-0.0248","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CZK","long":{"units":"0","pl":"-0.2881","resettablePL":"-0.2881","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.2881","resettablePL":"-0.2881","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_DKK","long":{"units":"0","pl":"-53.0322","resettablePL":"-53.0322","financing":"-0.5593","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-20.9816","resettablePL":"-20.9816","financing":"-2.2671","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-74.0138","resettablePL":"-74.0138","financing":"-2.8264","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_HUF","long":{"units":"0","pl":"0.2837","resettablePL":"0.2837","financing":"-0.0849","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.0478","resettablePL":"-2.0478","financing":"-0.0241","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-1.7641","resettablePL":"-1.7641","financing":"-0.1090","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_MXN","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.0111","resettablePL":"-0.0111","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0111","resettablePL":"-0.0111","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_NOK","long":{"units":"0","pl":"0.6285","resettablePL":"0.6285","financing":"-0.0007","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.6789","resettablePL":"-0.6789","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0504","resettablePL":"-0.0504","financing":"-0.0007","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_PLN","long":{"units":"0","pl":"1.4411","resettablePL":"1.4411","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.1391","resettablePL":"0.1391","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"1.5802","resettablePL":"1.5802","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_SEK","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.9250","resettablePL":"-0.9250","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.9250","resettablePL":"-0.9250","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_THB","long":{"units":"0","pl":"-0.0610","resettablePL":"-0.0610","financing":"-1.6071","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0610","resettablePL":"-0.0610","financing":"-1.6071","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_ZAR","long":{"units":"0","pl":"0.6501","resettablePL":"0.6501","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.6501","resettablePL":"0.6501","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"USD_CNH","long":{"units":"0","pl":"-1.0907","resettablePL":"-1.0907","financing":"-0.1736","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.4146","resettablePL":"-2.4146","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-3.5053","resettablePL":"-3.5053","financing":"-0.1736","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_USD","long":{"units":"0","pl":"-26.7154","resettablePL":"-26.7154","financing":"-2.0325","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-100.0905","resettablePL":"-100.0905","financing":"-1.0740","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-126.8059","resettablePL":"-126.8059","financing":"-3.1065","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_AUD","long":{"units":"0","pl":"6.4649","resettablePL":"6.4649","financing":"-0.0692","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.7514","resettablePL":"-2.7514","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"3.7135","resettablePL":"3.7135","financing":"-0.0692","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_CAD","long":{"units":"0","pl":"4.6457","resettablePL":"4.6457","financing":"-1.1263","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-146.7084","resettablePL":"-146.7084","financing":"-0.6562","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-142.0627","resettablePL":"-142.0627","financing":"-1.7825","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_SGD","long":{"units":"0","pl":"1.1370","resettablePL":"1.1370","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"1.1370","resettablePL":"1.1370","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_CHF","long":{"units":"0","pl":"-2.4277","resettablePL":"-2.4277","financing":"0.0021","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"5.0782","resettablePL":"5.0782","financing":"-0.2934","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"2.6505","resettablePL":"2.6505","financing":"-0.2913","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_JPY","long":{"units":"0","pl":"-4.5068","resettablePL":"-4.5068","financing":"-0.4505","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"13.3955","resettablePL":"13.3955","financing":"-0.5125","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"8.8887","resettablePL":"8.8887","financing":"-0.9630","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_NZD","long":{"units":"0","pl":"-35.2216","resettablePL":"-35.2216","financing":"-0.2806","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.8096","resettablePL":"0.8096","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-34.4120","resettablePL":"-34.4120","financing":"-0.2806","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_HKD","long":{"units":"0","pl":"-0.0787","resettablePL":"-0.0787","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.0787","resettablePL":"-0.0787","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"GBP_PLN","long":{"units":"0","pl":"-0.4538","resettablePL":"-0.4538","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.4538","resettablePL":"-0.4538","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_USD","long":{"units":"0","pl":"2.2305","resettablePL":"2.2305","financing":"-20.6369","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-40.4516","resettablePL":"-40.4516","financing":"-1.1155","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-38.2211","resettablePL":"-38.2211","financing":"-21.7524","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_CAD","long":{"units":"0","pl":"-5.3843","resettablePL":"-5.3843","financing":"-0.7939","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-29.6584","resettablePL":"-29.6584","financing":"-0.6593","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-35.0427","resettablePL":"-35.0427","financing":"-1.4532","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_SGD","long":{"units":"0","pl":"6.7216","resettablePL":"6.7216","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-15.6344","resettablePL":"-15.6344","financing":"-0.4213","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-8.9128","resettablePL":"-8.9128","financing":"-0.4213","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_CHF","long":{"units":"0","pl":"-8.6973","resettablePL":"-8.6973","financing":"-0.1334","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"1.3753","resettablePL":"1.3753","financing":"-0.7859","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-7.3220","resettablePL":"-7.3220","financing":"-0.9193","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_JPY","long":{"units":"0","pl":"-5.2695","resettablePL":"-5.2695","financing":"0.8279","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-2.0777","resettablePL":"-2.0777","financing":"-0.4010","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-7.3472","resettablePL":"-7.3472","financing":"0.4269","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_NZD","long":{"units":"0","pl":"-31.5047","resettablePL":"-31.5047","financing":"-2.6889","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-30.5507","resettablePL":"-30.5507","financing":"-0.7887","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-62.0554","resettablePL":"-62.0554","financing":"-3.4776","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"AUD_HKD","long":{"units":"0","pl":"0.4730","resettablePL":"0.4730","financing":"-0.0274","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.4730","resettablePL":"0.4730","financing":"-0.0274","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_SGD","long":{"units":"0","pl":"-0.3073","resettablePL":"-0.3073","financing":"-0.2687","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-1.9233","resettablePL":"-1.9233","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-2.2306","resettablePL":"-2.2306","financing":"-0.2687","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_CHF","long":{"units":"0","pl":"-3.2894","resettablePL":"-3.2894","financing":"-0.2226","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"3.1696","resettablePL":"3.1696","financing":"-0.4184","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-0.1198","resettablePL":"-0.1198","financing":"-0.6410","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_JPY","long":{"units":"0","pl":"23.4269","resettablePL":"23.4269","financing":"1.1442","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-3.8699","resettablePL":"-3.8699","financing":"-0.1910","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"19.5570","resettablePL":"19.5570","financing":"0.9532","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CAD_HKD","long":{"units":"0","pl":"-1.4358","resettablePL":"-1.4358","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.1855","resettablePL":"0.1855","financing":"-0.0288","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-1.2503","resettablePL":"-1.2503","financing":"-0.0288","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"SGD_CHF","long":{"units":"0","pl":"1.1939","resettablePL":"1.1939","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-0.3149","resettablePL":"-0.3149","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.8790","resettablePL":"0.8790","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"SGD_JPY","long":{"units":"0","pl":"0.6183","resettablePL":"0.6183","financing":"-0.0298","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"2.4439","resettablePL":"2.4439","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"3.0622","resettablePL":"3.0622","financing":"-0.0298","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CHF_JPY","long":{"units":"0","pl":"-14.0766","resettablePL":"-14.0766","financing":"-0.8451","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-12.4328","resettablePL":"-12.4328","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-26.5094","resettablePL":"-26.5094","financing":"-0.8451","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"CHF_ZAR","long":{"units":"0","pl":"0.0000","resettablePL":"0.0000","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.0368","resettablePL":"0.0368","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.0368","resettablePL":"0.0368","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_USD","long":{"units":"0","pl":"27.3858","resettablePL":"27.3858","financing":"-2.9941","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-20.9432","resettablePL":"-20.9432","financing":"-1.5916","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"6.4426","resettablePL":"6.4426","financing":"-4.5857","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_CAD","long":{"units":"0","pl":"-30.6872","resettablePL":"-30.6872","financing":"-0.6426","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-87.9107","resettablePL":"-87.9107","financing":"-1.2246","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-118.5979","resettablePL":"-118.5979","financing":"-1.8672","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_SGD","long":{"units":"0","pl":"-4.6931","resettablePL":"-4.6931","financing":"-0.1432","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-28.1942","resettablePL":"-28.1942","financing":"-0.3660","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-32.8873","resettablePL":"-32.8873","financing":"-0.5092","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_CHF","long":{"units":"0","pl":"4.2906","resettablePL":"4.2906","financing":"-0.0461","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"3.1981","resettablePL":"3.1981","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"7.4887","resettablePL":"7.4887","financing":"-0.0461","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"NZD_JPY","long":{"units":"0","pl":"-19.9012","resettablePL":"-19.9012","financing":"-0.0282","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-34.3361","resettablePL":"-34.3361","financing":"-1.3386","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-54.2373","resettablePL":"-54.2373","financing":"-1.3668","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"HKD_JPY","long":{"units":"0","pl":"-0.2144","resettablePL":"-0.2144","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"0.5035","resettablePL":"0.5035","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"0.2891","resettablePL":"0.2891","financing":"0.0000","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"TRY_JPY","long":{"units":"0","pl":"-1.3566","resettablePL":"-1.3566","financing":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-3.3099","resettablePL":"-3.3099","financing":"-0.9629","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-4.6665","resettablePL":"-4.6665","financing":"-0.9629","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},{"instrument":"ZAR_JPY","long":{"units":"0","pl":"-2.8109","resettablePL":"-2.8109","financing":"0.0155","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"short":{"units":"0","pl":"-1.4814","resettablePL":"-1.4814","financing":"-0.0931","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"},"pl":"-4.2923","resettablePL":"-4.2923","financing":"-0.0776","commission":"0.0000","dividendAdjustment":"0.0000","guaranteedExecutionFees":"0.0000","unrealizedPL":"0.0000"}],"trades":[],"unrealizedPL":"0.0000","NAV":"0.6477","marginUsed":"0.0000","marginAvailable":"0.6477","positionValue":"0.0000","marginCloseoutUnrealizedPL":"0.0000","marginCloseoutNAV":"0.6477","marginCloseoutMarginUsed":"0.0000","marginCloseoutPositionValue":"0.0000","marginCloseoutPercent":"0.00000","withdrawalLimit":"0.6477","marginCallMarginUsed":"0.0000","marginCallPercent":"0.00000"},"lastTransactionID":"1682519
//        for (JsonNode ac :res) {
//                if (ac.has("account")) {
//                    account.setLastTransactionID(String.valueOf(ac.get("lastTransactionID").asLong()));
//                    account.setResettablePL(ac.get("resettablePL").asDouble());
//                    account.setFinancing(ac.get("financing").asDouble());
//                    account.setAccountID(ac.get("accountID").asText());
//                    account.setCreatedTime(ac.get("createdTime").asText());
//                    account.setAlias(ac.get("alias").asText());
//                    account.setCurrency(ac.get("currency").asText());
//                    account.setBalance(ac.get("balance").asDouble());
//                    account.setCommission(ac.get("commission").asDouble());
//                    account.setMarginUsed(ac.get("marginUsed").asText());
//                    account.setGuaranteedExecutionFees(ac.get("guaranteedExecutionFees").asText());
//                    account.setPl(ac.get("pl").asText());
//                    account.setId(ac.get("id").asText());
//                    account.setAvailable(ac.get("available").asDouble());
//                    account.setRequireSelfTradePrevention(ac.get("requireSelfTradePrevention").asBoolean());
//                    account.setUpdateTime(ac.get("updateTime").asLong());
//                    if (ac.has("equity")) account.setEquity(ac.get("equity").asDouble());
//                    if (ac.has("nav")) account.setNAV(ac.get("nav").asDouble());
//                    if (ac.has("initialUnits")) account.setInitialUnits(ac.get("initialUnits").asDouble());
//                    if (ac.has("currentUnits")) account.setCurrentUnits(ac.get("currentUnits").asDouble());
//                    if (ac.has("units")) account.setUnits(ac.get("units").asDouble());
//                    if (ac.has("initialMarginRequired")) account.setInitialMarginRequired(ac.get("initialMarginRequired").asDouble());
//                    if (ac.has("state")) account.setState(ac.get("state").asText());
//                    if (ac.has("openTime")) account.setOpenTime(ac.get("openTime").asText());
//                    if (ac.has("closeTime")) account.setCloseTime(ac.get("closeTime").asText());
//                    if (ac.has("frozen")) account.setFrozen(ac.get("frozen").asDouble());
//                    if (ac.has("permissions")) account.setPermissions(ac.get("permissions").asText());
//                    if (ac.has("pl")) account.setPl(ac.get("pl").asText());
//                    if (ac.has("created")) account.setCreated(LocalDateTime.parse(ac.get("created").asText()));
//                    if (ac.has("ready")) account.setReady(ac.get("ready").asBoolean());
//                    if (ac.has("commissionRates")) {
//                        double[] commissionRates = new double[ac.get("commissionRates").size()];
//                        for (int i = 0; i < ac.get("commissionRates").size(); i++) {
//                            commissionRates[i] = ac.get("commissionRates").get(i).asDouble();
//                        }
//                        account.setCommissionRates(commissionRates);
//                        account.setCommissionRates(commissionRates[0], commissionRates[1], commissionRates[2], commissionRates[3]);
//                    }
//                    if (ac.has("accountType")) account.setAccountType(ac.get("accountType").asText());
//                    // Add more fields as needed
        //   return account;
        //}
        //}


    }

    @Override
    public Boolean isConnected() {
        return null;
    }


    @Override
    public String getSymbol() {
        return tradePair.toString('-');
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(STR."\{url}/orders"));

        requestBuilder.method("POST",
                HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(new CreateOrderRequest(tradePair.toString('-'), side, orderType, price, size, timestamp, stopLoss, takeProfit))));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            logger.info("Order created: {}", response.body());
        } else {
            logger.error("Failed to create order: {}", response.body());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error creating order");
            alert.setHeaderText("Failed to create order");
            alert.setContentText(response.body());
            alert.showAndWait();

        }

    }


    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException {
        Objects.requireNonNull(orderId);

        String uriStr = url + STR."/orders/\{orderId}";

        requestBuilder.uri(URI.create(uriStr)).DELETE().build();
        HttpRequest.BodyPublisher body =HttpRequest.BodyPublishers.ofString("");
        HttpResponse<String> response = client.send(requestBuilder.POST(body).build(), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<String> futureResult = new CompletableFuture<>();
        if (response.statusCode() == 204) {
            logger.info("Order canceled: {}", orderId);
            futureResult.complete(orderId);
        } else {
            logger.error("Failed to cancel order: {}", orderId);
            futureResult.completeExceptionally(new RuntimeException("Failed to cancel order: %s".formatted(response.body())));
        }
        return futureResult;
    }

    @Override
    public String getExchangeMessage() {

        return message;
    }

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        if (stopAt.isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        // It is not easy to fetch trades concurrently because we need to get the "cb-after" header after each request.
        CompletableFuture.runAsync(() -> {
            IntegerProperty afterCursor = new SimpleIntegerProperty(0);
            List<Trade> tradesBeforeStopTime = new ArrayList<>();

            // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
            // burst.
            // We will know if we get rate limited if we get a 429 response code.
            for (int i = 0; !futureResult.isDone(); i++) {
                String uriStr = url;
                uriStr += STR."accounts/\{account_id}/trades?instrument=\{tradePair.toString('_')}";

                if (i != 0) {
                    uriStr += STR."?after=\{afterCursor.get()}";
                    logger.info(STR."uriStr: \{uriStr}");
                }

                try {
                    HttpResponse<String> response = client.send(
                            requestBuilder.uri(URI.create(uriStr))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString());

                    logger.info(STR."trades response: \{response}");
                    if (response.headers().firstValue("time").isEmpty()) {
                        logger.error("Exception", STR." trades response did not contain header \"cb-after\": \{response}");
                        futureResult.completeExceptionally(new RuntimeException());
                        //  trades response did not contain header \"cb-after\": \{response}"));
                        return;
                    }

                    afterCursor.setValue(Integer.valueOf((response.headers().firstValue("time").get())));
                    JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());

                    if (!tradesResponse.isArray()) {
                        futureResult.completeExceptionally(new RuntimeException(

                                " trades response was not an array!"));

                    }
                    if (tradesResponse.isEmpty()) {
                        futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));


                    } else {
                        for (int j = 0; j < tradesResponse.size(); j++) {
                            JsonNode trade = tradesResponse.get(j);
                            Instant time = Instant.from(ISO_INSTANT.parse(trade.get("time").asText()));
                            if (time.compareTo(stopAt) <= 0) {
                                futureResult.complete(tradesBeforeStopTime);
                                break;
                            } else {
                                tradesBeforeStopTime.add(new Trade(tradePair,
                                        DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                        DefaultMoney.ofCrypto(trade.get("size").asText(), tradePair.getBaseCurrency()),
                                        Side.getSide(trade.get("side").asText()), trade.get("trade_id").asLong(), time));
                                logger.info(STR."tradesBeforeStopTime: \{tradesBeforeStopTime}");
                                logger.info(STR."time: \{time}");
                                logger.info(STR."price: \{trade.get("price").asText()}");
                                logger.info(STR."size: \{trade.get("size").asText()}");
                            }
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    logger.error("ex: ", ex);
                    futureResult.completeExceptionally(ex);
                }
            }
        });

        return futureResult;
    }


    /**
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString;
        startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        // Get the closest supported granularity to the ideal granularity.
        int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair).getSupportedGranularity().stream()
                .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
                .orElseThrow(() -> new NoSuchElementException("Supported granularity was empty!"));

        return client.sendAsync(
                        requestBuilder
                                .uri(URI.create(String.format(
                                        STR."\{url}/instruments/\{tradePair.toString('_')}/candles", actualGranularity, startDateString)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response1 -> {
                    logger.info(STR."candles response: \{response1}");

                    if (response1 == null || response1.isEmpty()) {
                        logger.error("ERROR", STR."\{tradePair.toString('_')} candles response was empty");
                        return Optional.empty();
                    }

                    try {
                        res = OBJECT_MAPPER.readTree(response1);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    if (res.has("message") && res.get("message").asText().equals("not_found")) {

                        logger.error("ERROR", tradePair.toString('-'));
                    }

                    JsonNode currCandle;
                    Iterator<JsonNode> candleItr = res.iterator();
                    logger.info(STR."res :\{res.toString()}");
                    int currentTill = -1;
                    double openPrice = -1;
                    double highSoFar = -1;
                    double lowSoFar = Double.MAX_VALUE;
                    double volumeSoFar = 0;
                    double lastTradePrice = -1;
                    boolean foundFirst = false;
                    while (candleItr.hasNext()) {
                        currCandle = candleItr.next();
                        if (currCandle.get(0).asInt() < currentCandleStartedAt.getEpochSecond() ||
                                currCandle.get(0).asInt() >= currentCandleStartedAt.getEpochSecond() +
                                        secondsPerCandle) {
                            logger.info(STR."currCandle: \{currCandle}");
                            continue;
                        } else {
                            if (!foundFirst) {
                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;
                            } else if (currCandle.get(4).asDouble() > lastTradePrice) {

                                currentTill = currCandle.get(0).asInt();
                            }
                        }

                        openPrice = currCandle.get(3).asDouble();

                        if (currCandle.get(2).asDouble() > highSoFar) {
                            highSoFar = currCandle.get(2).asDouble();
                        }

                        if (currCandle.get(1).asDouble() < lowSoFar) {
                            lowSoFar = currCandle.get(1).asDouble();
                        }

                        volumeSoFar += currCandle.get(5).asDouble();
                    }

                    int openTime = (int) (currentCandleStartedAt.toEpochMilli() / 1000L);

                    return Optional.of(new InProgressCandleData(openTime, openPrice, highSoFar, lowSoFar,
                            currentTill, lastTradePrice, volumeSoFar));
                });

    }

    @Override
    public List<Order> getPendingOrders() throws IOException {

        requestBuilder.uri(URI.create(STR."https://api-fxtrade.oanda.com/v3/accounts/\{account_id}/pendingOrders"));
        requestBuilder.GET();
        HttpResponse<String> response;
        try {
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        ArrayList<Order> ordersList = new ArrayList<>();
        if (response.statusCode() != 200) {
            logger.error(response.body(), response.statusCode());
            return ordersList;
        }
        try {
            res = OBJECT_MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Order order = OBJECT_MAPPER.readTree(res.traverse());
        ordersList.add(order);
        return ordersList;

    }


    @Override
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair);
        String uriStr = url + STR."/products/\{tradePair.toString('-')}/book?level=1";
        requestBuilder.uri(URI.create(uriStr)).GET().build();
        return client.sendAsync(requestBuilder.uri(
                        URI.create(uriStr)
                ).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public Ticker getLivePrice(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        String uriStr = STR."\{url}/instruments/\{tradePair.toString('-')}/ticker";

        requestBuilder.uri(URI.create(uriStr));
        try {
            res = OBJECT_MAPPER.readTree(client
                    .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get(5000, TimeUnit.MILLISECONDS).body());

            Ticker ticker = new Ticker();

            for (JsonNode rate : res) {
                ticker.setBidPrice(rate.get("bid").asDouble());
                ticker.setAskPrice(rate.get("ask").asDouble());
                ticker.setVolume(rate.get("volume").asDouble());
                ticker.setTimestamp(rate.get("timestamp").asLong() * 1000L);
                logger.info(
                        "bid: {ticker.getBidPrice()}, ask: {ticker.getAskPrice()}, volume: {ticker.getVolume()}, timestamp: {ticker.getTimestamp()}"
                );
            }
            return ticker;

        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public JsonParser getUserAccountDetails() {
        String uriStr = STR."\{url}/accounts/\{account_id}";
        requestBuilder.uri(URI.create(uriStr));
        try {
            JsonNode re = OBJECT_MAPPER.readTree(client
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .body());

            logger.info(re.toString());
            return re.traverse();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }


    }

    // Existing code


    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    @Override
    public void getPositionBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        String uriStr = url + STR."/accounts/\{account_id}/positions";

        requestBuilder.uri(URI.create(uriStr))

                .build();

        try {
            res = OBJECT_MAPPER.readTree(client
                    .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get(5000, TimeUnit.MILLISECONDS).body());

            for (JsonNode position : res) {
                logger.info(
                        "long: {position.get(\"longPositionSize\").asDouble()}, short: {position.get(\"shortPositionSize\").asDouble()}"
                );
            }
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) {


        Objects.requireNonNull(tradePair);

        String uriStr = url + STR."/accounts/\{account_id}/orders?instrument=" + tradePair.toString('_') + "&state=OPEN";
        requestBuilder.uri(URI.create(uriStr));
        ArrayList<Order> orders = new ArrayList<>();
        try {
            res = OBJECT_MAPPER.readTree(client
                    .sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get(5000, TimeUnit.MILLISECONDS).body());

            Order order = OBJECT_MAPPER.readValue(res.traverse(), Order.class);
            logger.info("Order ID: {order.getId()}, State: {order.getState()}, Price: {order.getPrice()}, Quantity: {order.getQuantity()}, Side: {order.getSide()}"
            );
            orders.add(order);


        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        return orders;
    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException {

        String uriStr = url + STR."/accounts/\{account_id}/orders";

        requestBuilder.uri(URI.create(uriStr));

        ObservableList<Order> pendingOrders;
        String data0 = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).body();

        if (data0.contains("errorMessage") || data0.contains("error") || data0.charAt(0) == '{') {
            logger.info(data0);
            return FXCollections.observableArrayList();  // return an empty list if an error message is found
        }
        JsonNode response = OBJECT_MAPPER.readTree(data0);

        Order order = OBJECT_MAPPER.readValue(response.traverse(), Order.class);
        pendingOrders = FXCollections.observableArrayList();

//            for (JsonNode js : res) {
//                if (js.get("state").asText().equals("pending")) {
//                    Order order = new Order();
//                    order.setOrderId(js.get("id").asText());
//                    order.setPrice(js.get("price").asDouble());
//                    order.setQuantity(js.get("quantity").asDouble());
//                    order.setSide(js.get("side").asText().equals("buy")? Side.BUY : Side.SELL);
//                    order.setOrderId(js.get("id").asText());
//                    order.setInstrument( js.get("instrument").asText());
        pendingOrders.add(order);
        logger.info(STR."orders \{pendingOrders}");
        return FXCollections.observableArrayList(pendingOrders);
    }
    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() {

        String uriStr = "https://api-fxtrade.oanda.com/v3/accounts/001-001-2783446-006/instruments";

//        Access-Control-Allow-Headers: Authorization, Content-Type, Accept-Datetime-Format
//        Content-Encoding: gzip
//        Transfer-Encoding: chunked
//        Server: openresty/1.7.0.1
//        Connection: keep-alive
//        Date: Wed, 22 Jun 2016 18:32:01 GMT
//        Access-Control-Allow-Origin: *
//        Access-Control-Allow-Methods: PUT, PATCH, POST, GET, OPTIONS, DELETE
//        Content-Type: application/json
     requestBuilder
                .header("Content-Type", "application/json")
                .header("Accept-Datetime-Format", "UNIX")
                .header("X-Ratelimit-Limit", "500")
                .header("X-Ratelimit-Remaining", "499")
                .header("X-Ratelimit-Reset", "1466561521")
                .header("User-Agent", "java-oanda-v3-sample/1.0.0");
             //header("Authorization", "Bearer 8bf45b37af06b42c5ee42adb4525f339-975adff698b1158504abc2c216e450f5")



        requestBuilder.uri(URI.create(uriStr));

        // Asynchronously send the request and handle the response
        return client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> {
                    ArrayList<TradePair> instrumentsList = new ArrayList<>();

                    if (response.statusCode() != 200) {
                        // Log error if response status is not 200 OK
                        logger.info("Error fetching instruments: %s".formatted(response.body()));
                        return instrumentsList;
                    }

                    // Process the response body
                    JsonNode jsonNode;
                    try {
                        jsonNode = OBJECT_MAPPER.readTree(response.body());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    JsonNode instrumentsNode = jsonNode.get("instruments");
                    logger.info("Instruments:  %s".formatted(instrumentsNode.toString()));

                    // Loop through the instruments and add them to the list
                    for (JsonNode instrumentNode : instrumentsNode) {
                        if (instrumentNode != null) {
                            TradePair instrument;
                            try {
                                instrument = new TradePair(instrumentNode.get("displayName").asText().split("/")[0],
                                        instrumentNode.get("displayName").asText().split("/")[1]);
                            } catch (SQLException | ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                            instrumentsList.add(instrument);
                        }
                    }

                    // Register fiat currencies asynchronously if needed
                    try {
                        new FiatCurrencyDataProvider().registerCurrencies();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    return instrumentsList;
                })
                .exceptionally(ex -> {
                    // Log any exceptions that occur during the process
                    logger.error("Error occurred during fetching trade pairs: ", ex);
                    return new ArrayList<>();  // Return an empty list in case of error
                });
    }


    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}