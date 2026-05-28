package org.investpro.exchange.resilience.model;

/**
 * Composite health grade for the exchange connectivity layer.
 *
 * <p>Grades map to health score ranges:
 * <pre>
 *   GREEN  : score ≥ 0.85  — fully operational
 *   YELLOW : score ≥ 0.65  — degraded, non-critical issues only
 *   ORANGE : score ≥ 0.40  — significant degradation, possible execution impact
 *   RED    : score &lt; 0.40  — critical failure, execution likely blocked
 * </pre>
 */
public enum ExchangeHealthGrade {

    GREEN(0.85, "Fully operational — all systems nominal"),
    YELLOW(0.65, "Degraded — non-critical endpoint failures"),
    ORANGE(0.40, "Significant degradation — possible execution impact"),
    RED(0.0, "Critical failure — execution likely blocked");

    public final double minScore;
    public final String description;

    ExchangeHealthGrade(double minScore, String description) {
        this.minScore = minScore;
        this.description = description;
    }

    /** Classify a composite 0.0–1.0 score into a health grade. */
    public static ExchangeHealthGrade fromScore(double score) {
        if (score >= GREEN.minScore) return GREEN;
        if (score >= YELLOW.minScore) return YELLOW;
        if (score >= ORANGE.minScore) return ORANGE;
        return RED;
    }

    public boolean isOperational() {
        return this == GREEN || this == YELLOW;
    }

    public boolean isCritical() {
        return this == RED;
    }
}
