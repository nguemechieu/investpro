package org.investpro.enums;

import lombok.Getter;

/**
 * Enum for capital protection strategies - defines how to preserve capital.
 */
@Getter
public enum CapitalProtection {
    STRICT_STOPS("Strict Stops", "Always use hard stops, no exceptions", 0.98, 0.02),
    DYNAMIC_STOPS("Dynamic Stops", "Move stops based on technical levels", 0.95, 0.05),
    TRAILING_STOPS("Trailing Stops", "Lock in gains as price moves favorably", 0.90, 0.10),
    TIME_STOPS("Time Stops", "Exit after fixed holding period", 0.85, 0.15),
    NONE("No Protection", "No predefined exit rules (HIGH RISK)", 0.50, 0.50);

    private final String displayName;
    private final String description;
    private final double capitalRetention;     // Expected % of capital retained per trade
    private final double maxLossThreshold;     // Maximum acceptable loss per trade

    CapitalProtection(String displayName, String description, double capitalRetention, double maxLossThreshold) {
        this.displayName = displayName;
        this.description = description;
        this.capitalRetention = capitalRetention;
        this.maxLossThreshold = maxLossThreshold;
    }

    public double getMaxDrawdownPercent() {

        return   maxLossThreshold;
    }
}
