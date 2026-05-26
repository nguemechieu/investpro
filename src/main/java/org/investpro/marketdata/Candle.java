package org.investpro.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        String exchangeId,
        String symbol,
        String timeframe,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean complete) {

    public Candle {
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        timeframe = safe(timeframe);
        openTime = openTime == null ? Instant.EPOCH : openTime;
        closeTime = closeTime == null ? openTime : closeTime;
        open = value(open);
        high = value(high);
        low = value(low);
        close = value(close);
        volume = value(volume);
    }

    public BigDecimal range() {
        return high.subtract(low).max(BigDecimal.ZERO);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
