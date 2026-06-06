package org.investpro.terminal.config;

import org.investpro.config.AppConfig;

public record InvestProConfig(
        RiskConfig risk,
        AiConfig ai,
        BrokerConfig broker,
        UiConfig ui,
        BacktestConfig backtest,
        PersistenceConfig persistence
) {
    public static InvestProConfig load() {
        return new InvestProConfig(
                RiskConfig.load(),
                AiConfig.load(),
                BrokerConfig.load(),
                UiConfig.load(),
                BacktestConfig.load(),
                PersistenceConfig.load());
    }

    static boolean bool(String key, boolean defaultValue) {
        return AppConfig.getBoolean(key, defaultValue);
    }

    static int integer(String key, int defaultValue) {
        return AppConfig.getInt(key, defaultValue);
    }

    static long longValue(String key, long defaultValue) {
        return AppConfig.getLong(key, defaultValue);
    }

    static double decimal(String key, double defaultValue) {
        return AppConfig.getDouble(key, defaultValue);
    }

    static String text(String key, String defaultValue) {
        return AppConfig.get(key, defaultValue);
    }
}
