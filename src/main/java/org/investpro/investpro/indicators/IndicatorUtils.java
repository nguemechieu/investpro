//package org.investpro.investpro.indicators;
//
//import org.investpro.investpro.model.CandleData;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.*;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//public class IndicatorUtils {
//
//    /**
//     * Calculates the Simple Moving Average (SMA) for a given period.
//     *
//     * @param candles List of Candle objects
//     * @param period  Number of periods for the SMA
//     * @return List of SMA values (same size as input; entries before period are null)
//     */
//    public static List<Double> calculateSMA(List<Candle> candles, int period) {
//        return IntStream.range(0, candles.size())
//                .mapToObj(i -> {
//                    if (i < period - 1) return null;
//                    return candles.subList(i - period + 1, i + 1).stream()
//                            .mapToDouble(c -> c.getClose().doubleValue())
//                            .average().orElse(Double.NaN);
//                })
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * Calculates the Exponential Moving Average (EMA) for a given period.
//     *
//     * @param candles List of Candle objects
//     * @param period  Number of periods for the EMA
//     * @return List of EMA values (same size as input; entries before period are null)
//     */
//    public static @NotNull List<Double> calculateEMA(List<Double> candles, int period) {
//        List<Double> emaList = new ArrayList<>();
//        double multiplier = 2.0 / (period + 1);
//        Double previousEma = null;
//
//        for (int i = 0; i < candles.size(); i++) {
//            double close = candles.get(i);
//            if (i < period - 1) {
//                emaList.add(null);
//                continue;
//            }
//
//            if (previousEma == null) {
//                // First EMA is a simple average
//                previousEma = candles.subList(i - period + 1, i + 1).stream()
//                        .mapToDouble(c -> c)
//                        .average().orElse(0);
//            } else {
//                previousEma = ((close - previousEma) * multiplier) + previousEma;
//            }
//            emaList.add(previousEma);
//        }
//        return emaList;
//    }
//
//    /**
//     * Calculates Bollinger Bands.
//     *
//     * @param candles List of Candle objects
//     * @param period  Period for SMA and standard deviation (typically 20)
//     * @param multiplier Standard deviation multiplier (typically 2)
//     * @return List of BollingerBandResult with upper, middle, and lower bands
//     */
//    public static List<BollingerBandResult> calculateBollingerBands(List<Candle> candles, int period, double multiplier) {
//        List<Double> closePrices = candles.stream()
//                .map(c -> c.getClose().doubleValue())
//                .collect(Collectors.toList());
//
//        List<BollingerBandResult> results = new ArrayList<>();
//
//        for (int i = 0; i < closePrices.size(); i++) {
//            if (i < period - 1) {
//                results.add(new BollingerBandResult(null, null, null));
//                continue;
//            }
//
//            List<Double> window = closePrices.subList(i - period + 1, i + 1);
//            double sma = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//            double stdDev = Math.sqrt(window.stream()
//                    .mapToDouble(p -> Math.pow(p - sma, 2))
//                    .sum() / period);
//
//            double upper = sma + multiplier * stdDev;
//            double lower = sma - multiplier * stdDev;
//
//            results.add(new BollingerBandResult(upper, sma, lower));
//        }
//
//        return results;
//    }
//
//    /**
//     * Container class for Bollinger Bands result.
//     */
//    public static class BollingerBandResult {
//        public final Double upperBand;
//        public final Double middleBand;
//        public final Double lowerBand;
//        public double upper;
//        public double lower;
//
//        public BollingerBandResult(Double upperBand, Double middleBand, Double lowerBand) {
//            this.upperBand = upperBand;
//            this.middleBand = middleBand;
//            this.lowerBand = lowerBand;
//        }
//    }
//    /**
//     * Calculates the Average True Range (ATR) for a given list of candles.
//     *
//     * @param candles List of Candle objects
//     * @param period  Number of periods to average (commonly 14)
//     * @return List of ATR values, same size as candles (first period-1 will be null)
//     */
//    public static List<Double> calculateATR(List<Candle> candles, int period) {
//        List<Double> atrValues = new ArrayList<>();
//        List<Double> trueRanges = new ArrayList<>();
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i == 0) {
//                trueRanges.add(candles.get(i).getHigh().doubleValue() - candles.get(i).getLow().doubleValue());
//            } else {
//                double high = candles.get(i).getHigh().doubleValue();
//                double low = candles.get(i).getLow().doubleValue();
//                double prevClose = candles.get(i - 1).getClose().doubleValue();
//
//                double tr = Math.max(high - low,
//                        Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
//
//                trueRanges.add(tr);
//            }
//
//            if (i < period - 1) {
//                atrValues.add(null); // Not enough data yet
//            } else {
//                double atr = trueRanges.subList(i - period + 1, i + 1)
//                        .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//                atrValues.add(atr);
//            }
//        }
//
//        return atrValues;
//    }
//    /**
//     * Calculates the Stochastic Oscillator (%K and %D).
//     *
//     * @param candles   List of Candle data
//     * @param kPeriod   Period for %K calculation (e.g., 14)
//     * @param dPeriod   Period for %D smoothing (e.g., 3)
//     * @return A list of double[] where each contains [%K, %D]
//     */
//    public static List<double[]> calculateStochasticOscillator(List<Candle> candles, int kPeriod, int dPeriod) {
//        List<Double> percentK = new ArrayList<>();
//        List<double[]> result = new ArrayList<>();
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < kPeriod - 1) {
//                percentK.add(null);
//                result.add(null);
//                continue;
//            }
//
//            double highestHigh = candles.subList(i - kPeriod + 1, i + 1).stream()
//                    .mapToDouble(c -> c.getHigh().doubleValue())
//                    .max()
//                    .orElse(0);
//
//            double lowestLow = candles.subList(i - kPeriod + 1, i + 1).stream()
//                    .mapToDouble(c -> c.getLow().doubleValue())
//                    .min()
//                    .orElse(0);
//
//            double close = candles.get(i).getClose().doubleValue();
//            double k = ((close - lowestLow) / (highestHigh - lowestLow)) * 100;
//            percentK.add(k);
//        }
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < kPeriod + dPeriod - 2 || percentK.get(i) == null) {
//                result.set(i, null);
//            } else {
//                double d = percentK.subList(i - dPeriod + 1, i + 1).stream()
//                        .mapToDouble(Double::doubleValue)
//                        .average()
//                        .orElse(0);
//                result.set(i, new double[]{percentK.get(i), d});
//            }
//        }
//
//        return result;
//    }
//    /**
//     * Calculates the Commodity Channel Index (CCI).
//     *
//     * @param candles   List of Candle data
//     * @param period    Lookback period (e.g., 20)
//     * @return          List of CCI values
//     */
//    public static List<Double> calculateCCI(List<CandleData> candles, int period) {
//        List<Double> cciValues = new ArrayList<>();
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < period - 1) {
//                cciValues.add(null);
//                continue;
//            }
//
//            List<CandleData> subList = candles.subList(i - period + 1, i + 1);
//
//            // Typical Price = (High + Low + Close) / 3
//            List<Double> typicalPrices = subList.stream()
//                    .map(c -> (c.getHighPrice() + c.getLowPrice() + c.getClose().doubleValue()) / 3)
//                    .toList();
//
//            double avgTypicalPrice = typicalPrices.stream()
//                    .mapToDouble(Double::doubleValue)
//                    .average()
//                    .orElse(0);
//
//            double meanDeviation = typicalPrices.stream()
//                    .mapToDouble(tp -> Math.abs(tp - avgTypicalPrice))
//                    .average()
//                    .orElse(0);
//
//            double currentTypicalPrice = typicalPrices.getLast();
//
//            double cci = (currentTypicalPrice - avgTypicalPrice) / (0.015 * meanDeviation);
//            cciValues.add(cci);
//        }
//
//        return cciValues;
//    }
//
//    /**
//     * Calculates the Momentum indicator.
//     *
//     * @param candles   List of Candle data
//     * @param period    Number of periods to look back
//     * @return          List of momentum values
//     */
//    public static List<Double> calculateMomentum(List<Candle> candles, int period) {
//        List<Double> momentum = new ArrayList<>();
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < period) {
//                momentum.add(null); // Not enough data to calculate
//            } else {
//                double currentClose = candles.get(i).getClose().doubleValue();
//                double pastClose = candles.get(i - period).getClose().doubleValue();
//                momentum.add(currentClose - pastClose);
//            }
//        }
//        return momentum;
//    }
//    /**
//     * Calculates the Rate of Change (ROC) indicator.
//     *
//     * @param candles List of Candle data
//     * @param period  Number of periods to look back
//     * @return        List of ROC values
//     */
//    public static List<Double> calculateROC(List<Candle> candles, int period) {
//        List<Double> roc = new ArrayList<>();
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < period) {
//                roc.add(null); // Not enough data
//            } else {
//                double currentClose = candles.get(i).getClose().doubleValue();
//                double pastClose = candles.get(i - period).getClose().doubleValue();
//                if (pastClose == 0) {
//                    roc.add(0.0);
//                } else {
//                    roc.add(((currentClose - pastClose) / pastClose) * 100);
//                }
//            }
//        }
//        return roc;
//    }
//
//    /**
//     * Calculates basic support and resistance levels from a list of candle prices.
//     * This uses the most recent N candles' high/low values.
//     *
//     * @param candles The list of recent candles (e.g., last 20 or 50)
//     * @return A map containing "support" and "resistance" values
//     */
//    public static Map<String, Double> calculateSupportResistance(List<Candle> candles) {
//        Map<String, Double> levels = new HashMap<>();
//
//        double resistance = candles.stream()
//                .mapToDouble(c -> c.getHigh().doubleValue())
//                .max()
//                .orElse(Double.NaN);
//
//        double support = candles.stream()
//                .mapToDouble(c -> c.getLow().doubleValue())
//                .min()
//                .orElse(Double.NaN);
//
//        levels.put("resistance", resistance);
//        levels.put("support", support);
//
//        return levels;
//    }
//    /**
//     * Calculates Bollinger Bands.
//     *
//     * @param prices Array of closing prices
//     * @param period Period to use for moving average and standard deviation
//     * @param numStdDevs Number of standard deviations to set bandwidth
//     * @return Map with keys: middleBand, upperBand, lowerBand
//     */
//    public static @NotNull Map<String, Double> calculateBollingerBands(double @NotNull [] prices, int period, double numStdDevs) {
//        if (prices.length < period) {
//            throw new IllegalArgumentException("Not enough data for the specified period");
//        }
//
//        double sum = 0.0;
//        for (int i = prices.length - period; i < prices.length; i++) {
//            sum += prices[i];
//        }
//        double mean = sum / period;
//
//        double variance = 0.0;
//        for (int i = prices.length - period; i < prices.length; i++) {
//            variance += Math.pow(prices[i] - mean, 2);
//        }
//        double stdDev = Math.sqrt(variance / period);
//
//        Map<String, Double> bands = new HashMap<>();
//        bands.put("middleBand", mean);
//        bands.put("upperBand", mean + numStdDevs * stdDev);
//        bands.put("lowerBand", mean - numStdDevs * stdDev);
//
//        return bands;
//    }
//
//    /**
//     * Calculates Pivot Point, Support, and Resistance levels using the Classic formula.
//     * Based on the previous day's high, low, and close.
//     *
//     * @param previousHigh  Previous candle high
//     * @param previousLow   Previous candle low
//     * @param previousClose Previous candle close
//     * @return Map with pivot, support1-3, resistance1-3
//     */
//    public static Map<String, Double> calculatePivotPoints(double previousHigh, double previousLow, double previousClose) {
//        Map<String, Double> pivots = new HashMap<>();
//
//        double pivot = (previousHigh + previousLow + previousClose) / 3.0;
//        double r1 = (2 * pivot) - previousLow;
//        double s1 = (2 * pivot) - previousHigh;
//        double r2 = pivot + (previousHigh - previousLow);
//        double s2 = pivot - (previousHigh - previousLow);
//        double r3 = previousHigh + 2 * (pivot - previousLow);
//        double s3 = previousLow - 2 * (previousHigh - pivot);
//
//        pivots.put("pivot", pivot);
//        pivots.put("resistance1", r1);
//        pivots.put("resistance2", r2);
//        pivots.put("resistance3", r3);
//        pivots.put("support1", s1);
//        pivots.put("support2", s2);
//        pivots.put("support3", s3);
//
//        return pivots;
//    }
//    /**
//     * Calculates Fibonacci retracement levels given a high and low price.
//     *
//     * @param high The highest price in the trend
//     * @param low  The lowest price in the trend
//     * @return     Map of Fibonacci levels and their corresponding price values
//     */
//    public static Map<String, Double> calculateFibonacciLevels(double high, double low) {
//        Map<String, Double> levels = new LinkedHashMap<>();
//        double diff = high - low;
//
//        levels.put("0.0%", high);
//        levels.put("23.6%", high - diff * 0.236);
//        levels.put("38.2%", high - diff * 0.382);
//        levels.put("50.0%", high - diff * 0.500);
//        levels.put("61.8%", high - diff * 0.618);
//        levels.put("78.6%", high - diff * 0.786);
//        levels.put("100.0%", low);
//
//        return levels;
//    }
//
//    /**
//     * Calculates the Parabolic SAR (Stop and Reverse) indicator.
//     *
//     * @param candles          List of Candle data
//     * @param startAF          Starting acceleration factor (e.g., 0.02)
//     * @param increment        Increment for acceleration factor (e.g., 0.02)
//     * @param maxAF            Maximum acceleration factor (e.g., 0.2)
//     * @return                 List of Parabolic SAR values
//     */
//    public static List<Double> calculateParabolicSAR(List<Candle> candles, double startAF, double increment, double maxAF) {
//        List<Double> psar = new ArrayList<>();
//        int size = candles.size();
//
//        if (size < 2) {
//            return Collections.nCopies(size, null);
//        }
//
//        boolean upTrend = candles.get(1).getClose().doubleValue() > candles.get(0).getClose().doubleValue();
//        double af = startAF;
//        double ep = upTrend ? candles.get(1).getHigh().doubleValue() : candles.get(1).getLow().doubleValue();
//        double sar = upTrend ? candles.get(0).getLow().doubleValue() : candles.get(0).getHigh().doubleValue();
//
//        psar.add(null); // First value undefined
//        psar.add(sar);  // Second value is initial SAR
//
//        for (int i = 2; i < size; i++) {
//            Candle prev = candles.get(i - 1);
//            Candle curr = candles.get(i);
//
//            sar = sar + af * (ep - sar);
//
//            boolean reversal = false;
//            if (upTrend) {
//                if (curr.getLow().doubleValue() < sar) {
//                    upTrend = false;
//                    sar = ep;
//                    ep = curr.getLow().doubleValue();
//                    af = startAF;
//                    reversal = true;
//                } else {
//                    if (curr.getHigh().doubleValue() > ep) {
//                        ep = curr.getHigh().doubleValue();
//                        af = Math.min(af + increment, maxAF);
//                    }
//                }
//            } else {
//                if (curr.getHigh().doubleValue() > sar) {
//                    upTrend = true;
//                    sar = ep;
//                    ep = curr.getHigh().doubleValue();
//                    af = startAF;
//                    reversal = true;
//                } else {
//                    if (curr.getLow().doubleValue() < ep) {
//                        ep = curr.getLow().doubleValue();
//                        af = Math.min(af + increment, maxAF);
//                    }
//                }
//            }
//
//            psar.add(sar);
//        }
//
//        return psar;
//    }
//
//
//    /**
//     * Calculates the Bulls Power indicator.
//     *
//     * Bulls Power = High - EMA(Close)
//     *
//     * @param candles   List of Candle data
//     * @param emaPeriod EMA period (e.g., 13)
//     * @return          List of Bulls Power values
//     */
//    public static List<Double> calculateBullsPower(List<Candle> candles, int emaPeriod) {
//        List<Double> bullsPower = new ArrayList<>(candles.stream().map(m -> m.getClose().doubleValue()).toList());
//        List<Double> ema = calculateEMA(bullsPower, emaPeriod);
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < emaPeriod - 1) {
//                bullsPower.add(null);
//            } else {
//                double high = candles.get(i).getHigh().doubleValue();
//                bullsPower.add(high - ema.get(i));
//            }
//        }
//
//        return bullsPower;
//    }
//
//    /**
//     * Calculates the Bears Power indicator.
//     *
//     * Bears Power = Low - EMA(Close)
//     *
//     * @param candles       List of Candle data
//     * @param emaPeriod     EMA period (e.g., 13)
//     * @return              List of Bears Power values
//     */
//    public static List<Double> calculateBearsPower(List<Candle> candles, int emaPeriod) {
//        List<Double> bearsPower = new ArrayList<>();
//        List<Double> candlesDoubleClosed=new ArrayList<>();
//        candlesDoubleClosed.add(candles.stream().toList().stream().findFirst().orElse(new Candle()).getClose().doubleValue());
//        List<Double> ema = calculateEMA(candlesDoubleClosed, emaPeriod);
//
//        for (int i = 0; i < candles.size(); i++) {
//            if (i < emaPeriod - 1) {
//                bearsPower.add(null);
//            } else {
//                double low = candles.get(i).getLow().doubleValue();
//                bearsPower.add(low - ema.get(i));
//            }
//        }
//
//        return bearsPower;
//    }
//
//    /**
//     * Calculates the Relative Strength Index (RSI).
//     *
//     * @param candles List of Candle objects
//     * @param period  Lookback period (usually 14)
//     * @return List of RSI values (same size as input; entries before period are null)
//     */
//    public static List<Double> calculateRSI(List<Candle> candles, int period) {
//        List<Double> rsi = new ArrayList<>();
//        List<Double> closes = candles.stream()
//                .map(c -> c.getClose().doubleValue())
//                .collect(Collectors.toList());
//
//        double gain = 0, loss = 0;
//
//        for (int i = 0; i < closes.size(); i++) {
//            if (i == 0) {
//                rsi.add(null);
//                continue;
//            }
//
//            double change = closes.get(i) - closes.get(i - 1);
//            double up = Math.max(change, 0);
//            double down = Math.max(-change, 0);
//
//            if (i < period) {
//                gain += up;
//                loss += down;
//                rsi.add(null);
//                continue;
//            }
//
//            if (i == period) {
//                gain /= period;
//                loss /= period;
//            } else {
//                gain = ((gain * (period - 1)) + up) / period;
//                loss = ((loss * (period - 1)) + down) / period;
//            }
//
//            double rs = loss == 0 ? 100 : gain / loss;
//            rsi.add(100 - (100 / (1 + rs)));
//        }
//
//        return rsi;
//    }
//    /**
//     * Calculates MACD line, signal line, and histogram.
//     *
//     * @param candles      List of Candle objects
//     * @param shortPeriod  Fast EMA period (default 12)
//     * @param longPeriod   Slow EMA period (default 26)
//     * @param signalPeriod Signal line EMA period (default 9)
//     * @return List of MACDResult containing macdLine, signalLine, and histogram
//     */
//    public static List<MACDResult> calculateMACD(List<Candle> candles, int shortPeriod, int longPeriod, int signalPeriod) {
//        List<Double> closePrices = candles.stream()
//                .map(c -> c.getClose().doubleValue())
//                .collect(Collectors.toList());
//
//        List<Double> shortEma = calculateEMA(closePrices, shortPeriod);
//        List<Double> longEma = calculateEMA(closePrices, longPeriod);
//        List<Double> macdLine = new ArrayList<>();
//
//        for (int i = 0; i < closePrices.size(); i++) {
//            if (shortEma.get(i) == null || longEma.get(i) == null) {
//                macdLine.add(null);
//            } else {
//                macdLine.add(shortEma.get(i) - longEma.get(i));
//            }
//        }
//
//        List<Double> signalLine = calculateEMA(macdLine, signalPeriod);
//        List<MACDResult> results = new ArrayList<>();
//
//        for (int i = 0; i < closePrices.size(); i++) {
//            Double macd = macdLine.get(i);
//            Double signal = signalLine.get(i);
//            if (macd == null || signal == null) {
//                results.add(new MACDResult(null, null, null));
//            } else {
//                results.add(new MACDResult(macd, signal, macd - signal));
//            }
//        }
//
//        return results;
//    }
//
//
//    /**
//     * Container class for MACD results.
//     */
//    public static class MACDResult {
//        public final Double macdLine;
//        public final Double signalLine;
//        public final Double histogram;
//
//        public MACDResult(Double macdLine, Double signalLine, Double histogram) {
//            this.macdLine = macdLine;
//            this.signalLine = signalLine;
//            this.histogram = histogram;
//        }
//    }
//
//    // You can add more indicators like RSI, MACD, Bollinger Bands here...
//
//}
