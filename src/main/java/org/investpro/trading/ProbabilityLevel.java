package org.investpro.trading;

/**
 * Enum for probability confidence levels - assesses confidence in trade setups.
 */
public enum ProbabilityLevel {
    VERY_HIGH("Very High", "90%+ confidence, multiple confirmations", 0.90, "STRONG_BUY"),
    HIGH("High", "70-90% confidence, good confirmation", 0.75, "BUY"),
    MODERATE("Moderate", "50-70% confidence, some confirmation", 0.60, "HOLD"),
    LOW("Low", "30-50% confidence, weak signals", 0.40, "SELL"),
    VERY_LOW("Very Low", "<30% confidence, avoid trade", 0.15, "STRONG_SELL");

    private final String displayName;
    private final String description;
    private final double expectedWinRate;       // Expected probability of successful trade
    private final String actionSignal;

    ProbabilityLevel(String displayName, String description, double expectedWinRate, String actionSignal) {
        this.displayName = displayName;
        this.description = description;
        this.expectedWinRate = expectedWinRate;
        this.actionSignal = actionSignal;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getExpectedWinRate() { return expectedWinRate; }
    public String getActionSignal() { return actionSignal; }
}
