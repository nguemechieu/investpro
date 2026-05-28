package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Computes technical features from candlestick data.
 *
 * Avoids lookahead bias by:
 * - Using completed candles only
 * - Excluding the latest candle from historical lookback calculations
 * - Computing indicators on historical data only
 *
 * Produces FeatureRow containing all features needed by UnifiedStrategy.
 */
@Slf4j
@Getter
@Setter
public class FeaturePipeline {

    /**
     * Compute the latest feature row from historical candles.
     *
     * @param candles Historical candleData, oldest to newest
     * @param config  Feature pipeline configuration
     * @return FeatureRow with computed features, or null if insufficient data
     */
    public @Nullable FeatureRow computeLatest(
            @NotNull List<CandleData> candles,
            @NotNull FeaturePipelineConfig config) {

        if (candles.isEmpty()) {
            log.debug("No candles provided");
            return null;
        }

        // Minimum bars needed for valid computation
        int minRequired = Math.max(
                Math.max(config.getEmaSlow(), config.getRsiPeriod()),
                config.getAtrPeriod()) + 10; // Extra margin for stability

        if (candles.size() < minRequired) {
            log.debug("Insufficient candles: {} < {}", candles.size(), minRequired);
            return null;
        }

        try {
            CandleData latest = candles.get(candles.size() - 1);
            CandleData previous = candles.size() > 1 ? candles.get(candles.size() - 2) : latest;

            double close = latest.closePrice();
            double previousClose = previous.closePrice();

            if (close <= 0 || previousClose <= 0) {
                log.warn("Invalid price data: close={}, previousClose={}", close, previousClose);
                return null;
            }

            // Compute indicators
            double emaFast = computeEMA(candles, config.getEmaFast());
            double emaSlow = computeEMA(candles, config.getEmaSlow());
            double rsi = computeRSI(candles, config.getRsiPeriod());
            double atr = computeATR(candles, config.getAtrPeriod());
            double atrPct = atr / close;

            // Bollinger Bands (SMA20 with 2 std dev)
            double[] bollingerBands = computeBollingerBands(candles, 20, 2.0);
            double upperBand = bollingerBands[0];
            double lowerBand = bollingerBands[1];

            // Band position: 0 = at lower band, 1 = at upper band
            double bandPosition = (close - lowerBand) / Math.max(upperBand - lowerBand, 0.0001);
            bandPosition = Math.max(0, Math.min(1, bandPosition));

            // Breakout levels (excluding latest candle to avoid lookahead)
            double[] breakoutLevels = computeBreakoutLevels(candles, config.getBreakoutLookback());
            double breakoutHigh = breakoutLevels[0];
            double breakoutLow = breakoutLevels[1];

            // Volume and momentum
            double volume = latest.volume();
            double avgVolume = computeAverageVolume(candles, 20);
            double volumeRatio = avgVolume > 0 ? volume / avgVolume : 1.0;

            double momentum = computeMomentum(candles, 10);
            double trendStrength = computeTrendStrength(candles, config.getEmaFast(), config.getEmaSlow());

            // Pullback gap (gap from fast EMA to low of recent pullback)
            double pullbackGap = computePullbackGap(candles, config.getEmaFast());

            // MACD (simplified: EMA12 - EMA26, signal = simple approximation)
            double macdLine = computeEMA(candles, 12) - computeEMA(candles, 26);
            double macdSignal = computeEMA(candles, 9); // Simplified - ideally would be EMA of MACD line

            // Determine market regime
            String regime = determineRegime(atrPct, trendStrength);

            return FeatureRow.builder()
                    .close(close)
                    .previousClose(previousClose)
                    .emaFast(emaFast)
                    .emaSlow(emaSlow)
                    .rsi(rsi)
                    .atr(atr)
                    .atrPct(atrPct)
                    .upperBand(upperBand)
                    .lowerBand(lowerBand)
                    .bandPosition(bandPosition)
                    .breakoutHigh(breakoutHigh)
                    .breakoutLow(breakoutLow)
                    .volume(volume)
                    .volumeRatio(volumeRatio)
                    .momentum(momentum)
                    .trendStrength(trendStrength)
                    .pullbackGap(pullbackGap)
                    .macdLine(macdLine)
                    .macdSignal(macdSignal)
                    .regime(regime)
                    .build();

        } catch (Exception e) {
            log.error("Error computing features", e);
            return null;
        }
    }

