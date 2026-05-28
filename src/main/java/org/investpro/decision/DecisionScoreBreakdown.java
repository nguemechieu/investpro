package org.investpro.decision;

/**
 * Institutional scoring breakdown for a trade decision.
 *
 * <p>All component scores are in the range [0.0, 1.0]. The composite score is the
 * weighted average of all components. This breakdown drives the UI risk dashboard,
 * the Strategy Lab performance analytics, and the AI confidence aggregation layer.</p>
 */
public record DecisionScoreBreakdown(

        /** How well the current market trend aligns with the trade direction (0.0–1.0). */
        double trendScore,

        /** Suitability of current volatility conditions for this strategy (0.0–1.0). */
        double volatilityScore,

        /** Market liquidity depth at the time of the decision (0.0–1.0). */
        double liquidityScore,

        /** Composite risk management quality score (0.0–1.0). */
        double riskScore,

        /** AI model's composite confidence contribution (0.0–1.0). */
        double aiScore,

        /** Portfolio-level fit and impact score (0.0–1.0). */
        double portfolioScore,

        /** Execution feasibility and venue quality score (0.0–1.0). */
        double executionScore,

        /** Spread cost acceptability relative to expected profit (0.0–1.0). */
        double spreadScore,

        /** Overall signal confidence from the originating strategy/indicator (0.0–1.0). */
        double confidenceScore

) {

    /**
     * Computes a weighted composite score across all components.
     *
     * <p>Weights are calibrated for institutional trading where risk and execution quality
     * are paramount. AI and portfolio scores carry extra weight for advanced configurations.</p>
     */
    public double compositeScore() {
        return (trendScore      * 0.20) +
               (volatilityScore * 0.10) +
               (liquidityScore  * 0.10) +
               (riskScore       * 0.20) +
               (aiScore         * 0.15) +
               (portfolioScore  * 0.10) +
               (executionScore  * 0.05) +
               (spreadScore     * 0.05) +
               (confidenceScore * 0.05);
    }

    /** Returns true if the composite score meets the institutional minimum (≥ 0.65). */
    public boolean meetsInstitutionalThreshold() {
        return compositeScore() >= 0.65;
    }

    /** Returns the weakest scoring component name for diagnostic reporting. */
    public String weakestComponent() {
        double min = Double.MAX_VALUE;
        String name = "unknown";
        if (trendScore      < min) { min = trendScore;      name = "trend"; }
        if (volatilityScore < min) { min = volatilityScore; name = "volatility"; }
        if (liquidityScore  < min) { min = liquidityScore;  name = "liquidity"; }
        if (riskScore       < min) { min = riskScore;       name = "risk"; }
        if (aiScore         < min) { min = aiScore;         name = "ai"; }
        if (portfolioScore  < min) { min = portfolioScore;  name = "portfolio"; }
        if (executionScore  < min) { min = executionScore;  name = "execution"; }
        if (spreadScore     < min) { min = spreadScore;     name = "spread"; }
        if (confidenceScore < min) {                        name = "confidence"; }
        return name;
    }

    /** Returns a zero-score breakdown for rejected or skipped decisions. */
    public static DecisionScoreBreakdown zero() {
        return new DecisionScoreBreakdown(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /** Returns a uniform score breakdown for testing/defaults. */
    public static DecisionScoreBreakdown uniform(double value) {
        return new DecisionScoreBreakdown(
                value, value, value, value, value, value, value, value, value);
    }
}
