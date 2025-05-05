package org.investpro.investpro.ui.chart;


import org.investpro.investpro.Exchange;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CandleChartDataLoader {
    private final Exchange exchange;

    public CandleChartDataLoader(Exchange exchange) {
        this.exchange = exchange;
    }

    public List<CandleData> getCandleData(TradePair symbol, Instant startTime, Instant endTime, int interval) throws ExecutionException, InterruptedException {
        CompletableFuture<Optional<?>> candles = exchange.getHistoricalCandles(symbol, startTime, endTime, interval);
        return ensureNonEmptyCandles(symbol, startTime, candles.get());
    }

    public List<CandleData> fetchHistoricalData(TradePair symbol, Instant from, Instant to, int interval) throws ExecutionException, InterruptedException {
        return getCandleData(symbol, from, to, interval);
    }

    public List<CandleData> loadMoreCandles(TradePair tradePair, @NotNull Instant olderThan, int interval, int limit) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(tradePair);
        Instant from = olderThan.minusSeconds((long) interval * limit);
        return fetchHistoricalData(tradePair, from, olderThan, interval);
    }


    public CandleData fetchLatestCandle(TradePair pair, int currentInterval) throws ExecutionException, InterruptedException {

        Instant now = Instant.now();
        Instant from = now.minusSeconds(300);
        CompletableFuture<Optional<?>> candles = exchange.getHistoricalCandles(pair, from, now, currentInterval);
        List<CandleData> nonEmpty = ensureNonEmptyCandles(pair, from, candles.get());
        return nonEmpty.getLast();
    }


    private List<CandleData> ensureNonEmptyCandles(TradePair symbol, Instant time, Optional<?> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        return null;
    }
}
