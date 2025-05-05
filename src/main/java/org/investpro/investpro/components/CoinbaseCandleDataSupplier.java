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

public class CoinbaseCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleDataSupplier.class);
    private static final String BASE_URL = "https://api.coinbase.com/v3/products";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Map<Integer, String> GRANULARITY_MAP = Map.of(
            60, "60",      // 1 minute
            300, "300",    // 5 minutes
            900, "900",    // 15 minutes
            3600, "3600",  // 1 hour
            21600, "21600",// 6 hours
            86400, "86400" // 1 day
    );
    int numCandles = 1000;

    public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {

        Objects.requireNonNull(tradePair);
        super(secondsPerCandle, tradePair);
    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        return GRANULARITY_MAP.keySet();
    }

    private String getGranularityLabel(int seconds) {
        return GRANULARITY_MAP.getOrDefault(seconds, "300");
    }

    @Override
    public Future<List<CandleData>> get() {
        try {
            if (endTime.get() == -1) {
                endTime.set((int) Instant.now().getEpochSecond());
            }

            long end = endTime.get();
            long start = Math.max(end - (long) numCandles * secondsPerCandle, 0);

            String symbol = tradePair.toString('-');
            String url = String.format("%s/%s/candles?granularity=%s&start=%s&end=%s",
                    BASE_URL,
                    symbol,
                    getGranularityLabel(secondsPerCandle),
                    Instant.ofEpochSecond(start),
                    Instant.ofEpochSecond(end)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
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

                                        node.get(3).asDouble(),     // open
                                        node.get(4).asDouble(),     // close
                                        node.get(2).asDouble(),     // high
                                        node.get(1).asDouble(),     // low
                                        node.get(0).asLong(),       // time
                                        node.get(5).asLong()        // volume
                                ));
                            }
                            candles.sort(Comparator.comparing(CandleData::getOpenTime)); // ascending
                            return candles;
                        } catch (Exception e) {
                            logger.error("Error parsing candle data: {}", e.getMessage(), e);
                            return Collections.emptyList();
                        }
                    });
        } catch (Exception ex) {
            logger.error("Failed to fetch Coinbase candles", ex);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
}
