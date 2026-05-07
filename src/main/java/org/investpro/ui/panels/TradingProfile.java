package org.investpro.ui.panels;

import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;

import java.util.prefs.Preferences;

public record TradingProfile(
        String traderName,
        RiskProfile riskProfile,
        String tradingStyle,
        double dailyLossLimit,
        double maxPositionSize,
        int maxOpenPositions,
        boolean autoTradingEnabled,
        boolean advancedOrdersEnabled,
        MarketBehavior marketBehavior,
        ExecutionStrategy executionStrategy,
        LiquidityProfile liquidityProfile,
        PsychologyProfile psychologyProfile,
        ProbabilityLevel probabilityLevel,
        CapitalProtection capitalProtection,
        SystemDesign systemDesign,
        String description,
        // OpenAI Configuration
        boolean enableOpenaiIntegration,
        String openaiApiKey,
        String openaiModel,
        double openaiTemperature) {

    private static final Preferences PREFS = Preferences.userNodeForPackage(TradingProfile.class);

    public static TradingProfile defaults() {
        return new TradingProfile(
                System.getProperty("user.name", "Trader"),
                RiskProfile.MODERATE,
                "Swing Trading",
                1000.0,
                5000.0,
                10,
                false,
                false,
                MarketBehavior.TRENDING_UP,
                ExecutionStrategy.LIMIT_ORDER,
                LiquidityProfile.NORMAL,
                PsychologyProfile.DISCIPLINED,
                ProbabilityLevel.HIGH,
                CapitalProtection.STRICT_STOPS,
                SystemDesign.HYBRID_SYSTEM,
                "",
                false,
                "",
                "gpt-3.5-turbo",
                0.7);
    }

    public static TradingProfile load() {
        TradingProfile defaults = defaults();
        return new TradingProfile(
                PREFS.get("traderName", defaults.traderName()),
                enumValue(RiskProfile.class, PREFS.get("riskProfile", defaults.riskProfile().name()),
                        defaults.riskProfile()),
                PREFS.get("tradingStyle", defaults.tradingStyle()),
                PREFS.getDouble("dailyLossLimit", defaults.dailyLossLimit()),
                PREFS.getDouble("maxPositionSize", defaults.maxPositionSize()),
                PREFS.getInt("maxOpenPositions", defaults.maxOpenPositions()),
                PREFS.getBoolean("autoTradingEnabled", defaults.autoTradingEnabled()),
                PREFS.getBoolean("advancedOrdersEnabled", defaults.advancedOrdersEnabled()),
                enumValue(MarketBehavior.class, PREFS.get("marketBehavior", defaults.marketBehavior().name()),
                        defaults.marketBehavior()),
                enumValue(ExecutionStrategy.class, PREFS.get("executionStrategy", defaults.executionStrategy().name()),
                        defaults.executionStrategy()),
                enumValue(LiquidityProfile.class, PREFS.get("liquidityProfile", defaults.liquidityProfile().name()),
                        defaults.liquidityProfile()),
                enumValue(PsychologyProfile.class, PREFS.get("psychologyProfile", defaults.psychologyProfile().name()),
                        defaults.psychologyProfile()),
                enumValue(ProbabilityLevel.class, PREFS.get("probabilityLevel", defaults.probabilityLevel().name()),
                        defaults.probabilityLevel()),
                enumValue(CapitalProtection.class, PREFS.get("capitalProtection", defaults.capitalProtection().name()),
                        defaults.capitalProtection()),
                enumValue(SystemDesign.class, PREFS.get("systemDesign", defaults.systemDesign().name()),
                        defaults.systemDesign()),
                PREFS.get("description", defaults.description()),
                PREFS.getBoolean("enableOpenaiIntegration", defaults.enableOpenaiIntegration()),
                PREFS.get("openaiApiKey", defaults.openaiApiKey()),
                PREFS.get("openaiModel", defaults.openaiModel()),
                PREFS.getDouble("openaiTemperature", defaults.openaiTemperature()));
    }

    public void save() {
        PREFS.put("traderName", safe(traderName));
        PREFS.put("riskProfile", riskProfile.name());
        PREFS.put("tradingStyle", safe(tradingStyle));
        PREFS.putDouble("dailyLossLimit", Math.max(0.0, dailyLossLimit));
        PREFS.putDouble("maxPositionSize", Math.max(0.0, maxPositionSize));
        PREFS.putInt("maxOpenPositions", Math.max(1, maxOpenPositions));
        PREFS.putBoolean("autoTradingEnabled", autoTradingEnabled);
        PREFS.putBoolean("advancedOrdersEnabled", advancedOrdersEnabled);
        PREFS.put("marketBehavior", marketBehavior.name());
        PREFS.put("executionStrategy", executionStrategy.name());
        PREFS.put("liquidityProfile", liquidityProfile.name());
        PREFS.put("psychologyProfile", psychologyProfile.name());
        PREFS.put("probabilityLevel", probabilityLevel.name());
        PREFS.put("capitalProtection", capitalProtection.name());
        PREFS.put("systemDesign", systemDesign.name());
        PREFS.put("description", safe(description));
        // Save OpenAI configuration
        PREFS.putBoolean("enableOpenaiIntegration", enableOpenaiIntegration);
        PREFS.put("openaiApiKey", safe(openaiApiKey));
        PREFS.put("openaiModel", safe(openaiModel));
        PREFS.putDouble("openaiTemperature", Math.max(0.0, Math.min(2.0, openaiTemperature)));
    }

    public static void reset() {
        defaults().save();
    }

    public double maxRiskPerTradePercent() {
        return Math.max(0.1, (riskProfile.getMaxPositionSizePercent() / 100.0) * 20.0);
    }

    public double maxCumulativeRiskPercent() {
        return Math.max(maxRiskPerTradePercent(), riskProfile.getMaxPortfolioHeatPercent());
    }

    public double maxDrawdownPercent() {
        double profileDrawdown = riskProfile.getMaxDrawdownThresholdPercent();
        if (dailyLossLimit <= 0.0 || maxPositionSize <= 0.0) {
            return profileDrawdown;
        }
        return Math.min(profileDrawdown, (dailyLossLimit / maxPositionSize) * 100.0);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String name, E fallback) {
        try {
            return Enum.valueOf(enumType, name);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
