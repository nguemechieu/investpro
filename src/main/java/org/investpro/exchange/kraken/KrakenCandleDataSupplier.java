package org.investpro.exchange.kraken;

import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class KrakenCandleDataSupplier extends CandleDataSupplier {
    static final Set<Integer> INTERVALS = Set.of(60, 300, 900, 1800, 3600, 14400, 86400, 604800, 1296000);
    private final Kraken exchange;
    KrakenCandleDataSupplier(Kraken exchange, int seconds, TradePair pair) {
        super(720, seconds, pair, new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        if (!INTERVALS.contains(seconds)) throw new IllegalArgumentException("Unsupported Kraken candle interval: " + seconds);
        this.exchange = exchange;
    }
    @Override public Set<Integer> getSupportedGranularities() { return INTERVALS; }
    @Override public Future<List<CandleData>> get() {
        return CompletableFuture.supplyAsync(() -> exchange.fetchCandles(tradePair, secondsPerCandle));
    }
    @Override public List<CandleData> getCandleData() { return exchange.fetchCandles(tradePair, secondsPerCandle); }
    @Override public CandleDataSupplier getCandleDataSupplier(int seconds, TradePair pair) {
        return new KrakenCandleDataSupplier(exchange, seconds, pair);
    }
    @Override public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(TradePair pair, Instant start, long elapsed, int seconds) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
    @Override public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair pair, Instant stopAt) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Kraken trade history is not implemented"));
    }
}
