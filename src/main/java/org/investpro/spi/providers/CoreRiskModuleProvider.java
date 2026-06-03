package org.investpro.spi.providers;

import org.investpro.spi.RiskModule;
import org.investpro.spi.RiskModuleProvider;
import org.investpro.spi.RiskModuleProviderContext;

/**
 * Built-in risk module provider so the plugin manager can discover a default
 * risk module entry.
 */
public final class CoreRiskModuleProvider implements RiskModuleProvider {

    @Override
    public String id() {
        return "core-risk";
    }

    @Override
    public String displayName() {
        return "Core Risk Module";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public RiskModule create(RiskModuleProviderContext context) {
        return () -> "core-risk";
    }
}