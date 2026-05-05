package org.investpro.strategy;

import org.investpro.trading.MarketBehavior;
import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all trading strategies in the InvestPro system.
 * All strategy implementations must conform to this contract.
 */
public interface TradingStrategy {

    /**
     * Gets the metadata describing this strategy.
     *
     * @return the strategy metadata
     */
    @NotNull StrategyMetadata getMetadata();

    /**
     * Generates a normalized trading signal based on current market context.
     * Must never return null; use StrategySignal.SignalSide.HOLD for no signal.
     *
     * @param context the market context with candles and prices
     * @return normalized StrategySignal (never null)
     */
    @NotNull StrategySignal generateSignal(@NotNull StrategyContext context);

    /**
     * Checks if this strategy supports the given asset class.
     *
     * @param assetClass the asset class to check
     * @return true if supported
     */
    boolean supportsAssetClass(@NotNull AssetClass assetClass);

    /**
     * Checks if this strategy supports the given contract type.
     *
     * @param contractType the contract type to check
     * @return true if supported
     */
    boolean supportsContractType(@NotNull ContractType contractType);

    /**
     * Checks if this strategy supports the given timeframe.
     *
     * @param timeframe the timeframe to check
     * @return true if supported
     */
    boolean supportsTimeframe(@NotNull Timeframe timeframe);

    /**
     * Checks if this strategy can trade in the given market behavior.
     *
     * @param marketBehavior the market behavior to check
     * @return true if supported
     */
    boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior);

    /**
     * Returns the minimum number of historical candles required
     * before this strategy can generate a signal.
     *
     * @return minimum bars required (e.g., 50, 100, 200)
     */
    int requiredWarmupBars();

    /**
     * Validates the strategy configuration.
     * Called during initialization to ensure consistency.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    void validateConfiguration() throws IllegalStateException;

    /**
     * Checks if this strategy is currently enabled for trading.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Gets human-readable description of the last generated signal.
     * Used for logging and UI display.
     *
     * @return description string
     */
    default String getLastSignalDescription() {
        return "No description available";
    }
}
