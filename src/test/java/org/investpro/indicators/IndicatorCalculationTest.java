package org.investpro.indicators;

import org.investpro.data.CandleData;
import org.investpro.spi.PluginIndicatorFactory;
import org.investpro.spi.PluginRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorCalculationTest {

    @Test
    void atrHandlesShortHistoryWithoutThrowing() {
        ATRIndicator indicator = new ATRIndicator(14);

        indicator.calculate(candles(5));

        assertTrue(indicator.isCalculated());
        assertEquals(5, indicator.getValues().get("ATR").length);
    }

    @Test
    void obvUsesLinearCalculationAndReturnsOneValuePerCandle() {
        OBVIndicator indicator = new OBVIndicator();

        indicator.calculate(candles(4));

        double[] values = indicator.getValues().get("OBV");
        assertEquals(4, values.length);
        assertEquals(1_000.0, values[0]);
        assertTrue(values[3] > values[0]);
    }

    @Test
    void factoryCreatesAdditionalIndicators() {
        PluginRegistry registry = PluginRegistry.of(List.of(), List.of(), List.of(), List.of(), List.of());
        assertNotNull(PluginIndicatorFactory.create("ROC 12", registry).orElse(null));
        assertNotNull(PluginIndicatorFactory.create("Williams %R 14", registry).orElse(null));
        assertNotNull(PluginIndicatorFactory.create("MFI 14", registry).orElse(null));
    }

    private static List<CandleData> candles(int count) {
        List<CandleData> candles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double price = 100.0 + i;
            candles.add(new CandleData(price, price + 0.5, price + 1.0, price - 1.0, i, 1_000.0 + i));
        }
        return candles;
    }
}
