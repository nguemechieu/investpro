package org.investpro.indicators.metadata;

import org.investpro.indicators.INDICATORS;
import org.investpro.indicators.IndicatorCatalog;

import java.util.List;

public record IndicatorDefinition(
        INDICATORS indicator,
        String id,
        String displayName,
        String description,
        INDICATORS.IndicatorCategory category,
        IndicatorCatalog.IndicatorRenderTarget renderTarget,
        List<IndicatorParameterDefinition> parameters,
        List<String> outputs) {
    public IndicatorDefinition {
        indicator = indicator == null ? INDICATORS.UNKNOWN : indicator;
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? "" : description;
        category = category == null ? INDICATORS.IndicatorCategory.UNKNOWN : category;
        renderTarget = renderTarget == null
                ? IndicatorCatalog.IndicatorRenderTarget.SEPARATE_PANE
                : renderTarget;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    public static List<IndicatorDefinition> all() {
        return IndicatorCatalog.all();
    }

    public static IndicatorDefinition get(INDICATORS indicator) {
        return IndicatorCatalog.get(indicator);
    }
}