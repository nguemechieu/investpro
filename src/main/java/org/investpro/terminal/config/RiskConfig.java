package org.investpro.terminal.config;

public record RiskConfig(
        boolean requireBacktestBeforeLive,
        boolean requirePaperTradingBeforeLive,
        boolean autoAssignBestStrategy,
        int minStrategyScore,
        int topCandidates,
        double maxDailyLossPercent,
        double maxPositionSizePercent,
        double maxPortfolioExposurePercent,
        double maxSpreadPercent,
        boolean closeForexBeforeWeekend,
        double smallAccountThreshold,
        int smallAccountMaxUnits
) {
    public static RiskConfig load() {
        return new RiskConfig(
                InvestProConfig.bool("investpro.risk.requireBacktestBeforeLive", true),
                InvestProConfig.bool("investpro.risk.requirePaperTradingBeforeLive", true),
                InvestProConfig.bool("investpro.risk.autoAssignBestStrategy", false),
                InvestProConfig.integer("investpro.risk.minStrategyScore", 70),
                InvestProConfig.integer("investpro.risk.topCandidates", 5),
                InvestProConfig.decimal("investpro.risk.maxDailyLossPercent", 3.0),
                InvestProConfig.decimal("investpro.risk.maxPositionSizePercent", 10.0),
                InvestProConfig.decimal("investpro.risk.maxPortfolioExposurePercent", 80.0),
                InvestProConfig.decimal("investpro.risk.maxSpreadPercent", 0.30),
                InvestProConfig.bool("investpro.risk.closeForexBeforeWeekend", true),
                InvestProConfig.decimal("investpro.risk.smallAccountThreshold", 100.0),
                InvestProConfig.integer("investpro.risk.smallAccountMaxUnits", 1));
    }
}
