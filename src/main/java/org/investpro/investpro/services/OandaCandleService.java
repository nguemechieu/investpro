package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.components.OandaCandleDataSupplier;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.models.CandleData;
import org.investpro.investpro.models.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

public record OandaCandleService(@NotNull String accountId, @NotNull String apiSecret, @NotNull HttpClient client,
                                 int maxCandle) {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<Integer, String> GRANULARITY_MAP = Map.of(
            60, "M1",
            300, "M5",
            900, "M15",
            1800, "M30",
            3600, "H1",
            14400, "H4",
            86400, "D",
            604800, "W"
    );

    public OandaCandleService {
        logger.debug("OANDA candle service initialized");
    }

    @Contract("_, _ -> new")
    public @NotNull CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        Objects.requireNonNull(tradePair);
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiSecret, client);
    }

    public List<CandleData> getHistoricalCandles(TradePair tradePair, Instant startTime, Instant endTime, int interval) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");

        String url = String.format(
                "%s/instruments/%s/candles?granularity=%s&price=M&from=%s&to=%s",
                Oanda.API_URL,
                tradePair.toString('_'),
                normalizeGranularity(interval),
                DateTimeFormatter.ISO_INSTANT.format(startTime),
                DateTimeFormatter.ISO_INSTANT.format(endTime)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Unable to fetch OANDA historical candles for {}. HTTP {}: {}", tradePair, response.statusCode(), response.body());
                return List.of();
            }

            JsonNode candlesNode = OBJECT_MAPPER.readTree(response.body()).path("candles");
            if (!candlesNode.isArray()) {
                return List.of();
            }

            List<CandleData> candles = new ArrayList<>();
            for (JsonNode candleNode : candlesNode) {
                JsonNode midNode = candleNode.path("mid");
                if (midNode.isMissingNode() || midNode.isEmpty()) {
                    continue;
                }

                candles.add(new CandleData(
                        midNode.path("o").asDouble(),
                        midNode.path("c").asDouble(),
                        midNode.path("h").asDouble(),
                        midNode.path("l").asDouble(),
                        Instant.parse(candleNode.path("time").asText()).getEpochSecond(),
                        candleNode.path("volume").asDouble()
                ));
            }

            candles.sort(Comparator.comparingInt(CandleData::getOpenTime));
            return candles;
        } catch (Exception e) {
            logger.error("Unable to fetch OANDA historical candles for {}", tradePair, e);
            return List.of();
        }
    }

    public Optional<CandleData> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle
    ) {
        Instant requestStart = currentCandleStartedAt.minusSeconds(secondsPerCandle);
        Instant requestEnd = currentCandleStartedAt.plusSeconds(Math.max(secondsIntoCurrentCandle, secondsPerCandle));

        return getHistoricalCandles(tradePair, requestStart, requestEnd, secondsPerCandle).stream()
                .filter(candle -> candle.getOpenTime() >= currentCandleStartedAt.getEpochSecond())
                .max(Comparator.comparingInt(CandleData::getOpenTime));
    }

    private String normalizeGranularity(int interval) {
        return GRANULARITY_MAP.getOrDefault(interval, "H1");
    }
}
