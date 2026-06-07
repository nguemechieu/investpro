package org.investpro.indicators.engine;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.indicators.INDICATORS;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class IndicatorCalculatorRegistry {

    private static final IndicatorCalculatorRegistry INSTANCE = new IndicatorCalculatorRegistry();

    private final Map<INDICATORS, IndicatorCalculator> calculators = new EnumMap<>(INDICATORS.class);

    private IndicatorCalculatorRegistry() {
        registerCoreCalculators();
    }

    public static IndicatorCalculatorRegistry getInstance() {
        return INSTANCE;
    }

    public Optional<IndicatorCalculator> find(INDICATORS indicator) {
        return Optional.ofNullable(indicator == null ? null : calculators.get(indicator));
    }

    public IndicatorResult calculate(INDICATORS indicator, List<CandleData> candles, Map<String, String> parameters) {
        IndicatorCalculator calculator = find(indicator)
                .orElseGet(() -> new UnsupportedCalculator(indicator));
        return calculator.calculate(candles, parameters == null ? Map.of() : parameters);
    }

    public void register(IndicatorCalculator calculator) {
        if (calculator != null && calculator.indicator() != null) {
            calculators.put(calculator.indicator(), calculator);
        }
    }

    private void registerCoreCalculators() {
        register(new FunctionalCalculator(INDICATORS.SMA, (candles, params) -> result(INDICATORS.SMA, "sma", sma(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.EMA, (candles, params) -> result(INDICATORS.EMA, "ema", ema(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.WMA, (candles, params) -> result(INDICATORS.WMA, "wma", wma(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.DEMA, (candles, params) -> result(INDICATORS.DEMA, "dema", dema(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.TEMA, (candles, params) -> result(INDICATORS.TEMA, "tema", tema(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.HMA, (candles, params) -> result(INDICATORS.HMA, "hma", hma(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.TMA, (candles, params) -> result(INDICATORS.TMA, "tma", tma(close(candles), intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.T3_TILLSON, (candles, params) -> result(INDICATORS.T3_TILLSON, "t3", t3(close(candles), intParam(params, "period", 5), doubleParam(params, "volumeFactor", 0.7)))));
        register(new FunctionalCalculator(INDICATORS.KAMA, (candles, params) -> result(INDICATORS.KAMA, "kama", kama(close(candles), intParam(params, "period", 10), intParam(params, "fastPeriod", 2), intParam(params, "slowPeriod", 30)))));
        register(new FunctionalCalculator(INDICATORS.MESA_ADAPTIVE_MOVING_AVERAGE, (candles, params) -> mama(candles, params)));
        register(new FunctionalCalculator(INDICATORS.TIME_SERIES_FORECAST, (candles, params) -> result(INDICATORS.TIME_SERIES_FORECAST, "tsf", timeSeriesForecast(close(candles), intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.RSI, (candles, params) -> result(INDICATORS.RSI, "rsi", rsi(close(candles), intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.RSI_REGION_CROSSOVER, (candles, params) -> {
            List<Double> rsi = rsi(close(candles), intParam(params, "period", 14));
            return result(INDICATORS.RSI_REGION_CROSSOVER, Map.of("rsi", rsi, "crossoverSignal", regionSignal(rsi, doubleParam(params, "oversold", 30), doubleParam(params, "overbought", 70))));
        }));
        register(new FunctionalCalculator(INDICATORS.ATR, (candles, params) -> result(INDICATORS.ATR, "atr", atr(candles, intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.MACD, (candles, params) -> macdResult(INDICATORS.MACD, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.MACD_LINE, (candles, params) -> macdResult(INDICATORS.MACD_LINE, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.MACD_SIGNAL, (candles, params) -> macdResult(INDICATORS.MACD_SIGNAL, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.MACD_HISTOGRAM, (candles, params) -> macdResult(INDICATORS.MACD_HISTOGRAM, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.APO, (candles, params) -> apo(candles, params)));
        register(new FunctionalCalculator(INDICATORS.PPO, (candles, params) -> macdResult(INDICATORS.PPO, candles, params, true)));
        register(new FunctionalCalculator(INDICATORS.BOLLINGER_BANDS, IndicatorCalculatorRegistry::bollinger));
        register(new FunctionalCalculator(INDICATORS.ROC, (candles, params) -> result(INDICATORS.ROC, "roc", roc(close(candles), intParam(params, "period", 12)))));
        register(new FunctionalCalculator(INDICATORS.PERCENT_CHANGE, (candles, params) -> result(INDICATORS.PERCENT_CHANGE, "percentChange", roc(close(candles), intParam(params, "period", 1)))));
        register(new FunctionalCalculator(INDICATORS.MOMENTUM, (candles, params) -> result(INDICATORS.MOMENTUM, "momentum", momentum(close(candles), intParam(params, "period", 10)))));
        register(new FunctionalCalculator(INDICATORS.OBV, (candles, params) -> result(INDICATORS.OBV, "obv", obv(candles))));
        register(new FunctionalCalculator(INDICATORS.CHAIKIN_AD_OSCILLATOR, (candles, params) -> result(INDICATORS.CHAIKIN_AD_OSCILLATOR, "chaikinOscillator", chaikinOscillator(candles, intParam(params, "fastPeriod", 3), intParam(params, "slowPeriod", 10)))));
        register(new FunctionalCalculator(INDICATORS.MFI, (candles, params) -> result(INDICATORS.MFI, "mfi", mfi(candles, intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.CCI, (candles, params) -> result(INDICATORS.CCI, "cci", cci(candles, intParam(params, "period", 20)))));
        register(new FunctionalCalculator(INDICATORS.STOCHASTIC, (candles, params) -> stochasticResult(INDICATORS.STOCHASTIC, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.STOCHASTIC_REGION_CROSSOVER, (candles, params) -> stochasticResult(INDICATORS.STOCHASTIC_REGION_CROSSOVER, candles, params, true)));
        register(new FunctionalCalculator(INDICATORS.STOCH_RSI, (candles, params) -> stochRsiResult(INDICATORS.STOCH_RSI, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.STOCHASTIC_RSI, (candles, params) -> stochRsiResult(INDICATORS.STOCHASTIC_RSI, candles, params, false)));
        register(new FunctionalCalculator(INDICATORS.STOCH_RSI_REGION_CROSSOVER, (candles, params) -> stochRsiResult(INDICATORS.STOCH_RSI_REGION_CROSSOVER, candles, params, true)));
        register(new FunctionalCalculator(INDICATORS.WILLIAMS_R, (candles, params) -> result(INDICATORS.WILLIAMS_R, "williamsR", williamsR(candles, intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.ULTIMATE_OSCILLATOR, (candles, params) -> result(INDICATORS.ULTIMATE_OSCILLATOR, "ultimateOscillator", ultimateOscillator(candles, intParam(params, "period1", 7), intParam(params, "period2", 14), intParam(params, "period3", 28)))));
        register(new FunctionalCalculator(INDICATORS.ADX, (candles, params) -> result(INDICATORS.ADX, "adx", dmi(candles, intParam(params, "period", 14)).get("adx"))));
        register(new FunctionalCalculator(INDICATORS.DMI, (candles, params) -> result(INDICATORS.DMI, dmi(candles, intParam(params, "period", 14)))));
        register(new FunctionalCalculator(INDICATORS.AROON, (candles, params) -> result(INDICATORS.AROON, aroon(candles, intParam(params, "period", 25)))));
        register(new FunctionalCalculator(INDICATORS.AROON_OSCILLATOR, (candles, params) -> result(INDICATORS.AROON_OSCILLATOR, "aroonOscillator", aroonOscillator(candles, intParam(params, "period", 25)))));
        register(new FunctionalCalculator(INDICATORS.PSAR, (candles, params) -> result(INDICATORS.PSAR, "psar", parabolicSar(candles, doubleParam(params, "step", 0.02), doubleParam(params, "maxStep", 0.2)))));
        register(new FunctionalCalculator(INDICATORS.PARABOLIC_SAR, (candles, params) -> result(INDICATORS.PARABOLIC_SAR, "psar", parabolicSar(candles, doubleParam(params, "step", 0.02), doubleParam(params, "maxStep", 0.2)))));
        register(new FunctionalCalculator(INDICATORS.ELDER_RAY, (candles, params) -> result(INDICATORS.ELDER_RAY, elderRay(candles, intParam(params, "period", 13)))));
        register(new FunctionalCalculator(INDICATORS.ICHIMOKU, (candles, params) -> result(INDICATORS.ICHIMOKU, ichimoku(candles, intParam(params, "tenkan", 9), intParam(params, "kijun", 26), intParam(params, "senkouB", 52), intParam(params, "displacement", 26)))));
    }

    private static IndicatorResult bollinger(List<CandleData> candles, Map<String, String> params) {
        int period = intParam(params, "period", 20);
        double mult = doubleParam(params, "stdDevMult", 2.0);
        List<Double> closes = close(candles);
        List<Double> middle = sma(closes, period);
        List<Double> upper = fillNaN(closes.size());
        List<Double> lower = fillNaN(closes.size());
        List<Double> bandwidth = fillNaN(closes.size());
        List<Double> percentB = fillNaN(closes.size());
        for (int i = period - 1; i < closes.size(); i++) {
            double mean = middle.get(i);
            double sd = stdDev(closes, i - period + 1, i, mean);
            double u = mean + mult * sd;
            double l = mean - mult * sd;
            upper.set(i, u);
            lower.set(i, l);
            bandwidth.set(i, mean == 0 ? Double.NaN : (u - l) / mean);
            percentB.set(i, (u - l) == 0 ? Double.NaN : (closes.get(i) - l) / (u - l));
        }
        return result(INDICATORS.BOLLINGER_BANDS, Map.of("upper", upper, "middle", middle, "lower", lower, "bandwidth", bandwidth, "percentB", percentB));
    }

    private static IndicatorResult macdResult(INDICATORS indicator, List<CandleData> candles, Map<String, String> params, boolean percent) {
        List<Double> closes = close(candles);
        List<Double> fast = ema(closes, intParam(params, "fastPeriod", 12));
        List<Double> slow = ema(closes, intParam(params, "slowPeriod", 26));
        List<Double> line = fillNaN(closes.size());
        for (int i = 0; i < closes.size(); i++) {
            if (Double.isNaN(fast.get(i)) || Double.isNaN(slow.get(i))) {
                continue;
            }
            line.set(i, percent ? (slow.get(i) == 0 ? Double.NaN : ((fast.get(i) - slow.get(i)) / slow.get(i)) * 100.0) : fast.get(i) - slow.get(i));
        }
        List<Double> signal = ema(line, intParam(params, "signalPeriod", 9));
        List<Double> histogram = fillNaN(closes.size());
        for (int i = 0; i < closes.size(); i++) {
            if (!Double.isNaN(line.get(i)) && !Double.isNaN(signal.get(i))) {
                histogram.set(i, line.get(i) - signal.get(i));
            }
        }
        return result(indicator, Map.of(percent ? "ppo" : "macd", line, "signal", signal, "histogram", histogram));
    }

    private static IndicatorResult apo(List<CandleData> candles, Map<String, String> params) {
        List<Double> closes = close(candles);
        List<Double> fast = ema(closes, intParam(params, "fastPeriod", 12));
        List<Double> slow = ema(closes, intParam(params, "slowPeriod", 26));
        List<Double> apo = fillNaN(closes.size());
        for (int i = 0; i < closes.size(); i++) {
            if (!Double.isNaN(fast.get(i)) && !Double.isNaN(slow.get(i))) {
                apo.set(i, fast.get(i) - slow.get(i));
            }
        }
        return result(INDICATORS.APO, "apo", apo);
    }

    private static IndicatorResult stochasticResult(INDICATORS indicator, List<CandleData> candles, Map<String, String> params, boolean region) {
        List<Double> k = stochasticK(candles, intParam(params, "kPeriod", 14));
        List<Double> smoothK = sma(k, intParam(params, "smooth", 3));
        List<Double> d = sma(smoothK, intParam(params, "dPeriod", 3));
        Map<String, List<Double>> outputs = new LinkedHashMap<>();
        outputs.put("percentK", smoothK);
        outputs.put("percentD", d);
        if (region) {
            outputs.put("crossoverSignal", regionSignal(smoothK, doubleParam(params, "oversold", 20), doubleParam(params, "overbought", 80)));
        }
        return result(indicator, outputs);
    }

    private static IndicatorResult stochRsiResult(INDICATORS indicator, List<CandleData> candles, Map<String, String> params, boolean region) {
        List<Double> rsi = rsi(close(candles), intParam(params, "rsiPeriod", 14));
        List<Double> k = stochasticOfSeries(rsi, intParam(params, "stochPeriod", 14));
        List<Double> smoothK = sma(k, intParam(params, "kSmooth", 3));
        List<Double> d = sma(smoothK, intParam(params, "dSmooth", 3));
        Map<String, List<Double>> outputs = new LinkedHashMap<>();
        outputs.put("stochRsiK", smoothK);
        outputs.put("stochRsiD", d);
        if (region) {
            outputs.put("crossoverSignal", regionSignal(smoothK, doubleParam(params, "oversold", 20), doubleParam(params, "overbought", 80)));
        }
        return result(indicator, outputs);
    }

    private static List<Double> sma(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        if (period <= 0) return out;
        double sum = 0.0;
        for (int i = 0; i < values.size(); i++) {
            double v = safe(values.get(i));
            sum += v;
            if (i >= period) sum -= safe(values.get(i - period));
            if (i >= period - 1) out.set(i, sum / period);
        }
        return out;
    }

    private static List<Double> ema(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        if (period <= 0 || values.isEmpty()) return out;
        double multiplier = 2.0 / (period + 1.0);
        Double previous = null;
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            if (Double.isNaN(value)) continue;
            previous = previous == null ? value : (value - previous) * multiplier + previous;
            if (i >= period - 1) out.set(i, previous);
        }
        return out;
    }

    private static List<Double> wma(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        int denominator = period * (period + 1) / 2;
        for (int i = period - 1; i < values.size(); i++) {
            double sum = 0.0;
            for (int j = 0; j < period; j++) {
                sum += safe(values.get(i - j)) * (period - j);
            }
            out.set(i, sum / denominator);
        }
        return out;
    }

    private static List<Double> dema(List<Double> values, int period) {
        List<Double> ema1 = ema(values, period);
        List<Double> ema2 = ema(ema1, period);
        List<Double> out = fillNaN(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (!Double.isNaN(ema1.get(i)) && !Double.isNaN(ema2.get(i))) {
                out.set(i, 2.0 * ema1.get(i) - ema2.get(i));
            }
        }
        return out;
    }

    private static List<Double> tema(List<Double> values, int period) {
        List<Double> ema1 = ema(values, period);
        List<Double> ema2 = ema(ema1, period);
        List<Double> ema3 = ema(ema2, period);
        List<Double> out = fillNaN(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (!Double.isNaN(ema1.get(i)) && !Double.isNaN(ema2.get(i)) && !Double.isNaN(ema3.get(i))) {
                out.set(i, 3.0 * ema1.get(i) - 3.0 * ema2.get(i) + ema3.get(i));
            }
        }
        return out;
    }

    private static List<Double> hma(List<Double> values, int period) {
        int half = Math.max(1, period / 2);
        int root = Math.max(1, (int) Math.round(Math.sqrt(period)));
        List<Double> shortWma = wma(values, half);
        List<Double> longWma = wma(values, period);
        List<Double> diff = fillNaN(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (!Double.isNaN(shortWma.get(i)) && !Double.isNaN(longWma.get(i))) {
                diff.set(i, 2.0 * shortWma.get(i) - longWma.get(i));
            }
        }
        return wma(diff, root);
    }

    private static List<Double> tma(List<Double> values, int period) {
        int first = Math.max(1, (period + 1) / 2);
        int second = Math.max(1, period - first + 1);
        return sma(sma(values, first), second);
    }

    private static List<Double> t3(List<Double> values, int period, double volumeFactor) {
        List<Double> e1 = ema(values, period);
        List<Double> e2 = ema(e1, period);
        List<Double> e3 = ema(e2, period);
        List<Double> e4 = ema(e3, period);
        List<Double> e5 = ema(e4, period);
        List<Double> e6 = ema(e5, period);
        double a = volumeFactor;
        double c1 = -a * a * a;
        double c2 = 3 * a * a + 3 * a * a * a;
        double c3 = -6 * a * a - 3 * a - 3 * a * a * a;
        double c4 = 1 + 3 * a + a * a * a + 3 * a * a;
        List<Double> out = fillNaN(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (!Double.isNaN(e3.get(i)) && !Double.isNaN(e4.get(i)) && !Double.isNaN(e5.get(i)) && !Double.isNaN(e6.get(i))) {
                out.set(i, c1 * e6.get(i) + c2 * e5.get(i) + c3 * e4.get(i) + c4 * e3.get(i));
            }
        }
        return out;
    }

    private static List<Double> kama(List<Double> values, int period, int fastPeriod, int slowPeriod) {
        List<Double> out = fillNaN(values.size());
        if (values.size() <= period) return out;
        double fast = 2.0 / (fastPeriod + 1.0);
        double slow = 2.0 / (slowPeriod + 1.0);
        out.set(period, values.get(period));
        for (int i = period + 1; i < values.size(); i++) {
            double change = Math.abs(values.get(i) - values.get(i - period));
            double volatility = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                volatility += Math.abs(values.get(j) - values.get(j - 1));
            }
            double er = volatility == 0.0 ? 0.0 : change / volatility;
            double sc = Math.pow(er * (fast - slow) + slow, 2);
            out.set(i, out.get(i - 1) + sc * (values.get(i) - out.get(i - 1)));
        }
        return out;
    }

    private static IndicatorResult mama(List<CandleData> candles, Map<String, String> params) {
        List<Double> closes = close(candles);
        List<Double> mama = kama(closes, 10, 2, 30);
        List<Double> fama = ema(mama, 3);
        return result(INDICATORS.MESA_ADAPTIVE_MOVING_AVERAGE, Map.of("mama", mama, "fama", fama));
    }

    private static List<Double> timeSeriesForecast(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        for (int i = period - 1; i < values.size(); i++) {
            double sumX = 0.0;
            double sumY = 0.0;
            double sumXY = 0.0;
            double sumXX = 0.0;
            for (int j = 0; j < period; j++) {
                double x = j + 1;
                double y = values.get(i - period + 1 + j);
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXX += x * x;
            }
            double denominator = period * sumXX - sumX * sumX;
            if (denominator != 0.0) {
                double slope = (period * sumXY - sumX * sumY) / denominator;
                double intercept = (sumY - slope * sumX) / period;
                out.set(i, intercept + slope * period);
            }
        }
        return out;
    }

    private static List<Double> rsi(List<Double> closes, int period) {
        List<Double> out = fillNaN(closes.size());
        if (closes.size() <= period) return out;
        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change >= 0) gain += change; else loss -= change;
        }
        gain /= period;
        loss /= period;
        out.set(period, loss == 0 ? 100.0 : 100.0 - 100.0 / (1.0 + gain / loss));
        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gain = (gain * (period - 1) + Math.max(change, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-change, 0)) / period;
            out.set(i, loss == 0 ? 100.0 : 100.0 - 100.0 / (1.0 + gain / loss));
        }
        return out;
    }

    private static List<Double> atr(List<CandleData> candles, int period) {
        List<Double> tr = fillNaN(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double previousClose = i == 0 ? c.closePrice() : candles.get(i - 1).closePrice();
            tr.set(i, Math.max(c.highPrice() - c.lowPrice(), Math.max(Math.abs(c.highPrice() - previousClose), Math.abs(c.lowPrice() - previousClose))));
        }
        return ema(tr, period);
    }

    private static List<Double> roc(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        for (int i = period; i < values.size(); i++) {
            double old = values.get(i - period);
            out.set(i, old == 0 ? Double.NaN : ((values.get(i) - old) / old) * 100.0);
        }
        return out;
    }

    private static List<Double> momentum(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        for (int i = period; i < values.size(); i++) {
            out.set(i, values.get(i) - values.get(i - period));
        }
        return out;
    }

    private static List<Double> obv(List<CandleData> candles) {
        List<Double> out = fillNaN(candles.size());
        double obv = 0.0;
        for (int i = 0; i < candles.size(); i++) {
            if (i > 0) {
                if (candles.get(i).closePrice() > candles.get(i - 1).closePrice()) obv += candles.get(i).volume();
                else if (candles.get(i).closePrice() < candles.get(i - 1).closePrice()) obv -= candles.get(i).volume();
            }
            out.set(i, obv);
        }
        return out;
    }

    private static List<Double> chaikinOscillator(List<CandleData> candles, int fastPeriod, int slowPeriod) {
        List<Double> adl = fillNaN(candles.size());
        double cumulative = 0.0;
        for (int i = 0; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            double range = candle.highPrice() - candle.lowPrice();
            double multiplier = range == 0.0
                    ? 0.0
                    : ((candle.closePrice() - candle.lowPrice()) - (candle.highPrice() - candle.closePrice())) / range;
            cumulative += multiplier * candle.volume();
            adl.set(i, cumulative);
        }
        List<Double> fast = ema(adl, fastPeriod);
        List<Double> slow = ema(adl, slowPeriod);
        List<Double> out = fillNaN(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            if (!Double.isNaN(fast.get(i)) && !Double.isNaN(slow.get(i))) {
                out.set(i, fast.get(i) - slow.get(i));
            }
        }
        return out;
    }

    private static List<Double> mfi(List<CandleData> candles, int period) {
        List<Double> out = fillNaN(candles.size());
        List<Double> typical = candles.stream()
                .map(c -> (c.highPrice() + c.lowPrice() + c.closePrice()) / 3.0)
                .toList();
        for (int i = period; i < candles.size(); i++) {
            double positive = 0.0;
            double negative = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                double moneyFlow = typical.get(j) * candles.get(j).volume();
                if (typical.get(j) > typical.get(j - 1)) positive += moneyFlow;
                else if (typical.get(j) < typical.get(j - 1)) negative += moneyFlow;
            }
            out.set(i, negative == 0.0 ? 100.0 : 100.0 - 100.0 / (1.0 + positive / negative));
        }
        return out;
    }

    private static List<Double> cci(List<CandleData> candles, int period) {
        List<Double> tp = candles.stream().map(c -> (c.highPrice() + c.lowPrice() + c.closePrice()) / 3.0).toList();
        List<Double> sma = sma(tp, period);
        List<Double> out = fillNaN(candles.size());
        for (int i = period - 1; i < candles.size(); i++) {
            double md = 0.0;
            for (int j = i - period + 1; j <= i; j++) md += Math.abs(tp.get(j) - sma.get(i));
            md /= period;
            out.set(i, md == 0 ? 0.0 : (tp.get(i) - sma.get(i)) / (0.015 * md));
        }
        return out;
    }

    private static List<Double> stochasticK(List<CandleData> candles, int period) {
        List<Double> out = fillNaN(candles.size());
        for (int i = period - 1; i < candles.size(); i++) {
            double high = Double.NEGATIVE_INFINITY;
            double low = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                high = Math.max(high, candles.get(j).highPrice());
                low = Math.min(low, candles.get(j).lowPrice());
            }
            out.set(i, high == low ? 0.0 : ((candles.get(i).closePrice() - low) / (high - low)) * 100.0);
        }
        return out;
    }

    private static List<Double> stochasticOfSeries(List<Double> values, int period) {
        List<Double> out = fillNaN(values.size());
        for (int i = period - 1; i < values.size(); i++) {
            double high = Double.NEGATIVE_INFINITY;
            double low = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                high = Math.max(high, values.get(j));
                low = Math.min(low, values.get(j));
            }
            out.set(i, high == low ? 0.0 : ((values.get(i) - low) / (high - low)) * 100.0);
        }
        return out;
    }

    private static List<Double> williamsR(List<CandleData> candles, int period) {
        List<Double> k = stochasticK(candles, period);
        List<Double> out = fillNaN(k.size());
        for (int i = 0; i < k.size(); i++) if (!Double.isNaN(k.get(i))) out.set(i, k.get(i) - 100.0);
        return out;
    }

    private static List<Double> ultimateOscillator(List<CandleData> candles, int p1, int p2, int p3) {
        int n = candles.size();
        List<Double> bp = fillNaN(n);
        List<Double> tr = fillNaN(n);
        for (int i = 0; i < n; i++) {
            CandleData c = candles.get(i);
            double prevClose = i == 0 ? c.closePrice() : candles.get(i - 1).closePrice();
            bp.set(i, c.closePrice() - Math.min(c.lowPrice(), prevClose));
            tr.set(i, Math.max(c.highPrice(), prevClose) - Math.min(c.lowPrice(), prevClose));
        }
        List<Double> out = fillNaN(n);
        for (int i = Math.max(p1, Math.max(p2, p3)) - 1; i < n; i++) {
            double avg1 = averageRatio(bp, tr, i, p1);
            double avg2 = averageRatio(bp, tr, i, p2);
            double avg3 = averageRatio(bp, tr, i, p3);
            out.set(i, 100.0 * (4.0 * avg1 + 2.0 * avg2 + avg3) / 7.0);
        }
        return out;
    }

    private static Map<String, List<Double>> dmi(List<CandleData> candles, int period) {
        int n = candles.size();
        List<Double> plusDm = fillNaN(n);
        List<Double> minusDm = fillNaN(n);
        List<Double> tr = fillNaN(n);
        plusDm.set(0, 0.0);
        minusDm.set(0, 0.0);
        tr.set(0, candles.isEmpty() ? 0.0 : candles.get(0).highPrice() - candles.get(0).lowPrice());
        for (int i = 1; i < n; i++) {
            double up = candles.get(i).highPrice() - candles.get(i - 1).highPrice();
            double down = candles.get(i - 1).lowPrice() - candles.get(i).lowPrice();
            plusDm.set(i, up > down && up > 0 ? up : 0.0);
            minusDm.set(i, down > up && down > 0 ? down : 0.0);
            double prevClose = candles.get(i - 1).closePrice();
            tr.set(i, Math.max(candles.get(i).highPrice() - candles.get(i).lowPrice(), Math.max(Math.abs(candles.get(i).highPrice() - prevClose), Math.abs(candles.get(i).lowPrice() - prevClose))));
        }
        List<Double> atr = ema(tr, period);
        List<Double> plusDi = fillNaN(n);
        List<Double> minusDi = fillNaN(n);
        List<Double> plusSmooth = ema(plusDm, period);
        List<Double> minusSmooth = ema(minusDm, period);
        List<Double> dx = fillNaN(n);
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(atr.get(i)) || atr.get(i) == 0) continue;
            plusDi.set(i, 100.0 * plusSmooth.get(i) / atr.get(i));
            minusDi.set(i, 100.0 * minusSmooth.get(i) / atr.get(i));
            double sum = plusDi.get(i) + minusDi.get(i);
            if (sum != 0) dx.set(i, 100.0 * Math.abs(plusDi.get(i) - minusDi.get(i)) / sum);
        }
        return Map.of("plusDI", plusDi, "minusDI", minusDi, "adx", ema(dx, period));
    }

    private static Map<String, List<Double>> aroon(List<CandleData> candles, int period) {
        List<Double> up = fillNaN(candles.size());
        List<Double> down = fillNaN(candles.size());
        for (int i = period - 1; i < candles.size(); i++) {
            int highIndex = i;
            int lowIndex = i;
            for (int j = i - period + 1; j <= i; j++) {
                if (candles.get(j).highPrice() >= candles.get(highIndex).highPrice()) highIndex = j;
                if (candles.get(j).lowPrice() <= candles.get(lowIndex).lowPrice()) lowIndex = j;
            }
            up.set(i, 100.0 * (period - (i - highIndex)) / period);
            down.set(i, 100.0 * (period - (i - lowIndex)) / period);
        }
        return Map.of("aroonUp", up, "aroonDown", down);
    }

    private static List<Double> aroonOscillator(List<CandleData> candles, int period) {
        Map<String, List<Double>> values = aroon(candles, period);
        List<Double> up = values.get("aroonUp");
        List<Double> down = values.get("aroonDown");
        List<Double> out = fillNaN(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            if (!Double.isNaN(up.get(i)) && !Double.isNaN(down.get(i))) {
                out.set(i, up.get(i) - down.get(i));
            }
        }
        return out;
    }

    private static List<Double> parabolicSar(List<CandleData> candles, double step, double maxStep) {
        List<Double> out = fillNaN(candles.size());
        if (candles.isEmpty()) return out;
        boolean rising = true;
        double acceleration = step;
        double extreme = candles.get(0).highPrice();
        double sar = candles.get(0).lowPrice();
        out.set(0, sar);
        for (int i = 1; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            sar = sar + acceleration * (extreme - sar);
            if (rising) {
                if (candle.lowPrice() < sar) {
                    rising = false;
                    sar = extreme;
                    extreme = candle.lowPrice();
                    acceleration = step;
                } else if (candle.highPrice() > extreme) {
                    extreme = candle.highPrice();
                    acceleration = Math.min(maxStep, acceleration + step);
                }
            } else {
                if (candle.highPrice() > sar) {
                    rising = true;
                    sar = extreme;
                    extreme = candle.highPrice();
                    acceleration = step;
                } else if (candle.lowPrice() < extreme) {
                    extreme = candle.lowPrice();
                    acceleration = Math.min(maxStep, acceleration + step);
                }
            }
            out.set(i, sar);
        }
        return out;
    }

    private static Map<String, List<Double>> elderRay(List<CandleData> candles, int period) {
        List<Double> ema = ema(close(candles), period);
        List<Double> bull = fillNaN(candles.size());
        List<Double> bear = fillNaN(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            if (!Double.isNaN(ema.get(i))) {
                bull.set(i, candles.get(i).highPrice() - ema.get(i));
                bear.set(i, candles.get(i).lowPrice() - ema.get(i));
            }
        }
        return Map.of("bullPower", bull, "bearPower", bear);
    }

    private static Map<String, List<Double>> ichimoku(List<CandleData> candles, int tenkanPeriod, int kijunPeriod, int senkouBPeriod, int displacement) {
        List<Double> tenkan = midpoint(candles, tenkanPeriod, 0);
        List<Double> kijun = midpoint(candles, kijunPeriod, 0);
        List<Double> senkouA = fillNaN(candles.size());
        List<Double> senkouB = midpoint(candles, senkouBPeriod, displacement);
        List<Double> chikou = fillNaN(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            int shifted = i + displacement;
            if (shifted < candles.size() && !Double.isNaN(tenkan.get(i)) && !Double.isNaN(kijun.get(i))) {
                senkouA.set(shifted, (tenkan.get(i) + kijun.get(i)) / 2.0);
            }
            int lagged = i - displacement;
            if (lagged >= 0) {
                chikou.set(lagged, candles.get(i).closePrice());
            }
        }
        return Map.of("tenkan", tenkan, "kijun", kijun, "senkouA", senkouA, "senkouB", senkouB, "chikou", chikou);
    }

    private static List<Double> midpoint(List<CandleData> candles, int period, int shiftForward) {
        List<Double> out = fillNaN(candles.size());
        for (int i = period - 1; i < candles.size(); i++) {
            double high = Double.NEGATIVE_INFINITY;
            double low = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                high = Math.max(high, candles.get(j).highPrice());
                low = Math.min(low, candles.get(j).lowPrice());
            }
            int target = i + shiftForward;
            if (target < candles.size()) {
                out.set(target, (high + low) / 2.0);
            }
        }
        return out;
    }

    private static double averageRatio(List<Double> numerator, List<Double> denominator, int end, int period) {
        double n = 0.0;
        double d = 0.0;
        for (int i = end - period + 1; i <= end; i++) {
            n += numerator.get(i);
            d += denominator.get(i);
        }
        return d == 0.0 ? 0.0 : n / d;
    }

    private static List<Double> regionSignal(List<Double> values, double low, double high) {
        List<Double> out = fillNaN(values.size());
        out.replaceAll(v -> 0.0);
        for (int i = 1; i < values.size(); i++) {
            double previous = values.get(i - 1);
            double current = values.get(i);
            if (Double.isNaN(previous) || Double.isNaN(current)) continue;
            if (previous < low && current >= low) out.set(i, 1.0);
            else if (previous > high && current <= high) out.set(i, -1.0);
        }
        return out;
    }

    private static List<Double> close(List<CandleData> candles) {
        return candles.stream().map(CandleData::closePrice).toList();
    }

    private static List<Double> fillNaN(int size) {
        List<Double> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(Double.NaN);
        return values;
    }

    private static double stdDev(List<Double> values, int start, int end, double mean) {
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= end; i++) {
            sum += Math.pow(values.get(i) - mean, 2);
            count++;
        }
        return count == 0 ? 0.0 : Math.sqrt(sum / count);
    }

    private static double safe(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }

    private static int intParam(Map<String, String> params, String key, int fallback) {
        try { return Integer.parseInt(params.getOrDefault(key, Integer.toString(fallback))); }
        catch (Exception ignored) { return fallback; }
    }

    private static double doubleParam(Map<String, String> params, String key, double fallback) {
        try { return Double.parseDouble(params.getOrDefault(key, Double.toString(fallback))); }
        catch (Exception ignored) { return fallback; }
    }

    private static IndicatorResult result(INDICATORS indicator, String key, List<Double> values) {
        return result(indicator, Map.of(key, values));
    }

    private static IndicatorResult result(INDICATORS indicator, Map<String, List<Double>> outputs) {
        return new IndicatorResult(indicator, outputs, Map.of());
    }

    private interface Calculation {
        IndicatorResult calculate(List<CandleData> candles, Map<String, String> parameters);
    }

    private record FunctionalCalculator(INDICATORS indicator, Calculation calculation) implements IndicatorCalculator {
        @Override
        public IndicatorResult calculate(List<CandleData> candles, Map<String, String> parameters) {
            return calculation.calculate(candles == null ? List.of() : candles, parameters == null ? Map.of() : parameters);
        }
    }

    private record UnsupportedCalculator(INDICATORS indicator) implements IndicatorCalculator {
        @Override
        public IndicatorResult calculate(List<CandleData> candles, Map<String, String> parameters) {
            throw new UnsupportedOperationException("Indicator calculation not implemented yet: "
                    + (indicator == null ? "Unknown" : indicator.getDisplayName()));
        }
    }
}
