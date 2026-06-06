package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTick(
        InstrumentId instrumentId,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal last,
        BigDecimal volume,
        Instant timestamp
) {
    public MarketTick {
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        bid = valueOrZero(bid);
        ask = valueOrZero(ask);
        last = valueOrZero(last);
        volume = valueOrZero(volume);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
