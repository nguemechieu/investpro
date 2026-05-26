package org.investpro.indicators;

import org.investpro.market.MarketContext;
import org.investpro.marketdata.Candle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class IndicatorEngine {
    public IndicatorSnapshot calculate(MarketContext context) {
        if (context == null || context.recentCandles().isEmpty()) {
            return IndicatorSnapshot.empty();
        }

        List<Candle> candles = context.recentCandles();
        BigDecimal sma = sma(candles, Math.min(20, candles.size()));
        BigDecimal latestClose = candles.getLast().close();
        BigDecimal atr = context.volatility();
        BigDecimal trendStrength = latestClose.compareTo(sma) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;

        return new IndicatorSnapshot(
                latestClose,
                sma,
                BigDecimal.valueOf(50),
                BigDecimal.ZERO,
                atr,
                sma.add(atr.multiply(BigDecimal.valueOf(2))),
                sma,
                sma.subtract(atr.multiply(BigDecimal.valueOf(2))),
                BigDecimal.ZERO,
                trendStrength,
                atr,
                candles.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                candles.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                Instant.now(),
                Map.of("engine", "deterministic-phase1"));
    }

    private BigDecimal sma(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return BigDecimal.ZERO;
        }
        List<Candle> window = candles.subList(Math.max(0, candles.size() - period), candles.size());
        BigDecimal sum = window.stream().map(Candle::close).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), java.math.MathContext.DECIMAL64);
    }
}
