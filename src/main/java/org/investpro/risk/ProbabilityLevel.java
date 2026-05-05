package org.investpro.risk;

import lombok.Getter;

/**
 * Assesses trade setup confidence and edge probability.
 * Critical for position sizing and trade filtering.
 */
@Getter
public enum ProbabilityLevel {
    VERY_HIGH("Very High (90%+)", "Extremely confident setup", 0.90, "STRONG_BUY"),
    HIGH("High (70-90%)", "Strong setup with clear edge", 0.70, "BUY"),
    MODERATE("Moderate (50-70%)", "Reasonable setup, mixed signals", 0.50, "HOLD"),
    LOW("Low (30-50%)", "Weak setup, marginal edge", 0.30, "SELL"),
    VERY_LOW("Very Low (<30%)", "Poor probability, avoid", 0.10, "STRONG_SELL");

    private final String displayName;
    private final String description;
    private final double expectedWinRate;
    private final String actionSignal;

    ProbabilityLevel(String displayName, String description, double expectedWinRate, String actionSignal) {
        this.displayName = displayName;
        this.description = description;
        this.expectedWinRate = expectedWinRate;
        this.actionSignal = actionSignal;
    }

    public static ProbabilityLevel fromString(String value) {
        if (value == null || value.isEmpty()) return MODERATE;
        try {
            return ProbabilityLevel.valueOf(value.toUpperCase().replace(" ", "_").replaceAll("[^A-Z_]", ""));
        } catch (IllegalArgumentException e) {
            return MODERATE;
        }
    }
}
