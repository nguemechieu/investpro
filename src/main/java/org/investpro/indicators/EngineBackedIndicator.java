package org.investpro.indicators;

import org.investpro.data.CandleData;
import org.investpro.indicators.engine.IndicatorCalculatorRegistry;
import org.investpro.indicators.engine.IndicatorResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chart indicator wrapper backed by the centralized indicator calculator
 * registry.
 */
public final class EngineBackedIndicator extends BaseIndicator {

    private final INDICATORS indicatorType;
    private final Map<String, String> parameters;

    public EngineBackedIndicator(INDICATORS indicatorType, Map<String, String> parameters) {
        super(indicatorType == null ? INDICATORS.UNKNOWN.getDisplayName() : indicatorType.getDisplayName(),
                resolvePeriod(parameters));
        this.indicatorType = indicatorType == null ? INDICATORS.UNKNOWN : indicatorType;
        this.parameters = parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            reset();
            return;
        }

        IndicatorResult result = IndicatorCalculatorRegistry.getInstance()
                .calculate(indicatorType, candles, parameters);

        Map<String, double[]> computed = new LinkedHashMap<>();
        if (result != null && result.outputs() != null) {
            result.outputs().forEach((key, values) -> {
                String seriesName = (key == null || key.isBlank()) ? indicatorType.getDisplayName() : key;
                computed.put(seriesName, toSeries(values, candles.size()));
            });
        }

        this.values.clear();
        this.values.putAll(computed);
        this.calculated = !this.values.isEmpty();
    }

    private static int resolvePeriod(Map<String, String> parameters) {
        if (parameters == null) {
            return 0;
        }
        try {
            return Integer.parseInt(parameters.getOrDefault("period", "0").trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double[] toSeries(List<Double> values, int expectedLength) {
        int length = Math.max(expectedLength, values == null ? 0 : values.size());
        double[] array = new double[length];
        java.util.Arrays.fill(array, Double.NaN);
        if (values == null) {
            return array;
        }
        for (int index = 0; index < values.size() && index < array.length; index++) {
            Double value = values.get(index);
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            array[index] = value;
        }
        return array;
    }
}
