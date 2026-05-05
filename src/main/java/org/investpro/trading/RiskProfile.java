package org.investpro.trading;

/**
 * Enum for risk profiles - defines acceptable risk levels and management strategies.
 */
public enum RiskProfile {
    CONSERVATIVE("Conservative", "Low risk tolerance, capital preservation priority", 1.0, 0.02, 0.05),
    MODERATE("Moderate", "Balanced risk-return, growth with controls", 3.0, 0.05, 0.15),
    AGGRESSIVE("Aggressive", "High risk tolerance, growth focused", 5.0, 0.10, 0.30),
    EXTREME("Extreme", "Maximum leverage, speculative trading", 10.0, 0.20, 0.50);

    private final String displayName;
    private final String description;
    private final double maxLeverage;          // Maximum allowed leverage ratio
    private final double maxPositionSize;      // Max position as % of portfolio
    private final double maxDrawdownThreshold; // Max acceptable drawdown %

    RiskProfile(String displayName, String description, double maxLeverage, 
                double maxPositionSize, double maxDrawdownThreshold) {
        this.displayName = displayName;
        this.description = description;
        this.maxLeverage = maxLeverage;
        this.maxPositionSize = maxPositionSize;
        this.maxDrawdownThreshold = maxDrawdownThreshold;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getMaxLeverage() { return maxLeverage; }
    public double getMaxPositionSize() { return maxPositionSize; }
    public double getMaxDrawdownThreshold() { return maxDrawdownThreshold; }
}
