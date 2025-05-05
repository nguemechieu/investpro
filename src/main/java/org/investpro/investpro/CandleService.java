package org.investpro.investpro;

import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface CandleService {
    List<CandleData> getHistoricalCandles(TradePair tradePair, Instant startTime, Instant endTime, int interval);

    CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    CompletableFuture<Optional<CandleData>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);
}
