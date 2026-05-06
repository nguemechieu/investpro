package org.investpro.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Comprehensive health assessment of an open position.
 * Combines multiple risk signals into a single 0.0-1.0 health score.
 * Used by risk manager to make automatic determinations about position safety.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PositionHealthScore {

    // =========================================================================
    // Health Status Enum
    // =========================================================================

    @Getter
    public enum HealthStatus {
        /** Score 0.8-1.0: Position is healthy, no concerns */
        HEALTHY("Healthy", true),

        /** Score 0.5-0.8: Position needs monitoring, some risks present */
        WATCH("Watch", true),

        /** Score 0.2-0.5: Position is in danger, significant risks */
        DANGER("Danger", false),

        /** Score 0.0-0.2: Position requires immediate action */
        EXIT_REQUIRED("Exit Required", false);

        private final String label;
        private final boolean isComfortable;

        HealthStatus(String label, boolean isComfortable) {
            this.label = label;
            this.isComfortable = isComfortable;
        }

    }

    // =========================================================================
    // Overall Score
    // =========================================================================

    /** Overall health score from 0.0 (critical) to 1.0 (excellent) */
    private double overallScore;

    /** Health status derived from score */
    private HealthStatus status;

    // =========================================================================
    // Component Scores
    // =========================================================================

    /** P&L component score (profitability, drawdown risk) */
    private double pnlScore;

    /** Risk component score (stop distance, leverage exposure) */
    private double riskScore;

    /** Technical score (support/resistance, momentum, trend alignment) */
    private double technicalScore;

    /** Liquidity score (can position be closed quickly without slippage?) */
    private double liquidityScore;

    /** Portfolio score (is portfolio heat sustainable?) */
    private double portfolioScore;

    // =========================================================================
    // Distance Metrics
    // =========================================================================

    /** Distance to stop loss in basis points (or -1 if no stop set) */
    private double distanceToStopBps;

    /** Distance to take profit in basis points (or -1 if no TP set) */
    private double distanceToTakeProfitBps;

    /** Distance to liquidation in basis points (or -1 if not applicable) */
    private double distanceToLiquidationBps;

    // =========================================================================
    // Risk Exposure
    // =========================================================================

    /** Current risk remaining if stop is hit (as % of account equity) */
    private double riskRemainingPercent;

    /** Profit protected: profit that would be lost if stopped out (%) */
    private double profitProtectedPercent;

    /** Portfolio heat contributed by this position (%) */
    private double portfolioHeatContributionPercent;

    // =========================================================================
    // Thesis Validation
    // =========================================================================

    /** Has original entry thesis been invalidated by price action? */
    private boolean thesisInvalidated;

    /** Reasons thesis may be invalidated */
    private java.util.List<String> thesisInvalidationReasons;

    // =========================================================================
    // Deterministic Risk Flags
    // =========================================================================

    /** Does deterministic risk rule require position exit? */
    private boolean deterministicExitRequired;

    /** Reason for deterministic exit requirement (if any) */
    private String deterministicExitReason;

    /** Does deterministic rule recommend reduce? */
    private boolean deterministicReduceRecommended;

    // =========================================================================
    // Metadata
    // =========================================================================

    /** When was this health score calculated? */
    private LocalDateTime calculatedAt;

    /** How many seconds until next recalculation recommended? */
    private int secondsUntilNextReview;

    /** Summary of key concerns */
    private String summary;

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Determine health status from overall score.
     */
    public static HealthStatus statusFromScore(double score) {
        if (score >= 0.8)
            return HealthStatus.HEALTHY;
        if (score >= 0.5)
            return HealthStatus.WATCH;
        if (score >= 0.2)
            return HealthStatus.DANGER;
        return HealthStatus.EXIT_REQUIRED;
    }

    /**
     * Check if position is in critical condition requiring immediate action.
     */
    public boolean isCritical() {
        return status == HealthStatus.EXIT_REQUIRED || deterministicExitRequired;
    }

    /**
     * Check if position health is acceptable.
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY && !deterministicExitRequired;
    }

    /**
     * Get recommended action based on health score.
     */
    public String getRecommendedAction() {
        if (deterministicExitRequired) {
            return "CLOSE_POSITION: " + deterministicExitReason;
        }
        if (deterministicReduceRecommended) {
            return "REDUCE_SIZE";
        }
        return switch (status) {
            case HEALTHY -> "HOLD";
            case WATCH -> "MONITOR";
            case DANGER -> "REDUCE_SIZE or CLOSE";
            case EXIT_REQUIRED -> "CLOSE_POSITION";
        };
    }

    /**
     * Get a summary of position health.
     */
    public String getHealthSummary() {
        return String.format("Status: %s, Score: %.2f, Stop Distance: %.0f bps, Risk: %.2f%%, Portfolio Heat: %.2f%%",
                status.getLabel(),
                overallScore,
                distanceToStopBps,
                riskRemainingPercent,
                portfolioHeatContributionPercent);
    }
}
