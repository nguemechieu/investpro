package org.investpro.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MarketTick(
        String exchangeId,
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal last,
        BigDecimal volume,
        Instant timestamp,
        Map<String, Object> metadata) {

    public MarketTick {
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        bid = value(bid);
        ask = value(ask);
        last = value(last);
        volume = value(volume);
        timestamp = timestamp == null ? Instant.EPOCH : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public BigDecimal spread() {
        if (ask.signum() <= 0 || bid.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return ask.subtract(bid).max(BigDecimal.ZERO);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
