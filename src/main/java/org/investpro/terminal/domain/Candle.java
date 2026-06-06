package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        InstrumentId instrumentId,
        String timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean placeholder
) {
    public Candle {
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        timeframe = timeframe == null ? "" : timeframe.trim();
        openTime = openTime == null ? Instant.EPOCH : openTime;
        open = valueOrZero(open);
        high = valueOrZero(high);
        low = valueOrZero(low);
        close = valueOrZero(close);
        volume = valueOrZero(volume);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
