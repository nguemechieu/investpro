package org.investpro.strategy;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyCatalogCandleReversalConfirmationTest {

    @Test
    void candleReversalConfirmationIsRegisteredWithBalancedRules() {
        StrategyDefinition definition = StrategyCatalog.definition("Candle Reversal Confirmation");

        assertNotNull(definition);
        assertEquals("Candle Reversal Confirmation", definition.getName());
        assertEquals("Candle Reversal Confirmation", definition.getBaseName());
        assertEquals(14, definition.getRules().size());
        assertTrue(StrategyCatalog.availableStrategyNames().contains("Candle Reversal Confirmation"));
        assertEquals(7L, definition.getRules().stream().filter(rule -> rule.signalType() == SignalType.BUY).count());
        assertEquals(7L, definition.getRules().stream().filter(rule -> rule.signalType() == SignalType.SELL).count());
        assertEquals(8L, definition.getRules().stream()
                .filter(rule -> rule.ruleSource() == StrategyRuleSource.CANDLE_PATTERN).count());
        assertEquals(6L, definition.getRules().stream()
                .filter(rule -> rule.ruleSource() == StrategyRuleSource.INDICATOR).count());
        assertTrue(definition.getRules().stream()
                .filter(rule -> rule.ruleSource() == StrategyRuleSource.INDICATOR)
                .allMatch(rule -> rule.timeframe() == Timeframe.H1));
        assertEquals("14", definition.getRules().stream()
                .filter(rule -> rule.ruleSource() == StrategyRuleSource.INDICATOR)
                .filter(rule -> rule.signalType() == SignalType.BUY)
                .filter(rule -> rule.indicator() != null)
                .findFirst()
                .orElseThrow()
                .parameters()
                .get("period"));
        assertEquals("200", definition.getRules().stream()
                .filter(rule -> rule.ruleSource() == StrategyRuleSource.INDICATOR)
                .filter(rule -> rule.indicator() != null)
                .filter(rule -> "200".equals(rule.parameters().get("period")))
                .findFirst()
                .orElseThrow()
                .parameters()
                .get("period"));
        assertTrue(definition.getRules().stream()
                .anyMatch(rule -> Map.of("confirmatorySide", "BUY").equals(rule.parameters())
                        || Map.of("confirmatorySide", "SELL").equals(rule.parameters())));
    }
}