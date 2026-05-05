package org.investpro.risk;

import lombok.Getter;

/**
 * Classifies overall trading methodology and approach.
 * Informational for strategy documentation and analysis.
 */
@Getter
public enum SystemDesign {
    TECHNICAL_ANALYSIS("Technical Analysis", "Charts, indicators, price action", "QUANTITATIVE"),
    FUNDAMENTAL_ANALYSIS("Fundamental Analysis", "Earnings, assets, economic factors", "QUALITATIVE"),
    QUANTITATIVE_MODELS("Quantitative Models", "Statistical, algorithmic, ML-based", "ALGORITHMIC"),
    HYBRID_SYSTEM("Hybrid System", "Combination of multiple approaches", "COMPREHENSIVE"),
    DISCRETIONARY("Discretionary", "Judgment-based, flexible interpretation", "SUBJECTIVE"),
    MECHANICAL_RULES("Mechanical Rules", "Strictly follow predefined rules", "OBJECTIVE");

    private final String displayName;
    private final String description;
    private final String approachType;

    SystemDesign(String displayName, String description, String approachType) {
        this.displayName = displayName;
        this.description = description;
        this.approachType = approachType;
    }

    public static SystemDesign fromString(String value) {
        if (value == null || value.isEmpty()) return HYBRID_SYSTEM;
        try {
            return SystemDesign.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return HYBRID_SYSTEM;
        }
    }
}
