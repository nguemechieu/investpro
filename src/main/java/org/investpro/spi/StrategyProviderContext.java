package org.investpro.spi;

import org.investpro.risk.RiskManagementSystem;
import org.investpro.strategy.StrategyEngine;

import java.util.Map;
import java.util.Optional;

public record StrategyProviderContext(
        StrategyEngine strategyEngine,
        PluginRegistry pluginRegistry,
        RiskManagementSystem riskManagementSystem,
        Map<String, String> config) {

    public StrategyProviderContext {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public Optional<String> configValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.get(key));
    }
}
