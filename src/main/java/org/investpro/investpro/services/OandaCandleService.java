package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.components.OandaCandleDataSupplier;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.Candle;

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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Setter
@Getter
public class OandaCandleService {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String accountId;
    private final String apiSecret;
    private final HttpClient client;

    public OandaCandleService(String accountId, String apiSecret, HttpClient client) {
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.client = client;
    }

    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair);
    }

    public CompletableFuture<Optional<Candle>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant candleStart, long secondsInto, int secondsPerCandle) {
        String startDate = DateTimeFormatter.ISO_INSTANT.format(candleStart);
        List<Candle> collected = new ArrayList<>();

        return fetchPaginated(tradePair, startDate, secondsPerCandle, collected)
                .thenApply(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1)));
    }

    private CompletableFuture<List<Candle>> fetchPaginated(TradePair tradePair, String startDate,
                                                           int secondsPerCandle, List<Candle> acc) {
        String url = "%s/instruments/%s/candles?granularity=%s&to=%s&count=5000".formatted(
                Oanda.API_URL, tradePair.toString('_'), granularity(secondsPerCandle), startDate);

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
                            long open = Instant.parse(c.get("time").asText()).getEpochSecond();
                            Candle data = new Candle(
                                    open,
                                    c.get("mid").get("o").asDouble(),
                                    c.get("mid").get("h").asDouble(),
                                    c.get("mid").get("l").asDouble(),

                                    c.get("mid").get("c").asDouble(),
                                    c.get("volume").asLong()
                            );
                            acc.add(data);
                        }

                        if (candles.size() == 5000) {
                            String nextTo = candles.get(candles.size() - 1).get("time").asText();
                            return fetchPaginated(tradePair, nextTo, secondsPerCandle, acc);
                        }
                        return CompletableFuture.completedFuture(acc);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    private String granularity(int seconds) {
        if (seconds < 60) return "S" + seconds;
        if (seconds < 3600) return "M" + (seconds / 60);
        if (seconds < 86400) return "H" + (seconds / 3600);
        if (seconds < 604800) return "D";
        return "W";
    }

    public List<Candle> getHistoricalCandles(String symbol, Instant start, Instant end, String interval) {
        logger.info("Historical candles not yet implemented, returning empty list.");
        return Collections.emptyList();
    }
}
