package org.investpro.indicators.engine;

import org.investpro.data.CandleData;
import org.investpro.indicators.INDICATORS;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndicatorCalculatorRegistryTest {

    private final IndicatorCalculatorRegistry registry = IndicatorCalculatorRegistry.getInstance();

    @Test
    void calculatesCoreIndicatorOutputs() {
        List<CandleData> candles = candles(80);

        assertOutput(INDICATORS.SMA, candles, Map.of("period", "5"), "sma");
        assertOutput(INDICATORS.EMA, candles, Map.of("period", "5"), "ema");
        assertOutput(INDICATORS.RSI, candles, Map.of("period", "14"), "rsi");
        assertOutput(INDICATORS.ATR, candles, Map.of("period", "14"), "atr");
        assertOutput(INDICATORS.MACD, candles, Map.of("fastPeriod", "12", "slowPeriod", "26", "signalPeriod", "9"), "histogram");
        assertOutput(INDICATORS.MACD_SIGNAL, candles, Map.of("fastPeriod", "12", "slowPeriod", "26", "signalPeriod", "9"), "histogram");
        assertOutput(INDICATORS.BOLLINGER_BANDS, candles, Map.of("period", "20", "stdDevMult", "2.0"), "percentB");
        assertOutput(INDICATORS.WILLIAMS_R, candles, Map.of("period", "14"), "williamsR");
        assertOutput(INDICATORS.DMI, candles, Map.of("period", "14"), "plusDI");
        assertOutput(INDICATORS.APO, candles, Map.of("fastPeriod", "12", "slowPeriod", "26"), "apo");
        assertOutput(INDICATORS.AROON, candles, Map.of("period", "25"), "aroonUp");
        assertOutput(INDICATORS.CHAIKIN_AD_OSCILLATOR, candles, Map.of("fastPeriod", "3", "slowPeriod", "10"), "chaikinOscillator");
        assertOutput(INDICATORS.DEMA, candles, Map.of("period", "10"), "dema");
        assertOutput(INDICATORS.HMA, candles, Map.of("period", "10"), "hma");
        assertOutput(INDICATORS.ICHIMOKU, candles, Map.of("tenkan", "9", "kijun", "26", "senkouB", "52", "displacement", "10"), "tenkan");
        assertOutput(INDICATORS.KAMA, candles, Map.of("period", "10", "fastPeriod", "2", "slowPeriod", "30"), "kama");
        assertOutput(INDICATORS.MFI, candles, Map.of("period", "14"), "mfi");
        assertOutput(INDICATORS.PARABOLIC_SAR, candles, Map.of("step", "0.02", "maxStep", "0.2"), "psar");
        assertOutput(INDICATORS.T3_TILLSON, candles, Map.of("period", "5", "volumeFactor", "0.7"), "t3");
        assertOutput(INDICATORS.TIME_SERIES_FORECAST, candles, Map.of("period", "14"), "tsf");
        assertOutput(INDICATORS.ULTIMATE_OSCILLATOR, candles, Map.of("period1", "7", "period2", "14", "period3", "28"), "ultimateOscillator");
    }

    @Test
    void unsupportedIndicatorsFailClearly() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> registry.calculate(INDICATORS.UNKNOWN, candles(40), Map.of()));

        assertFalse(exception.getMessage().isBlank());
    }

    private void assertOutput(
            INDICATORS indicator,
            List<CandleData> candles,
            Map<String, String> parameters,
            String key) {
        IndicatorResult result = registry.calculate(indicator, candles, parameters);

        assertEquals(indicator, result.indicator());
        assertFalse(result.outputs().isEmpty());
        assertEquals(candles.size(), result.outputs().get(key).size());
        assertFalse(Double.isNaN(last(result.outputs().get(key))), indicator.name() + " produced no final value");
    }

    private static double last(List<Double> values) {
        return values.get(values.size() - 1);
    }

    private static List<CandleData> candles(int count) {
        List<CandleData> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = 100.0 + i + Math.sin(i / 4.0);
            double open = close - 0.4;
            candles.add(new CandleData(
                    open,
                    close,
                    close + 1.5,
                    open - 1.2,
                    i,
                    1_000 + i * 10));
        }
        return candles;
    }
}
