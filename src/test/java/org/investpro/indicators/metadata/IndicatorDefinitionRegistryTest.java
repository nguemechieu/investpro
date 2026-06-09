package org.investpro.indicators.metadata;

import org.investpro.indicators.INDICATORS;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IndicatorDefinitionRegistryTest {

    @Test
    void providesDefinitionForEveryIndicator() {
        EnumSet<INDICATORS> seen = EnumSet.noneOf(INDICATORS.class);

        for (IndicatorDefinition definition : IndicatorDefinition.all()) {
            assertNotNull(definition.indicator());
            assertFalse(definition.displayName().isBlank(), definition.indicator().name());
            assertFalse(definition.description().isBlank(), definition.indicator().name());
            assertNotNull(definition.category(), definition.indicator().name());
            assertFalse(definition.outputs().isEmpty(), definition.indicator().name());
            seen.add(definition.indicator());
        }

        for (INDICATORS indicator : INDICATORS.values()) {
            assertFalse(!seen.contains(indicator), indicator.name() + " is missing metadata");
        }
    }

    @Test
    void exposesDefaultParametersForCatalogIndicators() {
        assertHasParameter(INDICATORS.RSI, "period", "14");
        assertHasParameter(INDICATORS.MACD, "fastPeriod", "12");
        assertHasParameter(INDICATORS.WILLIAMS_R, "oversold", "-80");
        assertHasParameter(INDICATORS.STOCH_RSI_REGION_CROSSOVER, "crossoverMode", "REGION_EXIT");
    }

    private static void assertHasParameter(INDICATORS indicator, String name, String defaultValue) {
        IndicatorDefinition definition = IndicatorDefinition.get(indicator);
        boolean found = definition.parameters().stream()
                .anyMatch(parameter -> parameter.name().equals(name)
                        && parameter.defaultValue().equals(defaultValue));
        assertFalse(!found, indicator.name() + " missing " + name);
    }
}
