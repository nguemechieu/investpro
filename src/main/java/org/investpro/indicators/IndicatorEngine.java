package org.investpro.indicators;

import org.investpro.market.MarketContext;
import org.investpro.marketdata.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IndicatorEngine {
    private static final MathContext MC = MathContext.DECIMAL64;

    public IndicatorSnapshot calculate(MarketContext context) {
        if (context == null || context.recentCandles().isEmpty()) {
            return IndicatorSnapshot.empty();
        }

        List<Candle> candles = context.recentCandles();
        int size = candles.size();
        BigDecimal latestClose = candles.get(size - 1).close();
        BigDecimal sma = sma(candles, Math.min(20, size));
        BigDecimal ema = ema(candles, Math.min(20, size));
        BigDecimal rsi = rsi(candles, Math.min(14, Math.max(1, size - 1)));
        BigDecimal macd = ema(candles, Math.min(12, size)).subtract(ema(candles, Math.min(26, size)), MC);
        BigDecimal atr = atr(candles, Math.min(14, size));
        BigDecimal volatility = latestClose.signum() == 0 ? BigDecimal.ZERO : atr.divide(latestClose, MC).multiply(BigDecimal.valueOf(100), MC);
        BigDecimal stdDev = standardDeviation(candles, Math.min(20, size), sma);
        BigDecimal bollingerUpper = sma.add(stdDev.multiply(BigDecimal.valueOf(2), MC), MC);
        BigDecimal bollingerLower = sma.subtract(stdDev.multiply(BigDecimal.valueOf(2), MC), MC);
        BigDecimal vwap = vwap(candles);
        BigDecimal support = lowestLow(candles, Math.min(20, size));
        BigDecimal resistance = highestHigh(candles, Math.min(20, size));
        BigDecimal trendStrength = trendStrength(latestClose, ema, sma, atr);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "deterministic");
        metadata.put("candleCount", size);
        metadata.put("bollingerWidth", latestClose.signum() == 0
                ? BigDecimal.ZERO
                : bollingerUpper.subtract(bollingerLower, MC).divide(latestClose, MC));
        metadata.put("priceAboveVwap", latestClose.compareTo(vwap) > 0);
        metadata.put("rsiState", rsi.compareTo(BigDecimal.valueOf(70)) >= 0 ? "OVERBOUGHT"
                : rsi.compareTo(BigDecimal.valueOf(30)) <= 0 ? "OVERSOLD" : "NEUTRAL");

        return new IndicatorSnapshot(
                ema,
                sma,
                rsi,
                macd,
                atr,
                bollingerUpper,
                sma,
                bollingerLower,
                vwap,
                trendStrength,
                volatility,
                support,
                resistance,
                Instant.now(),
                metadata);
    }

    private BigDecimal sma(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return BigDecimal.ZERO;
        }
        int start = Math.max(0, candles.size() - period);
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).close(), MC);
            count++;
        }
        return count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), MC);
    }

    private BigDecimal ema(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1.0));
        BigDecimal ema = candles.get(0).close();
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            ema = close.multiply(multiplier, MC).add(ema.multiply(BigDecimal.ONE.subtract(multiplier, MC), MC), MC);
        }
        return ema;
    }

    private BigDecimal rsi(List<Candle> candles, int period) {
        if (candles == null || candles.size() < 2 || period <= 0) {
            return BigDecimal.valueOf(50);
        }
        int start = Math.max(1, candles.size() - period);
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close(), MC);
            if (change.signum() > 0) {
                gains = gains.add(change, MC);
            } else {
                losses = losses.add(change.abs(), MC);
            }
            count++;
        }
        if (count == 0) {
            return BigDecimal.valueOf(50);
        }
        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(count), MC);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(count), MC);
        if (avgLoss.signum() == 0) {
            return avgGain.signum() == 0 ? BigDecimal.valueOf(50) : BigDecimal.valueOf(100);
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs, MC), MC), MC);
    }

    private BigDecimal atr(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return BigDecimal.ZERO;
        }
        int start = Math.max(0, candles.size() - period);
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i < candles.size(); i++) {
            Candle current = candles.get(i);
            BigDecimal range = current.high().subtract(current.low(), MC).abs();
            if (i > 0) {
                BigDecimal previousClose = candles.get(i - 1).close();
                range = range.max(current.high().subtract(previousClose, MC).abs());
                range = range.max(current.low().subtract(previousClose, MC).abs());
            }
            sum = sum.add(range, MC);
            count++;
        }
        return count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), MC);
    }

    private BigDecimal standardDeviation(List<Candle> candles, int period, BigDecimal mean) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return BigDecimal.ZERO;
        }
        int start = Math.max(0, candles.size() - period);
        double sumSquared = 0.0;
        int count = 0;
        double meanValue = mean.doubleValue();
        for (int i = start; i < candles.size(); i++) {
            double diff = candles.get(i).close().doubleValue() - meanValue;
            sumSquared += diff * diff;
            count++;
        }
        return count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(Math.sqrt(sumSquared / count));
    }

    private BigDecimal vwap(List<Candle> candles) {
        BigDecimal priceVolume = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (Candle candle : candles) {
            BigDecimal typical = candle.high().add(candle.low(), MC).add(candle.close(), MC).divide(BigDecimal.valueOf(3), MC);
            priceVolume = priceVolume.add(typical.multiply(candle.volume(), MC), MC);
            volume = volume.add(candle.volume(), MC);
        }
        return volume.signum() == 0 ? BigDecimal.ZERO : priceVolume.divide(volume, MC);
    }

    private BigDecimal lowestLow(List<Candle> candles, int period) {
        int start = Math.max(0, candles.size() - period);
        BigDecimal low = candles.get(start).low();
        for (int i = start + 1; i < candles.size(); i++) {
            low = low.min(candles.get(i).low());
        }
        return low;
    }

    private BigDecimal highestHigh(List<Candle> candles, int period) {
        int start = Math.max(0, candles.size() - period);
        BigDecimal high = candles.get(start).high();
        for (int i = start + 1; i < candles.size(); i++) {
            high = high.max(candles.get(i).high());
        }
        return high;
    }

    private BigDecimal trendStrength(BigDecimal close, BigDecimal ema, BigDecimal sma, BigDecimal atr) {
        BigDecimal baseline = ema.add(sma, MC).divide(BigDecimal.valueOf(2), MC);
        if (atr.signum() == 0) {
            return close.compareTo(baseline) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        BigDecimal normalized = close.subtract(baseline, MC).abs().divide(atr, MC);
        return BigDecimal.valueOf(Math.min(1.0, normalized.doubleValue()));
    }
}
