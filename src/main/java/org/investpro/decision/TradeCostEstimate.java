package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Comprehensive breakdown of trade costs.
 * Includes all friction costs that will reduce gross profit to net profit.
 */
public record TradeCostEstimate(
        BigDecimal spread, // bid-ask spread cost in quote currency
        BigDecimal commission, // exchange/broker commission
        BigDecimal slippage, // estimated price slippage on entry and exit
        BigDecimal swapCost, // daily swap/funding rate cost (annualized for position)
        BigDecimal marketImpact, // cost from market impact of the position size
        BigDecimal totalCost, // sum of all costs
        @NotNull String costBreakdown, // formatted string explaining costs
        boolean isCostAcceptable, // true if totalCost <= 30% of expected gross profit
        @Nullable String warningMessage // null if acceptable
) {

    /**
     * Return the percentage of total cost relative to gross profit
     */
    public double costAsPercentageOfProfit(BigDecimal grossProfit) {
        if (grossProfit.signum() <= 0)
            return 100.0;
        return totalCost.divide(grossProfit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public boolean isHighCost() {
        return !isCostAcceptable;
    }
}
