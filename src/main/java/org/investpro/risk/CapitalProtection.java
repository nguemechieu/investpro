package org.investpro.risk;

import lombok.Getter;

/**
 * Defines capital preservation strategies and stop-loss behavior.
 * Critical for risk containment and loss management.
 */
@Getter
public enum CapitalProtection {
    STRICT_STOPS("Strict Stops", "Hard stops at tight levels, max 2% loss", 0.98, 2.0),
    DYNAMIC_STOPS("Dynamic Stops", "Stops adjust to volatility, max 5% loss", 0.95, 5.0),
    TRAILING_STOPS("Trailing Stops", "Stops follow profits, max 10% loss", 0.90, 10.0),
    TIME_STOPS("Time Stops", "Exit after time period, max 15% loss", 0.85, 15.0),
    NONE("None", "No active stop strategy, max 50% loss", 0.50, 50.0);

    private final String displayName;
    private final String description;
    private final double capitalRetention;
    private final double maxLossThresholdPercent;

    CapitalProtection(String displayName, String description, double capitalRetention, double maxLossThresholdPercent) {
        this.displayName = displayName;
        this.description = description;
        this.capitalRetention = capitalRetention;
        this.maxLossThresholdPercent = maxLossThresholdPercent;
    }

    public static CapitalProtection fromString(String value) {
        if (value == null || value.isEmpty()) return DYNAMIC_STOPS;
        try {
            return CapitalProtection.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return DYNAMIC_STOPS;
        }
    }

    public double getMaxDrawdownPercent() {

        return maxLossThresholdPercent / 100.0;
    }
}
