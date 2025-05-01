package org.investpro.investpro.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.investpro.investpro.CandleDataSupplier;
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
import java.util.*;
import java.util.concurrent.Future;

@Getter

public class OandaCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
    private static final String API_URL = "https://api-fxtrade.oanda.com/v3";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final HttpClient httpClient = HttpClient.newHttpClient();

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
    int numCandles = 1000;
    private String apiSecret;

    public OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(secondsPerCandle, tradePair);
    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        return GRANULARITY_MAP.keySet();
    }

    private String getGranularityLabel(int secondsPerCandle) {
        return GRANULARITY_MAP.getOrDefault(secondsPerCandle, "M1");
    }

    @Override
    public Future<List<Candle>> get() {

        int start = endTime.get() == -1
                ? (int) (Instant.now().getEpochSecond() - secondsPerCandle * numCandles)
                : endTime.get() - secondsPerCandle * numCandles;

        String startIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(start));
        String instrument = tradePair.toString('_');
        String granularity = getGranularityLabel(secondsPerCandle);
        String url = String.format("%s/instruments/%s/candles?granularity=%s&from=%s&price=M", API_URL, instrument, granularity, startIso);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        JsonNode root = OBJECT_MAPPER.readTree(response.body());
                        JsonNode candlesNode = root.get("candles");
                        if (candlesNode == null || !candlesNode.isArray()) {
                            logger.warn("No candles returned for: {}", instrument);
                            return Collections.emptyList();
                        }

                        List<Candle> candles = new ArrayList<>();
                        for (JsonNode node : candlesNode) {
                            long timestamp = Instant.parse(node.get("time").asText()).getEpochSecond();
                            JsonNode mid = node.get("mid");
                            candles.add(new Candle(
                                    timestamp,
                                    mid.get("o").asDouble(),
                                    mid.get("c").asDouble(),
                                    mid.get("h").asDouble(),
                                    mid.get("l").asDouble(),
                                    node.get("volume").asLong()
                            ));
                        }

                        // Sort by time and update endTime for next request
                        candles.sort(Comparator.comparingLong(c -> c.getTime().getEpochSecond()));
                        if (!candles.isEmpty()) {
                            endTime.set((int) candles.getLast().getTime().getEpochSecond());
                        }

                        return candles;

                    } catch (Exception e) {
                        logger.error("Error parsing OANDA candle data: {}", e.getMessage(), e);
                        return Collections.emptyList();
                    }
                });
    }
}
