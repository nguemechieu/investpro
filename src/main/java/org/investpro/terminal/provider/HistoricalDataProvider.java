package org.investpro.terminal.provider;

import org.investpro.terminal.domain.Candle;
import org.investpro.terminal.domain.InstrumentId;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface HistoricalDataProvider extends ProviderCapabilities {
    CompletableFuture<List<Candle>> candles(
            InstrumentId instrumentId,
            String timeframe,
            Instant from,
            Instant to,
            int limit
    );
}
