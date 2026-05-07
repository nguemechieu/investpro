package org.investpro.enums;

import lombok.Getter;

/**
 * Enum for trading psychology profiles - addresses emotional/behavioral factors.
 */
@Getter
public enum PsychologyProfile {
    DISCIPLINED("Disciplined", "Strict rule-following, emotional control", true, 0.95, "SYSTEMATIC"),
    CONFIDENT("Confident", "Trust in strategy, calculated risk-taking", true, 0.80, "AGGRESSIVE"),
    CAUTIOUS("Cautious", "Risk-averse, prefers small trades", true, 0.60, "CONSERVATIVE"),
    IMPULSIVE("Impulsive", "Emotional trading, overtrading risk", false, 0.40, "ERRATIC"),
    FEARFUL("Fearful", "Avoids trades, misses opportunities", false, 0.30, "PARALYZED");

    private final String displayName;
    private final String description;
    private final boolean emotionallyControlled;  // Can manage emotions effectively
    private final double successProbability;      // Expected success rate
    private final String tradingCharacter;

    PsychologyProfile(String displayName, String description, boolean emotionallyControlled,
                      double successProbability, String tradingCharacter) {
        this.displayName = displayName;
        this.description = description;
        this.emotionallyControlled = emotionallyControlled;
        this.successProbability = successProbability;
        this.tradingCharacter = tradingCharacter;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean isEmotionallyControlled() { return emotionallyControlled; }
    public double getSuccessProbability() { return successProbability; }
    public String getTradingCharacter() { return tradingCharacter; }
}
