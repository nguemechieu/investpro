package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Getter
@Setter
public class CoinbaseCandleService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String apiKey;
    private String apiSecret;
    private HttpClient httpClient;

    public CoinbaseCandleService(String apiKey, String apiSecret, HttpClient httpClient, int i) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

//    public Optional<?> fetchCandleDataForInProgressCandle(
//            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) throws ExecutionException, InterruptedException {
//
//        String startDateString = DateTimeFormatter.ISO_INSTANT.format(currentCandleStartedAt);
//
//        String url = String.format("%s/products/%s/candles?granularity=%d&start=%s",
//                Coinbase.API_URL,
//                tradePair.toString('-'),
//                secondsPerCandle,
//                startDateString);
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(url))
//                .GET()
//                .build();
//
//        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
//                .thenApply(
//                        HttpResponse::body
//                )
//                .thenApply(response -> {
//                    try {
//                        JsonNode res = OBJECT_MAPPER.readTree(response);
//                        if (res.isEmpty()) return Optional.empty();
//
//                        JsonNode first = res.get(0);
//                        long openTime = first.get(0).asLong();
//                        double low = first.get(1).asDouble();
//                        double high = first.get(2).asDouble();
//                        double open = first.get(3).asDouble();
//                        double close = first.get(4).asDouble();
//                        long volume = first.get(5).asLong();
//
//                        return Optional.of(new CandleData(openTime, open, high, low,
//                                close, volume));
//                    } catch (Exception e) {
//                        logger.error("Error parsing in-progress candle data", e);
//                        return Optional.empty();
//                    }
//                }).get();
//    }

    public List<CandleData> getHistoricalCandles(String symbol, Instant startTime, Instant endTime, int interval) {
        // Placeholder for historical candles (not implemented)
        return Collections.emptyList();
    }

    public CoinbaseCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Getter
    @Setter
    public static class CoinbaseCandleDataSupplier extends CandleDataSupplier {

        private final int secondsPerCandle;

        public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(secondsPerCandle, tradePair);
            this.secondsPerCandle = secondsPerCandle;
            this.tradePair = tradePair;
        }

        @Override
        public Set<Integer> getSupportedGranularity() {

            return Set.of(60, 300, 900, 1800, 3600, 21600, 86400, 604800);
        }

        @Override
        public Future<List<CandleData>> get() {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
