package org.investpro.strategy.provider;

import org.investpro.decision.MarketRegime;
import org.investpro.enums.AssetClass;
import org.investpro.enums.timeframe.Timeframe;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable discovery metadata for a strategy without requiring construction of
 * the strategy instance itself.
 */
public record StrategyDescriptor(
        String strategyId,
        String displayName,
        Set<AssetClass> assetClasses,
        Set<Timeframe> supportedTimeframes,
        Set<MarketRegime> supportedRegimes,
        Set<String> supportedSessions,
        StrategyComplexity cpuComplexity,
        StrategyComplexity memoryComplexity,
        boolean aiEnhanced,
        boolean requiresTraining,
        boolean supportsLiveTrading,
        boolean supportsPaperTrading,
        boolean supportsBacktesting,
        Instant registeredAt) {

    public StrategyDescriptor {
        strategyId = safe(strategyId);
        displayName = displayName == null || displayName.isBlank() ? strategyId : displayName.trim();
        assetClasses = assetClasses == null ? Set.of() : Set.copyOf(assetClasses);
        supportedTimeframes = supportedTimeframes == null ? Set.of() : Set.copyOf(supportedTimeframes);
        supportedRegimes = supportedRegimes == null ? Set.of() : Set.copyOf(supportedRegimes);
        supportedSessions = supportedSessions == null ? Set.of() : Set.copyOf(supportedSessions);
        cpuComplexity = cpuComplexity == null ? StrategyComplexity.MEDIUM : cpuComplexity;
        memoryComplexity = memoryComplexity == null ? StrategyComplexity.MEDIUM : memoryComplexity;
        registeredAt = registeredAt == null ? Instant.now() : registeredAt;
    }

    public static StrategyDescriptor lightweight(String strategyId, String displayName) {
        return new StrategyDescriptor(strategyId, displayName, Set.of(), Set.of(), Set.of(), Set.of(),
                StrategyComplexity.LIGHT, StrategyComplexity.LIGHT,
                false, false, true, true, true, Instant.now());
    }

    public boolean supportsAssetClass(AssetClass assetClass) {
        return assetClass == null || assetClasses.isEmpty() || assetClasses.contains(assetClass);
    }

    public boolean supportsTimeframe(Timeframe timeframe) {
        return timeframe == null || supportedTimeframes.isEmpty() || supportedTimeframes.contains(timeframe);
    }

    public boolean supportsRegime(MarketRegime regime) {
        return regime == null || supportedRegimes.isEmpty() || supportedRegimes.contains(regime);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
