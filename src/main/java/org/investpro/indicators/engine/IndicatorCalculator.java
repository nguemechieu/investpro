package org.investpro.indicators.engine;

import org.investpro.data.CandleData;
import org.investpro.indicators.INDICATORS;

import java.util.List;
import java.util.Map;

public interface IndicatorCalculator {
    INDICATORS indicator();

    IndicatorResult calculate(List<CandleData> candles, Map<String, String> parameters);
}
