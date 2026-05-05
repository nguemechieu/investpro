package org.investpro.risk;

/**
 * Calculates portfolio heat - total risk exposure as percentage of account equity.
 * Critical for preventing over-leverage and managing cumulative risk.
 */
public class PortfolioHeatCalculator {

    /**
     * Calculate portfolio heat after adding a new position.
     * 
     * @param newTradeRisk Risk in currency of the new trade
     * @param totalOpenRisk Current total open risk from existing positions
     * @param accountEquity Current account equity
     * @return Portfolio heat as percentage (0.0 to 100.0+)
     */
    public static double calculatePortfolioHeat(
            double newTradeRisk,
            double totalOpenRisk,
            double accountEquity) {

        if (accountEquity <= 0) {
            return 0;
        }

        double totalRisk = newTradeRisk + totalOpenRisk;
        return (totalRisk / accountEquity) * 100.0;
    }

    /**
     * Check if adding a new trade would exceed portfolio heat limit.
     * 
     * @param newTradeRisk Risk of new trade
     * @param totalOpenRisk Current total open risk
     * @param accountEquity Account equity
     * @param maxHeatPercent Maximum allowed portfolio heat %
     * @return true if heat would be exceeded
     */
    public static boolean exceedsMaxHeat(
            double newTradeRisk,
            double totalOpenRisk,
            double accountEquity,
            double maxHeatPercent) {

        double heat = calculatePortfolioHeat(newTradeRisk, totalOpenRisk, accountEquity);
        return heat > maxHeatPercent;
    }

    /**
     * Calculate remaining capacity for new trades.
     * 
     * @param totalOpenRisk Current total open risk
     * @param accountEquity Account equity
     * @param maxHeatPercent Maximum allowed portfolio heat %
     * @return Maximum currency amount available for new risk
     */
    public static double getRemainingHeatCapacity(
            double totalOpenRisk,
            double accountEquity,
            double maxHeatPercent) {

        if (accountEquity <= 0) {
            return 0;
        }

        double maxAllowedRisk = accountEquity * (maxHeatPercent / 100.0);
        double availableRisk = maxAllowedRisk - totalOpenRisk;

        return Math.max(0, availableRisk);
    }

    /**
     * Get current portfolio heat percentage.
     * 
     * @param totalOpenRisk Total current risk
     * @param accountEquity Account equity
     * @return Current portfolio heat as percentage
     */
    public static double getCurrentHeat(double totalOpenRisk, double accountEquity) {
        if (accountEquity <= 0) {
            return 0;
        }
        return (totalOpenRisk / accountEquity) * 100.0;
    }
}
