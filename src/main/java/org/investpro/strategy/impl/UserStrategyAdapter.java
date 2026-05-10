package org.investpro.strategy.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.MarketBehavior;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

/**
 * Adapts a user-developed UserStrategy to implement the TradingStrategy
 * interface.
 *
 * This allows user strategies to be used seamlessly throughout the platform
 * as if they were built-in strategies.
 *
 * Safety properties:
 * - Wraps all exceptions from user code
 * - Never allows null signals to escape (returns HOLD if null)
 * - Normalizes all signals before returning
 * - Supports all asset classes, contract types, and market behaviors by default
 * - Transparent in the strategy registry
 */
@Slf4j
@Getter
@Setter
public class UserStrategyAdapter implements TradingStrategy {

    private final UserStrategy userStrategy;
    private final StrategyMetadata metadata;

    private boolean enabled = true;

    private String lastSignalDescription = "No signal generated yet";

    public UserStrategyAdapter(@NotNull UserStrategy userStrategy) {
        this.userStrategy = userStrategy;
        this.metadata = createMetadata(userStrategy);
    }

    private static StrategyMetadata createMetadata(@NotNull UserStrategy userStrategy) {
        return StrategyMetadata.builder()
                .strategyId(userStrategy.getId())
                .displayName(userStrategy.getName())
                .description(userStrategy.getDescription())
                .build();
    }

    // =========================================================================
    // TradingStrategy implementation
    // =========================================================================

    @Override
    public @NotNull StrategySignal generateSignal(@NotNull StrategyContext context) {
        try {
            StrategySignal signal = userStrategy.generateSignal(context);

            // Safety: if user returns null, use HOLD
            if (signal == null) {
                log.warn("User strategy {} returned null signal, using HOLD", getId());
                return StrategySignal.hold(
                        context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                        context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                        String.valueOf(getId()),
                        "User strategy returned null signal");
            }

            // Normalize the signal
            StrategySignal normalized = signal.normalized();
            updateLastSignalDescription(normalized);
            return normalized;

        } catch (Exception e) {
            log.error("Exception in user strategy {}: {}", getId(), e.getMessage(), e);
            lastSignalDescription = "Error: " + e.getClass().getSimpleName();
            return StrategySignal.hold(
                    context.getSymbol() != null ? context.getSymbol().toString() : "UNKNOWN",
                    context.getTimeframe() != null ? context.getTimeframe().toString() : "UNKNOWN",
                    String.valueOf(getId()),
                    "Error in strategy: " + e.getMessage());
        }
    }

    private void updateLastSignalDescription(@NotNull StrategySignal signal) {
        StringBuilder desc = new StringBuilder();
        desc.append(signal.getSide().name());

        if (signal.getConfidence() > 0) {
            desc.append(String.format(" (%.0f%% confidence)", signal.getConfidence() * 100));
        }

        if (!signal.getReasons().isEmpty()) {
            desc.append(" - ").append(signal.getReasons().get(0));
        }

        this.lastSignalDescription = desc.toString();
    }

    @Override
    public boolean supportsAssetClass(AssetClass assetClass) {
        // User strategies support all asset classes by default
        return true;
    }

    @Override
    public boolean supportsContractType(ContractType contractType) {
        // User strategies support all contract types by default
        return true;
    }

    @Override
    public boolean supportsTimeframe(Timeframe timeframe) {
        // User strategies support all timeframes by default
        return true;
    }

    @Override
    public boolean supportsMarketBehavior(MarketBehavior marketBehavior) {
        // User strategies support all market behaviors by default
        return true;
    }

    @Override
    public int requiredWarmupBars() {
        try {
            return Math.max(1, userStrategy.requiredWarmupBars());
        } catch (Exception e) {
            log.warn("Error getting warmup bars from user strategy: {}", e.getMessage());
            return 100;
        }
    }

    @Override
    public void validateConfiguration() {
        // User strategies are validated elsewhere by UserStrategyValidator
        // This is a no-op
    }

    @Override
    public @NotNull Object getName() {
        try {
            return userStrategy.getName();
        } catch (Exception e) {
            log.error("Error getting strategy name", e);
            return "Unknown User Strategy";
        }
    }

    @Override
    public @NotNull Object getId() {
        try {
            return userStrategy.getId();
        } catch (Exception e) {
            log.error("Error getting strategy ID", e);
            return "unknown-user-strategy";
        }
    }

    public String getDescription() {
        try {
            return userStrategy.getDescription();
        } catch (Exception e) {
            log.warn("Error getting strategy description", e);
            return "";
        }
    }

    public boolean isUserStrategy() {
        return true;
    }

    @Override
    public String toString() {
        return String.format(
                "UserStrategyAdapter[id=%s, name=%s, enabled=%s]",
                getId(),
                getName(),
                enabled);
    }
}
