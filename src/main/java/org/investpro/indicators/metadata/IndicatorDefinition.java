package org.investpro.indicators.metadata;

import org.investpro.indicators.INDICATORS;

import java.util.List;

public record IndicatorDefinition(
        INDICATORS indicator,
        String displayName,
        String description,
        INDICATORS.IndicatorCategory category,
        List<IndicatorParameterDefinition> parameters,
        List<String> outputs) {
}
