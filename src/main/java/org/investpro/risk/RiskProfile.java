package org.investpro.risk;

import lombok.Getter;

/**
 * Defines overall risk tolerance level and constraints for trading.
 * Each profile enforces strict leverage, position sizing, and drawdown limits.
 */
@Getter
public enum RiskProfile {
    CONSERVATIVE("Conservative", 
        "Maximum safety with minimal leverage", 
        100, 2.0, 5.0, 5.0),
    
    MODERATE("Moderate", 
        "Balanced risk and reward", 
        3.0, 5.0, 15.0, 10.0),
    
    AGGRESSIVE("Aggressive", 
        "Higher leverage and position exposure", 
        100.0, 10.0, 30.0, 20.0),
    
    EXTREME("Extreme", 
        "Maximum leverage allowed (use with extreme caution)", 
        100.0, 20.0, 50.0, 30.0);

    private final String displayName;
    private final String description;
    private final double maxLeverage;
    private final double maxPositionSizePercent;
    private final double maxDrawdownThreshold;
    private final double maxPortfolioHeatPercent;

    RiskProfile(String displayName, String description, double maxLeverage, 
                double maxPositionSizePercent, double maxDrawdownThreshold, 
                double maxPortfolioHeatPercent) {
        this.displayName = displayName;
        this.description = description;
        this.maxLeverage = maxLeverage;
        this.maxPositionSizePercent = maxPositionSizePercent;
        this.maxDrawdownThreshold = maxDrawdownThreshold;
        this.maxPortfolioHeatPercent = maxPortfolioHeatPercent;
    }

    public static RiskProfile fromString(String value) {
        if (value == null || value.isEmpty()) return MODERATE;
        try {
            return RiskProfile.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MODERATE;
        }
    }
}
