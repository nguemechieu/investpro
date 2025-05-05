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
import java.util.*;
import java.util.concurrent.Future;

@Getter
public class OandaCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
    private static final String API_URL = "https://api-fxtrade.oanda.com/v3";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
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

    private final int numCandles = 5000;


    public OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(secondsPerCandle, Objects.requireNonNull(tradePair));
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
        int end = endTime.get();
        Instant now = Instant.now();
        int startEpoch = end == -1
                ? (int) (now.getEpochSecond() - secondsPerCandle * numCandles)
                : end - secondsPerCandle * numCandles;

        String startIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(startEpoch));
        String instrument = tradePair.toString('_');
        String granularity = getGranularityLabel(secondsPerCandle);

        String url = String.format("%s/instruments/%s/candles?granularity=%s&from=%s&price=M",
                API_URL, instrument, granularity, startIso);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer 186bcf0b71e393506c79af84a45a857e-247e832f3a754cb65a33be57296ce955")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        logger.info("OANDA Response: {}", response.body());

                        if (response.statusCode() != 200) {
                            logger.warn("Non-200 response: {}", response.statusCode());
                            return List.of(new CandleData());
                        }
                        JsonNode root = MAPPER.readTree(response.body());
                        JsonNode candlesNode = root.path("candles");

                        if (!candlesNode.isArray()) {
                            logger.warn("Unexpected candle format from OANDA for instrument: {}", instrument);
                            return List.of();
                        }

                        List<CandleData> candles = new ArrayList<>();
                        for (JsonNode node : candlesNode) {
                            JsonNode mid = node.path("mid");
                            CandleData candle = new CandleData(
                                    mid.path("o").asDouble(),
                                    mid.path("c").asDouble(),
                                    mid.path("h").asDouble(),
                                    mid.path("l").asDouble(),
                                    Instant.parse(node.path("time").asText()).getEpochSecond(),

                                    node.path("volume").asLong()
                            );
                            candles.add(candle);
                        }

                        candles.sort(Comparator.comparing(CandleData::getOpenTime));
                        if (!candles.isEmpty()) {
                            endTime.set(candles.getLast().getOpenTime());
                        }

                        return candles;

                    } catch (Exception e) {
                        logger.error("❌ Failed to parse OANDA candle data", e);
                        return List.of();
                    }
                });
    }
}
