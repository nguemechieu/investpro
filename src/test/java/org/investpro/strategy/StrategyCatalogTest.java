package org.investpro.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StrategyCatalog.
 */
@DisplayName("Strategy Catalog Tests")
class StrategyCatalogTest {

    @BeforeEach
    void setUp() {
        // Ensure catalog is initialized
        StrategyCatalog.availableStrategyNames();
    }

    @Test
    @DisplayName("Core strategy names should not be empty")
    void testCoreStrategyNames() {
        assertFalse(StrategyCatalog.CORE_STRATEGY_NAMES.isEmpty());
        assertEquals(17, StrategyCatalog.CORE_STRATEGY_NAMES.size());
        assertTrue(StrategyCatalog.CORE_STRATEGY_NAMES.contains("Trend Following"));
        assertTrue(StrategyCatalog.CORE_STRATEGY_NAMES.contains("Mean Reversion"));
        assertTrue(StrategyCatalog.CORE_STRATEGY_NAMES.contains("Breakout"));
    }

    @Test
    @DisplayName("Alias resolution should work")
    void testAliasResolution() {
        assertEquals("Trend Following", StrategyCatalog.STRATEGY_ALIASES.get("DEFAULT"));
        assertEquals("Trend Following", StrategyCatalog.STRATEGY_ALIASES.get("TREND"));
        assertEquals("Mean Reversion", StrategyCatalog.STRATEGY_ALIASES.get("MEAN REVERSION"));
        assertEquals("Breakout", StrategyCatalog.STRATEGY_ALIASES.get("BREAKOUT"));
    }

    @Test
    @DisplayName("Strategy name normalization should resolve aliases")
    void testNormalizeStrategyName() {
        assertEquals("Trend Following", StrategyCatalog.normalizeStrategyName("TREND"));
        assertEquals("Mean Reversion", StrategyCatalog.normalizeStrategyName("MEAN REVERSION"));
        assertEquals("Trend Following", StrategyCatalog.normalizeStrategyName("trend following"));
    }

    @Test
    @DisplayName("Definition lookup should work for base strategies")
    void testDefinitionLookup() {
        StrategyDefinition def = StrategyCatalog.definition("Trend Following");
        assertNotNull(def);
        assertEquals("Trend Following", def.getBaseName());
        assertNotNull(def.getParameters());
    }

    @Test
    @DisplayName("Base strategy name resolution should work")
    void testResolveBaseStrategyName() {
        assertEquals("Trend Following", StrategyCatalog.resolveBaseStrategyName("Trend Following"));
        // Test a variant name with pipes
        String variantName = StrategyCatalog.availableStrategyNames().stream()
                .filter(n -> n.contains("|"))
                .findFirst()
                .orElse("Trend Following");
        String baseName = StrategyCatalog.resolveBaseStrategyName(variantName);
        assertTrue(StrategyCatalog.CORE_STRATEGY_NAMES.contains(baseName));
    }

    @Test
    @DisplayName("Available strategy names should not be empty")
    void testAvailableStrategyNames() {
        var names = StrategyCatalog.availableStrategyNames();
        assertFalse(names.isEmpty());
        assertTrue(names.contains("Trend Following"));
    }

    @Test
    @DisplayName("Total strategies should be greater than core count")
    void testTotalStrategies() {
        int total = StrategyCatalog.availableStrategyNames().size();
        assertTrue(total > StrategyCatalog.CORE_STRATEGY_NAMES.size());
        // Should have base + variants
        assertTrue(total > 100);
    }

    @Test
    @DisplayName("Strategy parameters should be mergeable")
    void testParameterMerge() {
        StrategyParameters base = StrategyParameters.builder()
                .rsiPeriod(14)
                .emaFast(20)
                .build();

        StrategyParameters override = StrategyParameters.builder()
                .rsiPeriod(10)
                .build();

        StrategyParameters merged = base.merge(override);
        assertEquals(10, merged.getRsiPeriod());
        assertEquals(20, merged.getEmaFast()); // Should keep base value
    }

    @Test
    @DisplayName("Feature pipeline config should be creatable from parameters")
    void testFeaturePipelineConfig() {
        StrategyParameters params = StrategyParameters.builder()
                .rsiPeriod(14)
                .emaFast(20)
                .emaSlow(50)
                .atrPeriod(14)
                .breakoutLookback(20)
                .build();

        FeaturePipelineConfig config = FeaturePipelineConfig.from(params);
        assertEquals(14, config.getRsiPeriod());
        assertEquals(20, config.getEmaFast());
        assertEquals(50, config.getEmaSlow());
    }
}