    /**
     * Compute Exponential Moving Average.
     */
    private double computeEMA(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period)
            return 0;

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).closePrice();
        }

        double ema = sum / period;
        double multiplier = 2.0 / (period + 1);

        for (int i = period; i < candles.size(); i++) {
            double close = candles.get(i).closePrice();
            ema = close * multiplier + ema * (1 - multiplier);
        }

        return ema;
    }

    /**
     * Compute Relative Strength Index.
     */
    private double computeRSI(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1)
            return 50;

        double gainSum = 0;
        double lossSum = 0;

        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum -= change;
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        // Smooth the averages for remaining candles
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = avgLoss * (period - 1) / period;
            } else {
                avgGain = avgGain * (period - 1) / period;
                avgLoss = (avgLoss * (period - 1) - change) / period;
            }
        }

        if (avgLoss == 0) {
            return avgGain > 0 ? 100 : 50;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * Compute Average True Range.
     */
    private double computeATR(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period)
            return 0;

        double trSum = 0;
        for (int i = 0; i < period; i++) {
            CandleData candle = candles.get(i);
            double tr = computeTrueRange(candle, i > 0 ? candles.get(i - 1) : null);
            trSum += tr;
        }

        double atr = trSum / period;

        // Smooth ATR for remaining candles
        for (int i = period; i < candles.size(); i++) {
            double tr = computeTrueRange(candles.get(i), candles.get(i - 1));
            atr = (atr * (period - 1) + tr) / period;
        }

        return atr;
    }

    /**
     * Compute True Range for a single candle.
     */
    private double computeTrueRange(@NotNull CandleData current, @Nullable CandleData previous) {
        double high = current.highPrice();
        double low = current.lowPrice();

        if (previous == null) {
            return high - low;
        }

        double prevClose = previous.closePrice();
        double tr1 = high - low;
        double tr2 = Math.abs(high - prevClose);
        double tr3 = Math.abs(low - prevClose);

        return Math.max(tr1, Math.max(tr2, tr3));
    }

    /**
     * Compute Bollinger Bands using SMA and standard deviation.
     *
     * @return [upperBand, lowerBand, sma]
     */
    private double[] computeBollingerBands(
            @NotNull List<CandleData> candles,
            int period,
            double stdDevMultiplier) {

        if (candles.size() < period) {
            return new double[] { 0, 0, 0 };
        }

        // Compute SMA
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).closePrice();
        }
        double sma = sum / period;

        // Compute standard deviation
        double variance = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double diff = candles.get(i).closePrice() - sma;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / period);

        double upper = sma + (stdDev * stdDevMultiplier);
        double lower = sma - (stdDev * stdDevMultiplier);

        return new double[] { upper, lower, sma };
    }

    /**
     * Compute breakout levels from previous N candles, excluding the latest candle.
     * This prevents lookahead bias.
     *
     * @return [breakoutHigh, breakoutLow]
     */
    private double[] computeBreakoutLevels(
            @NotNull List<CandleData> candles,
            int lookback) {

        // Exclude the latest candle
        int endIndex = candles.size() - 1;
        int startIndex = Math.max(0, endIndex - lookback);

        double high = 0;
        double low = Double.MAX_VALUE;

        for (int i = startIndex; i < endIndex; i++) {
            CandleData candle = candles.get(i);
            high = Math.max(high, candle.highPrice());
            low = Math.min(low, candle.lowPrice());
        }

        if (low == Double.MAX_VALUE) {
            low = candles.get(endIndex).closePrice();
        }

        return new double[] { high, low };
    }

    /**
     * Compute average volume over N periods.
     */
    private double computeAverageVolume(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period) {
            period = candles.size();
        }

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).volume();
        }

        return sum / period;
    }

    /**
     * Compute momentum (ROC over 10 periods).
     */
    private double computeMomentum(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1)
            return 0;

        CandleData current = candles.get(candles.size() - 1);
        CandleData past = candles.get(candles.size() - period - 1);

        return (current.closePrice() - past.closePrice()) / past.closePrice();
    }

    /**
     * Compute trend strength based on EMA alignment and direction.
     */
    private double computeTrendStrength(
            @NotNull List<CandleData> candles,
            int fastPeriod,
            int slowPeriod) {

        double emaSlow = computeEMA(candles, slowPeriod);
        double close = candles.get(candles.size() - 1).closePrice();

        // Strength: how far price is from slow EMA relative to ATR
        double distance = Math.abs(close - emaSlow);
        double atr = computeATR(candles, 14);

        double strength = Math.min(1.0, distance / Math.max(atr, 0.0001));

        // Direction: positive if price > emaSlow, negative if below
        if (close < emaSlow) {
            strength = -strength;
        }

        return strength;
    }

    /**
     * Compute pullback gap as percent from price to fast EMA.
     */
    private double computePullbackGap(@NotNull List<CandleData> candles, int emaPeriod) {
        double close = candles.get(candles.size() - 1).closePrice();
        double emaFast = computeEMA(candles, emaPeriod);

        if (close <= 0)
            return 0;
        return (emaFast - close) / close;
    }

    /**
     * Determine market regime based on volatility and trend strength.
     */
    private @NotNull String determineRegime(double atrPct, double trendStrength) {
        if (atrPct > 0.04) {
            return "high_volatility";
        } else if (Math.abs(trendStrength) > 0.4) {
            return "trending";
        } else {
            return "ranging";
        }
    }
}
