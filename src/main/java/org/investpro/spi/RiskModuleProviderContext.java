package org.investpro.spi;

import org.investpro.risk.RiskManagementSystem;

import java.util.Map;
import java.util.Optional;

public record RiskModuleProviderContext(
        RiskManagementSystem riskManagementSystem,
        Map<String, String> config) {

    public RiskModuleProviderContext {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public Optional<String> configValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.get(key));
    }
}
