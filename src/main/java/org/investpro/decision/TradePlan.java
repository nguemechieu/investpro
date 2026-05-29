package org.investpro.decision;


import java.math.BigDecimal;

/**
 * Complete trade plan with entry, stops, position sizing, and risk/reward
 * metrics.
 * Generated for institutional-grade pre-trade analysis before execution.
 */
public record TradePlan(
        // Price levels
        BigDecimal entryPrice, // Price at which trade will be entered
        BigDecimal stopLoss, // Absolute stop loss price level
        BigDecimal takeProfit, // Absolute take profit price level

        // Position sizing
        BigDecimal positionSize, // Number of units/contracts to trade

        // Risk/Reward amounts
        BigDecimal riskAmount, // Loss amount from entry to stop (position size * (entry - stop))
        BigDecimal rewardAmount, // Profit amount from entry to take profit (position size * (tp - entry))

        // Calculations
        double riskRewardRatio // Reward / Risk ratio
) {

    /**
     * Validate that the trade plan is internally consistent
     */
    public boolean isValid() {
        if (entryPrice == null || entryPrice.signum() <= 0)
            return false;
        if (stopLoss == null || stopLoss.signum() <= 0)
            return false;
        if (takeProfit == null || takeProfit.signum() <= 0)
            return false;
        if (positionSize == null || positionSize.signum() <= 0)
            return false;
        if (riskAmount == null || riskAmount.signum() < 0)
            return false;
        if (rewardAmount == null || rewardAmount.signum() < 0)
            return false;
        return !(riskRewardRatio < 0);
    }

    /**
     * Get risk/reward ratio as formatted string
     */
    public String getRiskRewardFormatted() {
        return String.format("%.2f:1", riskRewardRatio);
    }

    /**
     * Get a summary of the trade plan
     */
    public String getSummary() {
        return String.format(
                "Entry: %.4f | Stop: %.4f | TP: %.4f | Size: %.4f | RR: %.2f:1",
                entryPrice, stopLoss, takeProfit, positionSize, riskRewardRatio);
    }
}
