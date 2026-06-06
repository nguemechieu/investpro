package org.investpro.terminal.config;

public record BacktestConfig(
        int maxConcurrency,
        boolean requireConfirmationForBulkRuns,
        boolean cacheCandles,
        long defaultTimeoutSeconds
) {
    public static BacktestConfig load() {
        return new BacktestConfig(
                InvestProConfig.integer("investpro.backtest.maxConcurrency", 2),
                InvestProConfig.bool("investpro.backtest.requireConfirmationForBulkRuns", true),
                InvestProConfig.bool("investpro.backtest.cacheCandles", true),
                InvestProConfig.longValue("investpro.backtest.defaultTimeoutSeconds", 120L));
    }
}
