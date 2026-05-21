package org.investpro.spi;

public interface RiskModuleProvider extends InvestProPlugin {
    RiskModule create(RiskModuleProviderContext context);
}
