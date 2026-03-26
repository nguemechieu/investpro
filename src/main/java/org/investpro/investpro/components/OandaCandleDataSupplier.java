package org.investpro.investpro.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

@Getter
public class OandaCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
    private static final String API_URL = "https://api-fxtrade.oanda.com/v3";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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

    private final int numCandles = 5000;
    private final String apiSecret;
    private final HttpClient httpClient;

    public OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair, String apiSecret, HttpClient httpClient) {
        super(secondsPerCandle, Objects.requireNonNull(tradePair));
        this.apiSecret = Objects.requireNonNull(apiSecret, "apiSecret must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        return GRANULARITY_MAP.keySet();
    }

    private String getGranularityLabel(int secondsPerCandle) {
        return GRANULARITY_MAP.getOrDefault(secondsPerCandle, "M1");
    }

    @Override
    public Future<List<CandleData>> get() {
        Instant requestEnd = endTime.get() == -1
                ? Instant.now()
                : Instant.ofEpochSecond(endTime.get());
        String instrument = tradePair.toString('_');
        String granularity = getGranularityLabel(secondsPerCandle);

        String url = String.format(
                "%s/instruments/%s/candles?granularity=%s&count=%d&price=M&to=%s",
                API_URL,
                instrument,
                granularity,
                numCandles,
                DateTimeFormatter.ISO_INSTANT.format(requestEnd)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warn("Unable to fetch OANDA candles for {}. HTTP {}: {}", instrument, response.statusCode(), response.body());
                        return List.<CandleData>of();
                    }

                    try {
                        JsonNode root = MAPPER.readTree(response.body());
                        JsonNode candlesNode = root.path("candles");

                        if (!candlesNode.isArray()) {
                            logger.warn("Unexpected candle format from OANDA for instrument: {}", instrument);
                            return List.<CandleData>of();
                        }

                        List<CandleData> candles = new ArrayList<>();
                        for (JsonNode node : candlesNode) {
                            JsonNode mid = node.path("mid");
                            if (mid.isMissingNode() || mid.isEmpty()) {
                                continue;
                            }

                            candles.add(new CandleData(
                                    mid.path("o").asDouble(),
                                    mid.path("c").asDouble(),
                                    mid.path("h").asDouble(),
                                    mid.path("l").asDouble(),
                                    Instant.parse(node.path("time").asText()).getEpochSecond(),
                                    node.path("volume").asLong()
                            ));
                        }

                        candles.sort(Comparator.comparing(CandleData::getOpenTime));
                        if (!candles.isEmpty()) {
                            endTime.set(Math.max(candles.getFirst().getOpenTime() - secondsPerCandle, 0));
                        }

                        return candles;
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA candle data", e);
                        return List.<CandleData>of();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to request OANDA candle data for {}", instrument, throwable);
                    return List.of();
                });
    }
}
