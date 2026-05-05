package org.investpro.trading;

/**
 * Enum for system design approaches - defines the overall strategy framework.
 */
public enum SystemDesign {
    TECHNICAL_ANALYSIS("Technical Analysis", "Price action, charts, indicators", "QUANTITATIVE"),
    FUNDAMENTAL_ANALYSIS("Fundamental Analysis", "Company/asset valuations, earnings", "QUALITATIVE"),
    QUANTITATIVE_MODELS("Quantitative Models", "Statistical models, machine learning", "ALGORITHMIC"),
    HYBRID_SYSTEM("Hybrid System", "Combines multiple approaches", "COMPREHENSIVE"),
    DISCRETIONARY("Discretionary", "Trader judgment, intuition-based", "SUBJECTIVE"),
    MECHANICAL_RULES("Mechanical Rules", "Strict rules, no discretion", "OBJECTIVE");

    private final String displayName;
    private final String description;
    private final String approachType;

    SystemDesign(String displayName, String description, String approachType) {
        this.displayName = displayName;
        this.description = description;
        this.approachType = approachType;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getApproachType() { return approachType; }
}
