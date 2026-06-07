package org.investpro.strategy.auto;

import org.investpro.data.CandleData;

import java.util.List;

public class MarketRegimeDetector {

    public MarketRegime detect(List<CandleData> candles) {
        if (candles == null || candles.size() < 30) {
            return MarketRegime.UNKNOWN;
        }
        int lookback = Math.min(60, candles.size() - 1);
        double first = candles.get(candles.size() - lookback - 1).closePrice();
        double last = candles.get(candles.size() - 1).closePrice();
        double movePercent = first == 0.0 ? 0.0 : ((last - first) / first) * 100.0;
        double averageRangePercent = candles.subList(candles.size() - lookback, candles.size()).stream()
                .mapToDouble(candle -> {
                    double close = Math.max(0.00000001, candle.closePrice());
                    return ((candle.highPrice() - candle.lowPrice()) / close) * 100.0;
                })
                .average()
                .orElse(0.0);

        if (averageRangePercent >= 4.0) {
            return MarketRegime.HIGH_VOLATILITY;
        }
        if (averageRangePercent <= 0.75) {
            return MarketRegime.LOW_VOLATILITY;
        }
        if (movePercent >= 3.0) {
            return MarketRegime.TRENDING_UP;
        }
        if (movePercent <= -3.0) {
            return MarketRegime.TRENDING_DOWN;
        }
        return MarketRegime.RANGING;
    }
}
