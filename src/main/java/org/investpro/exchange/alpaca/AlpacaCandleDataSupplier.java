package org.investpro.exchange.alpaca;

import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Historical stock bars using the user's configured data feed; pages move backwards in time. */
final class AlpacaCandleDataSupplier extends CandleDataSupplier {
    private final Alpaca exchange;
    private volatile List<CandleData> candles = List.of();

    AlpacaCandleDataSupplier(Alpaca exchange, int seconds, TradePair pair) {
        super(300, seconds, pair, new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        exchange.supportsTimeframe(seconds);
        this.exchange = exchange;
    }

    @Override public Set<Integer> getSupportedGranularities() {
        return Set.of(60, 300, 900, 1800, 3600, 14400, 86400);
    }

    @Override public synchronized CompletableFuture<List<CandleData>> get() {
        int pageEnd = endTime.get();
        return CompletableFuture.supplyAsync(() -> {
            Instant end = Instant.ofEpochSecond(pageEnd);
            Instant start = end.minusSeconds(Math.max(30L * 86400, 4L * numCandles * secondsPerCandle));
            String symbol = URLEncoder.encode(tradePair.getBaseCode(), StandardCharsets.UTF_8);
            String path = "/v2/stocks/" + symbol + "/bars?timeframe=" + exchange.supportsTimeframe(secondsPerCandle)
                    + "&start=" + URLEncoder.encode(start.toString(), StandardCharsets.UTF_8)
                    + "&end=" + URLEncoder.encode(end.toString(), StandardCharsets.UTF_8)
                    + "&limit=" + numCandles + "&sort=desc&feed=" + Alpaca.dataFeed();
            var response = exchange.readData(path);
            if (!response.path("bars").isArray()) throw new IllegalStateException("Alpaca returned invalid historical bars");
            List<CandleData> next = new ArrayList<>();
            for (var bar : response.path("bars")) {
                next.add(new CandleData(bar.path("o").asDouble(), bar.path("c").asDouble(),
                        bar.path("h").asDouble(), bar.path("l").asDouble(),
                        Math.toIntExact(Instant.parse(bar.path("t").asText()).getEpochSecond()), bar.path("v").asDouble()));
            }
            next.sort(Comparator.comparingInt(CandleData::openTime));
            candles = List.copyOf(next);
            endTime.set(next.isEmpty() ? Math.toIntExact(start.getEpochSecond()) : next.getFirst().openTime() - 1);
            return candles;
        });
    }

    @Override public List<CandleData> getCandleData() { return candles; }
    @Override public CandleDataSupplier getCandleDataSupplier(int seconds, TradePair pair) {
        return new AlpacaCandleDataSupplier(exchange, seconds, pair);
    }
    @Override public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            TradePair pair, Instant start, long elapsed, int seconds) {
        return exchange.fetchCandleDataForInProgressCandle(pair, start, elapsed, seconds).thenApply(value -> value);
    }
    @Override public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair pair, Instant stopAt) {
        return exchange.fetchRecentTradesUntil(pair, stopAt);
    }
}
