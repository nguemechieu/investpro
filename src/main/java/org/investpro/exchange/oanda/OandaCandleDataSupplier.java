package org.investpro.exchange.oanda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class OandaCandleDataSupplier extends CandleDataSupplier {


    private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
    private static final String OANDA_API_URL = "https://api-fxtrade.oanda.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();

    private final String apiToken;

    public OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        this(secondsPerCandle, tradePair, "");
    }

    public OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair, String apiToken) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        this.apiToken = firstNonBlank(apiToken, configuredToken());
    }



    @Override
    public Set<Integer> getSupportedGranularities() {
        return new TreeSet<>(Set.of(
                5, 10, 15, 30,
                60, 120, 240, 300, 600, 900, 1800,
                3600, 7200, 10800, 14400, 21600, 28800, 43200,
                86400, 604800
        ));
    }

    @Override
    public List<CandleData> getCandleData() {
        return Collections.emptyList();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiToken);
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public Future<List<CandleData>> get() {
        if (tradePair == null) {
            logger.warn("OANDA candle request skipped: trade pair is missing");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (apiToken.isBlank()) {
            logger.warn("OANDA candle request skipped: API token is missing");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (endTime.get() == -1) {
            endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
        }

        String endDateString = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochSecond(endTime.get()));

        int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
        String startDateString = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochSecond(startTime));

        String uriStr = "%s/v3/instruments/%s/candles?granularity=%s&from=%s&to=%s&price=M"
                .formatted(
                        OANDA_API_URL,
                        urlEncode(toOandaInstrument(tradePair)),
                        toOandaGranularity(secondsPerCandle),
                        urlEncode(startDateString),
                        urlEncode(endDateString)
                );

        if (startTime == EARLIEST_DATA) {
            // signal more data is false
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return HTTP_CLIENT.sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(uriStr))
                                .timeout(java.time.Duration.ofSeconds(30))
                                .header("Authorization", "Bearer " + apiToken)
                                .header("Accept-Datetime-Format", "RFC3339")
                                .header("User-Agent", "InvestPro/1.0")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        logger.warn("OANDA candles failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response.body());
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    JsonNode candles = res.path("candles");

                    if (candles.isArray() && !candles.isEmpty()) {
                        List<CandleData> candleData = parseCandles(candles);
                        endTime.set(startTime);
                        return candleData;
                    } else {
                        logger.warn("OANDA candle response contained no candles: {}", response.body());
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * Convert seconds to OANDA granularity format.
     * OANDA uses: M1, M5, M15, M30, H1, H4, D
     */
    private String toOandaGranularity(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "M1";
            case 120 -> "M2";
            case 240 -> "M4";
            case 300 -> "M5";
            case 600 -> "M10";
            case 900 -> "M15";
            case 1800 -> "M30";
            case 3600 -> "H1";
            case 7200 -> "H2";
            case 10800 -> "H3";
            case 14400 -> "H4";
            case 21600 -> "H6";
            case 28800 -> "H8";
            case 43200 -> "H12";
            case 86400 -> "D";
            case 604800 -> "W";
            default -> "M1"; // Default to 1 minute
        };
    }

    static List<CandleData> parseCandles(JsonNode candles) {
        if (candles == null || !candles.isArray()) {
            return Collections.emptyList();
        }

        List<CandleData> candleData = new ArrayList<>(candles.size());

        for (JsonNode candle : candles) {
            if (!candle.path("complete").asBoolean(false)) {
                continue;
            }

            JsonNode mid = candle.path("mid");
            if (mid.isMissingNode()) {
                continue;
            }

            Instant openInstant = Instant.parse(candle.path("time").asText());
            int openTime = Math.toIntExact(openInstant.getEpochSecond());

            candleData.add(new CandleData(
                    mid.path("o").asDouble(),
                    mid.path("c").asDouble(),
                    mid.path("h").asDouble(),
                    mid.path("l").asDouble(),
                    openTime,
                    candle.path("volume").asDouble(0.0)
            ));
        }

        candleData.sort(Comparator.comparingInt(CandleData::openTime));
        return candleData;
    }

    private String toOandaInstrument(TradePair pair) {
        return "%s_%s".formatted(
                pair.getBaseCurrency().getCode().replace("/", "").replace("-", "").toUpperCase(Locale.ROOT),
                pair.getCounterCurrency().getCode().replace("/", "").replace("-", "").toUpperCase(Locale.ROOT)
        );
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String configuredToken() {
        String token = firstNonBlank(
                System.getenv("INVESTPRO_OANDA_API_KEY"),
                System.getenv("OANDA_API_KEY"),
                System.getenv("oanda_api_key"),
                System.getProperty("investpro.oanda.apiKey"),
                System.getProperty("oanda_api_key")
        );

        if (!token.isBlank()) {
            return token;
        }

        Properties properties = new Properties();
        try (InputStream stream = OandaCandleDataSupplier.class.getClassLoader().getResourceAsStream("conf.properties")) {
            if (stream != null) {
                properties.load(stream);
                return firstNonBlank(
                        properties.getProperty("oanda_api_key"),
                        properties.getProperty("OANDA_API_KEY"),
                        properties.getProperty("investpro.oanda.apiKey")
                );
            }
        } catch (Exception exception) {
            logger.debug("Unable to load OANDA token from conf.properties", exception);
        }

        return "";
    }
}
