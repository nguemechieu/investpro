package org.investpro.investpro;

import org.investpro.investpro.model.Candle;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface CandleService {
    List<Candle> getHistoricalCandles(String symbol, Instant startTime, Instant endTime, String interval);

    CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    CompletableFuture<Optional<Candle>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);
}
