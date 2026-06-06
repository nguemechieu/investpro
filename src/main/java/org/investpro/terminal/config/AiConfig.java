package org.investpro.terminal.config;

public record AiConfig(
        boolean enabled,
        String host,
        int port,
        long timeoutMs,
        boolean remoteEnabled,
        double minConfidence,
        double highConfidence
) {
    public static AiConfig load() {
        return new AiConfig(
                InvestProConfig.bool("investpro.ai.enabled", false),
                InvestProConfig.text("investpro.ai.host", "127.0.0.1"),
                InvestProConfig.integer("investpro.ai.port", 8010),
                InvestProConfig.longValue("investpro.ai.timeoutMs", 1500L),
                InvestProConfig.bool("investpro.ai.remoteEnabled", false),
                InvestProConfig.decimal("investpro.ai.minConfidence", 0.60),
                InvestProConfig.decimal("investpro.ai.highConfidence", 0.80));
    }
}
