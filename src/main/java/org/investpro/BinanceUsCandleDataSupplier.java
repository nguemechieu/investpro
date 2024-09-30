package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;

import static org.investpro.Coinbase.client;
import static org.investpro.Coinbase.requestBuilder;
import static org.investpro.Exchange.apiSecret;
import static org.investpro.Exchange.logger;

public     class BinanceUsCandleDataSupplier extends CandleDataSupplier {

        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        private static final String BINANCE_API_URL = "https://api.binance.us/api/v3";

        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Override
        public Set<Integer> getSupportedGranularity() {
            // Binance uses fixed time intervals (1m, 3m, 5m, etc.)
            // Here we map them to seconds
            return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 14400, 86400));
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
            }

            long endTimeMillis = (long) endTime.get() * 1000;
            long startTimeMillis = Math.max(endTimeMillis - (numCandles * secondsPerCandle * 1000L), 1422144000000L); // earliest timestamp

            // Binance uses string intervals for granularity like "1m", "5m", etc.
            String interval = getBinanceInterval(secondsPerCandle);

            // Construct the URL
            String url = String.format("%s/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    BINANCE_API_URL, tradePair.toString('/'), interval, startTimeMillis, endTimeMillis, numCandles);
            logger.info("Fetching candle data for trade pair: {} from {} to {}", tradePair.toString('/'), startTimeMillis, endTimeMillis);
            Instant timestamp = Instant.now();
            logger.info("Fetching candle data took: {} seconds", Duration.between(timestamp, Instant.now()).toSeconds());
            String signature;
            try {
                 signature = BinanceUtils.generateSignature(String.valueOf(timestamp), apiSecret);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            requestBuilder.setHeader("Content-Type", "application/");
            requestBuilder.setHeader("X-MBX-APIKEY", apiSecret);
            HashMap<String, String>data0= new HashMap<>();
            data0.put("timestamp", String.valueOf(timestamp));
            data0.put("signature", signature);
            requestBuilder.uri(URI.create(url));
            requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(String.valueOf(data0)));
           


            return client.sendAsync(
                            requestBuilder.uri(URI.create(url))

                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            logger.error("Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair.toString('/')));

                            new Messages("Error", "Failed to fetch candle data\n%s".formatted(response));
                            throw new RuntimeException("Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair.toString('/')));
                        }

                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response.body());
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {
                            List<CandleData> candleData = new ArrayList<>();
                            for (JsonNode candle : res) {
                                candleData.add(new CandleData(
                                        candle.get(1).asDouble(),  // open price
                                        candle.get(4).asDouble(),  // close price
                                        candle.get(2).asDouble(),  // high price
                                        candle.get(3).asDouble(),  // low price
                                        candle.get(0).asInt() / 1000,  // open time (convert ms to seconds)
                                        candle.get(5).asDouble()   // volume
                                ));
                            }
                            candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                            endTime.set((int) (startTimeMillis / 1000));  // Update endTime for pagination
                            return candleData;
                        } else {
                            logger.info("No candle data found for trade pair: %s".formatted(tradePair));
                            return Collections.emptyList();
                        }
                    });
        }

        // Helper method to convert secondsPerCandle to Binance interval strings
        private @NotNull String getBinanceInterval(int secondsPerCandle) {
            return switch (secondsPerCandle) {
                case 60 -> "1m";
                case 180 -> "3m";
                case 300 -> "5m";
                case 900 -> "15m";
                case 1800 -> "30m";
                case 3600 -> "1h";
                case 14400 -> "4h";
                case 86400 -> "1d";
                default -> throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
            };
        }
    }
