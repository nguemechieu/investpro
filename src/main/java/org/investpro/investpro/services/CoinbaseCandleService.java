package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Getter
@Setter
public class CoinbaseCandleService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_PAGE_SIZE = 300;
    private static final Set<Integer> SUPPORTED_GRANULARITIES = Set.of(60, 300, 900, 3600, 21600, 86400);

    private String apiKey;
    private String apiSecret;
    private HttpClient httpClient;

    public CoinbaseCandleService(String apiKey, String apiSecret, HttpClient httpClient, int i) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public Optional<?> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) throws ExecutionException, InterruptedException {
        Instant requestStart = currentCandleStartedAt.minusSeconds(secondsPerCandle);
        Instant requestEnd = Instant.now();

        try {
            List<CandleData> candles = fetchCandlesAsync(
                    tradePair.toString('-'),
                    requestStart,
                    requestEnd,
                    secondsPerCandle
            ).get();

            return candles.stream()
                    .filter(candle -> candle.getOpenTime() >= currentCandleStartedAt.getEpochSecond())
                    .max(Comparator.comparingInt(CandleData::getOpenTime))
                    .map(candle -> (Object) candle);
        } catch (ExecutionException e) {
            logger.error("Error fetching in-progress candle data for {}", tradePair, e);
            return Optional.empty();
        }
    }

    public List<CandleData> getHistoricalCandles(String symbol, Instant startTime, Instant endTime, int interval) {
        try {
            return fetchCandlesAsync(symbol, startTime, endTime, interval).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching historical candles for {}", symbol, e);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            logger.error("Unable to fetch historical candles for {}", symbol, e);
            return Collections.emptyList();
        }
    }

    @Getter
    @Setter
    public static class CoinbaseCandleDataSupplier extends CandleDataSupplier {

        private final int secondsPerCandle;
        private final HttpClient httpClient;

        public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair, HttpClient httpClient) {
            super(secondsPerCandle, tradePair);
            this.secondsPerCandle = secondsPerCandle;
            this.tradePair = tradePair;
            this.httpClient = httpClient;
        }

        @Override
        public Set<Integer> getSupportedGranularity() {
            return SUPPORTED_GRANULARITIES;
        }

        @Override
        public Future<List<CandleData>> get() {
            try {
                if (endTime.get() == -1) {
                    endTime.set((int) Instant.now().getEpochSecond());
                }

                int normalizedGranularity = normalizeGranularity(secondsPerCandle);
                int requestEnd = Math.max(endTime.get(), normalizedGranularity);
                int requestStart = Math.max(requestEnd - (normalizedGranularity * DEFAULT_PAGE_SIZE), 0);
                String url = buildCandlesUrl(
                        tradePair.toString('-'),
                        Instant.ofEpochSecond(requestStart),
                        Instant.ofEpochSecond(requestEnd),
                        normalizedGranularity
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("User-Agent", "InvestPro")
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build();

                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> parseCandles(response.body(), tradePair.toString('-')))
                        .thenApply(candles -> {
                            if (!candles.isEmpty()) {
                                endTime.set(Math.max(candles.getFirst().getOpenTime() - normalizedGranularity, normalizedGranularity));
                            }
                            return candles;
                        });
            } catch (Exception e) {
                logger.error("Error fetching Coinbase candle data for {}", tradePair, e);
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
        }
    }

    public CoinbaseCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair, httpClient);
    }

    private CompletableFuture<List<CandleData>> fetchCandlesAsync(
            String symbol,
            Instant startTime,
            Instant endTime,
            int secondsPerCandle
    ) {
        int normalizedGranularity = normalizeGranularity(secondsPerCandle);
        String url = buildCandlesUrl(symbol, startTime, endTime, normalizedGranularity);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseCandles(response.body(), symbol))
                .exceptionally(throwable -> {
                    logger.error("Failed to fetch candles for {}", symbol, throwable);
                    return Collections.emptyList();
                });
    }

    private static int normalizeGranularity(int secondsPerCandle) {
        if (SUPPORTED_GRANULARITIES.contains(secondsPerCandle)) {
            return secondsPerCandle;
        }
        return 3600;
    }

    private static String buildCandlesUrl(String symbol, Instant startTime, Instant endTime, int secondsPerCandle) {
        return String.format(
                "%s/products/%s/candles?granularity=%d&start=%s&end=%s",
                Coinbase.API_URL,
                symbol,
                secondsPerCandle,
                DateTimeFormatter.ISO_INSTANT.format(startTime),
                DateTimeFormatter.ISO_INSTANT.format(endTime)
        );
    }

    private static List<CandleData> parseCandles(String responseBody, String symbol) {
        try {
            JsonNode response = OBJECT_MAPPER.readTree(responseBody);
            if (!response.isArray() || response.isEmpty()) {
                return Collections.emptyList();
            }

            List<CandleData> candles = new ArrayList<>();
            for (JsonNode node : response) {
                if (!node.isArray() || node.size() < 6) {
                    continue;
                }

                candles.add(new CandleData(
                        node.get(3).asDouble(),
                        node.get(4).asDouble(),
                        node.get(2).asDouble(),
                        node.get(1).asDouble(),
                        node.get(0).asLong(),
                        node.get(5).asDouble()
                ));
            }

            candles.sort(Comparator.comparingInt(CandleData::getOpenTime));
            return candles;
        } catch (Exception e) {
            logger.error("Failed to parse Coinbase candle response for {}", symbol, e);
            return Collections.emptyList();
        }
    }
}
