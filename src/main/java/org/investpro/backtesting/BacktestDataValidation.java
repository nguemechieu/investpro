package org.investpro.backtesting;

import org.investpro.data.CandleData;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Reject invalid historical inputs before a strategy can generate simulated trades. */
public final class BacktestDataValidation {
    private BacktestDataValidation() {}

    public static void validate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) throw new IllegalArgumentException("Historical candles are required");
        long previous = Long.MIN_VALUE;
        for (int index = 0; index < candles.size(); index++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Backtest cancelled");
            CandleData candle = candles.get(index);
            if (candle == null || candle.placeHolder()
                    || !positive(candle.openPrice()) || !positive(candle.closePrice())
                    || !positive(candle.highPrice()) || !positive(candle.lowPrice())
                    || candle.highPrice() < Math.max(candle.openPrice(), candle.closePrice())
                    || candle.lowPrice() > Math.min(candle.openPrice(), candle.closePrice())
                    || !Double.isFinite(candle.volume()) || candle.volume() < 0) {
                throw new IllegalArgumentException("Invalid OHLC/volume or placeholder candle at index " + index);
            }
            if (candle.openTime() <= previous) {
                throw new IllegalArgumentException("Candle timestamps must be unique and increasing at index " + index);
            }
            previous = candle.openTime();
        }
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
