package org.investpro.indicators.metadata;

public record IndicatorParameterDefinition(
        String name,
        String displayName,
        String defaultValue,
        String type,
        String description,
        boolean required) {
}
