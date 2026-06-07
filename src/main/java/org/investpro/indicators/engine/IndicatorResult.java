package org.investpro.indicators.engine;

import org.investpro.indicators.INDICATORS;

import java.util.List;
import java.util.Map;

public record IndicatorResult(
        INDICATORS indicator,
        Map<String, List<Double>> outputs,
        Map<String, String> metadata) {
}
