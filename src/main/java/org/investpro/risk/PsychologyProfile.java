package org.investpro.risk;

import lombok.Getter;

/**
 * Classifies trader emotional control and discipline.
 * Affects risk adjustments and validation rules.
 */
@Getter
public enum PsychologyProfile {
    DISCIPLINED("Disciplined", "High emotional control and consistency", true, 0.95),
    CONFIDENT("Confident", "Good composure with some risk-taking", true, 0.80),
    CAUTIOUS("Cautious", "Risk-averse decision making", true, 0.60),
    IMPULSIVE("Impulsive", "Prone to quick, emotional decisions", false, 0.40),
    FEARFUL("Fearful", "Excessive caution and hesitation", false, 0.30);

    private final String displayName;
    private final String description;
    private final boolean emotionallyControlled;
    private final double successProbability;

    PsychologyProfile(String displayName, String description, boolean emotionallyControlled, double successProbability) {
        this.displayName = displayName;
        this.description = description;
        this.emotionallyControlled = emotionallyControlled;
        this.successProbability = successProbability;
    }

    public static PsychologyProfile fromString(String value) {
        if (value == null || value.isEmpty()) return CAUTIOUS;
        try {
            return PsychologyProfile.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CAUTIOUS;
        }
    }
}
