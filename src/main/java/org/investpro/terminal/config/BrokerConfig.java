package org.investpro.terminal.config;

public record BrokerConfig(
        String defaultProvider,
        boolean liveTradingEnabled,
        int maxConnectedBrokers,
        long reconciliationIntervalSeconds
) {
    public static BrokerConfig load() {
        return new BrokerConfig(
                InvestProConfig.text("investpro.broker.defaultProvider", "OANDA"),
                InvestProConfig.bool("investpro.broker.liveTradingEnabled", false),
                InvestProConfig.integer("investpro.broker.maxConnectedBrokers", 3),
                InvestProConfig.longValue("investpro.broker.reconciliationIntervalSeconds", 300L));
    }
}
