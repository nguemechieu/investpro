package org.investpro.terminal.persistence;

import org.investpro.terminal.domain.Candle;
import org.investpro.terminal.domain.InstrumentId;

import java.time.Instant;
import java.util.List;

public interface CandleRepository {
    void saveAll(List<Candle> candles);
    List<Candle> findCandles(InstrumentId instrumentId, String timeframe, Instant from, Instant to, int limit);
}
