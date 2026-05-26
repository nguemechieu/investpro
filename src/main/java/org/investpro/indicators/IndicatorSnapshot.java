package org.investpro.indicators;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record IndicatorSnapshot(
        BigDecimal ema,
        BigDecimal sma,
        BigDecimal rsi,
        BigDecimal macd,
        BigDecimal atr,
        BigDecimal bollingerUpper,
        BigDecimal bollingerMiddle,
        BigDecimal bollingerLower,
        BigDecimal vwap,
        BigDecimal trendStrength,
        BigDecimal volatilityScore,
        BigDecimal support,
        BigDecimal resistance,
        Instant calculatedAt,
        Map<String, Object> metadata) {

    public IndicatorSnapshot {
        ema = value(ema);
        sma = value(sma);
        rsi = value(rsi);
        macd = value(macd);
        atr = value(atr);
        bollingerUpper = value(bollingerUpper);
        bollingerMiddle = value(bollingerMiddle);
        bollingerLower = value(bollingerLower);
        vwap = value(vwap);
        trendStrength = value(trendStrength);
        volatilityScore = value(volatilityScore);
        support = value(support);
        resistance = value(resistance);
        calculatedAt = calculatedAt == null ? Instant.now() : calculatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static IndicatorSnapshot empty() {
        return new IndicatorSnapshot(null, null, null, null, null, null, null, null, null,
                null, null, null, null, Instant.now(), Map.of());
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
