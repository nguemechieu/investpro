package org.investpro.risk;

import lombok.Data;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;

/**
 * Models slippage impact on trade execution.
 * Combines execution strategy, liquidity, and volatility factors.
 */
@Data
public class SlippageModel {

    /**
     * Calculate total estimated slippage for a trade.
     * Combines execution strategy slippage, liquidity spread, and volatility.
     * 
     * @param executionStrategy Execution method
     * @param liquidityProfile Market liquidity
     * @param volatility Current market volatility (0.0 to 1.0)
     * @return Total slippage as percentage (0.0 to 10.0+)
     */
    public static double calculateTotalSlippage(
            ExecutionStrategy executionStrategy,
            LiquidityProfile liquidityProfile,
            double volatility) {

        if (executionStrategy == null || liquidityProfile == null) {
            return 0.5;
        }

        double baseSlippage = executionStrategy.getEstimatedSlippage();
        double spreadImpact = liquidityProfile.getAvgSpread();
        double volatilityImpact = volatility * 0.5;  // Volatility can add up to 0.5%

        return baseSlippage + (spreadImpact * 0.1) + volatilityImpact;
    }

    /**
     * Check if execution strategy is viable for given liquidity.
     * Market orders are not recommended for illiquid markets.
     * 
     * @param executionStrategy Execution method
     * @param liquidityProfile Market liquidity
     * @return true if strategy is viable
     */
    public static boolean isExecutionStrategyViable(
            ExecutionStrategy executionStrategy,
            LiquidityProfile liquidityProfile) {

        // Market orders not viable in illiquid markets
        if (executionStrategy == ExecutionStrategy.MARKET_ORDER && liquidityProfile == LiquidityProfile.ILLIQUID) {
            return false;
        }

        // Market orders risky in thin liquidity
        return executionStrategy != ExecutionStrategy.MARKET_ORDER || liquidityProfile != LiquidityProfile.THIN;  // Still return false - not recommended
    }

    /**
     * Get recommended execution strategy for given liquidity.
     * 
     * @param liquidityProfile Market liquidity
     * @return Recommended execution strategy
     */
    public static ExecutionStrategy getRecommendedStrategy(LiquidityProfile liquidityProfile) {
        return switch (liquidityProfile) {
            case DEEP, NORMAL -> ExecutionStrategy.LIMIT_ORDER;
            case THIN, ILLIQUID -> ExecutionStrategy.SCALED_ENTRY;
        };
    }

    /**
     * Calculate cost of slippage in currency.
     * 
     * @param positionSize Number of units
     * @param entryPrice Entry price per unit
     * @param slippagePercent Slippage as percentage
     * @return Cost of slippage in currency
     */
    public static double calculateSlippageCost(double positionSize, double entryPrice, double slippagePercent) {
        if (positionSize <= 0 || entryPrice <= 0) {
            return 0;
        }
        return positionSize * entryPrice * (slippagePercent / 100.0);
    }

    /**
     * Apply slippage reduction to execution price.
     * 
     * @param planPrice Planned entry/exit price
     * @param slippagePercent Slippage as percentage
     * @param isBuySide true for buy orders (slippage increases price), false for sells
     * @return Actual execution price after slippage
     */
    public static double applySlippageToPrice(double planPrice, double slippagePercent, boolean isBuySide) {
        if (planPrice <= 0 || slippagePercent < 0) {
            return planPrice;
        }

        double slippageAmount = planPrice * (slippagePercent / 100.0);
        if (isBuySide) {
            return planPrice + slippageAmount;  // Buy slips higher
        } else {
            return planPrice - slippageAmount;  // Sell slips lower
        }
    }
}
