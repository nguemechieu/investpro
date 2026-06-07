package org.investpro.agent.symbol;

import java.time.Duration;

public record SymbolAgentConfig(
        int warmupCandles,
        int maxCandlesInMemory,
        boolean autoEvaluateOnCandleClose,
        boolean autoImproveEnabled,
        boolean allowLiveTrading,
        boolean requireTradabilityCheck,
        boolean blockIfNewsCritical,
        Duration evaluationCooldown,
        Duration strategyReviewInterval,
        Duration staleMarketDataTimeout,
        boolean duplicateOrderProtection) {

    public SymbolAgentConfig {
        warmupCandles = Math.max(1, warmupCandles);
        maxCandlesInMemory = Math.max(warmupCandles, maxCandlesInMemory);
        evaluationCooldown = evaluationCooldown == null ? Duration.ofSeconds(10) : evaluationCooldown;
        strategyReviewInterval = strategyReviewInterval == null ? Duration.ofHours(1) : strategyReviewInterval;
        staleMarketDataTimeout = staleMarketDataTimeout == null ? Duration.ofMinutes(5) : staleMarketDataTimeout;
    }

    public static SymbolAgentConfig defaults() {
        return new SymbolAgentConfig(
                100,
                1000,
                true,
                false,
                false,
                true,
                true,
                Duration.ofSeconds(10),
                Duration.ofHours(1),
                Duration.ofMinutes(5),
                true);
    }
}
