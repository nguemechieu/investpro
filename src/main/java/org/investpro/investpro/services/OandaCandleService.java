package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.components.OandaCandleDataSupplier;
import org.investpro.investpro.exchanges.Oanda;


import org.investpro.investpro.model.TradePair;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public record OandaCandleService(@NotNull String accountId, @NotNull String apiSecret, @NotNull HttpClient client,
                                 int max_candle) {


    private static final Logger logger = LoggerFactory.getLogger(OandaCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Contract("_, _ -> new")
    public @NotNull CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        Objects.requireNonNull(tradePair);
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair);
    }

    public List<CandleData> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant startTime, Instant endTime, int secondsPerCandle) {


        List<CandleData> collected = new ArrayList<>();
        CompletableFuture<List<CandleData>> res = fetchPaginated(tradePair, startTime, endTime, secondsPerCandle, collected);


        try {
            return res.get().stream().toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private @NotNull CompletableFuture<List<CandleData>> fetchPaginated(@NotNull TradePair tradePair, Instant startDate, Instant endDate,

                                                                        int secondsPerCandle, List<CandleData> acc) {
        String url = "%s/instruments/%s/candles?granularity=%s&from=%s&to=%s&count=%s".formatted(
                Oanda.API_URL, tradePair.toString('_'), granularity(secondsPerCandle), DateTimeFormatter.ISO_INSTANT.format(startDate)
                , DateTimeFormatter.ISO_INSTANT.format(endDate), max_candle

        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(resp -> {
                    try {
                        JsonNode root = OBJECT_MAPPER.readTree(resp.body());
                        JsonNode candles = root.get("candles");
                        if (candles == null || !candles.isArray()) return CompletableFuture.completedFuture(acc);

                        for (JsonNode c : candles) {
                            if (!c.has("mid")) continue;
                            long time = Instant.parse(c.get("time").asText()).getEpochSecond();

                            acc.add(new CandleData(

                                    c.get("mid").get("o").asDouble(),
                                    c.get("mid").get("h").asDouble(),
                                    c.get("mid").get("l").asDouble(),
                                    c.get("mid").get("c").asDouble(), time,
                                    c.get("volume").asLong()));

                        }

                        if (candles.size() == max_candle) {
                            // String nextTo = candles.get(candles.size() - 1).get("time").asText();
                            return fetchPaginated(tradePair, startDate, endDate, secondsPerCandle,
                                    acc);
                        }
                        return CompletableFuture.completedFuture(acc);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    @Contract(pure = true)
    private @NotNull String granularity(int seconds) {
        if (seconds < 60) return "S" + seconds;
        if (seconds < 3600) return "M" + (seconds / 60);
        if (seconds < 86400) return "H" + (seconds / 3600);
        if (seconds < 604800) return "D";
        if (seconds > 604800 & seconds < 24 * 3600 * 7 * 4) return "W" + (seconds / 3600 * 24 * 7);
        if (seconds >= 24 * 3600 * 7 * 4) return "Mn";
        return "";
    }


}
