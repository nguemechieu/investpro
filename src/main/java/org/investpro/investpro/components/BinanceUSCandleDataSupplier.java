package org.investpro.investpro.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.investpro.investpro.CandleDataSupplier;

import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class BinanceUSCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSCandleDataSupplier.class);
    private static final String BASE_URL = "https://api.binance.us/api/v3/klines";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Map<Integer, String> GRANULARITY_MAP = Map.of(
            60, "1m",
            180, "3m",
            300, "5m",
            900, "15m",
            1800, "30m",
            3600, "1h",
            14400, "4h",
            86400, "1d"
    );
    private static final long numCandles = 1000;

    public BinanceUSCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(secondsPerCandle, tradePair);
    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        return GRANULARITY_MAP.keySet();
    }

    private String getIntervalLabel(int seconds) {
        return GRANULARITY_MAP.getOrDefault(seconds, "1m");
    }

    @Override
    public Future<List<CandleData>> get() {
        try {
            if (endTime.get() == -1) {
                endTime.set((int) Instant.now().getEpochSecond());
            }

            long endTimeMs = endTime.get() * 1000L;
            long startTimeMs = endTimeMs - ((long) secondsPerCandle * numCandles * 1000L);

            String interval = getIntervalLabel(secondsPerCandle);
            String symbol = tradePair.toString().replace("/", "");
            String url = String.format("%s?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    BASE_URL, symbol, interval, startTimeMs, endTimeMs, numCandles);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        try {
                            JsonNode array = OBJECT_MAPPER.readTree(body);
                            List<CandleData> candles = new ArrayList<>();
                            for (JsonNode node : array) {
                                candles.add(new CandleData(

                                        node.get(1).asDouble(),
                                        node.get(4).asDouble(),
                                        node.get(2).asDouble(),

                                        node.get(3).asDouble(),
                                        node.get(0).asLong(),
                                        node.get(5).asLong()
                                ));
                            }

                            if (!candles.isEmpty()) {
                                endTime.set((int) candles.getLast().getOpenTime());
                            }

                            return candles;
                        } catch (Exception e) {
                            logger.error("Failed to parse Binance US candle data", e);
                            return Collections.emptyList();
                        }
                    });
        } catch (Exception e) {
            logger.error("Error fetching candle data from Binance US", e);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
}
