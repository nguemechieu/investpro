package org.investpro.decision;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Immutable output of the position sizing phase in the institutional decision pipeline.
 *
 * <p>A {@code PositionSizingDecision} is produced after risk evaluation and before execution
 * planning. It translates abstract exposure percentages into concrete position sizes while
 * accounting for portfolio constraints, volatility, and the selected sizing methodology.</p>
 *
 * <h3>Supported sizing methods:</h3>
 * <ul>
 *   <li>{@link SizingMethod#FIXED_RISK} — risk a fixed percentage of equity per trade</li>
 *   <li>{@link SizingMethod#ATR} — size based on Average True Range volatility</li>
 *   <li>{@link SizingMethod#KELLY} — Kelly criterion optimal fraction</li>
 *   <li>{@link SizingMethod#VOLATILITY} — inverse volatility weighting</li>
 *   <li>{@link SizingMethod#RISK_PARITY} — equalize risk contribution across positions</li>
 *   <li>{@link SizingMethod#DRAWDOWN_SCALED} — reduce size proportionally to drawdown</li>
 *   <li>{@link SizingMethod#EXPOSURE_SCALED} — scale to target exposure percent</li>
 *   <li>{@link SizingMethod#CONCENTRATION_REDUCED} — reduce to avoid concentration</li>
 * </ul>
 *
 * @param recommendedSize  recommended position size in base currency units
 * @param maxAllowedSize   hard maximum position size (portfolio constraint)
 * @param riskPercent      fraction of equity at risk for this trade (0.0–1.0)
 * @param leverage         target leverage multiplier (1.0 = no leverage)
 * @param sizingMethod     the algorithm that produced the recommended size
 * @param reductionReason  optional explanation if size was reduced from the initial estimate
 * @param scalingFactor    the factor applied to the raw size (e.g., 0.5 = halved)
 */
public record PositionSizingDecision(
        BigDecimal recommendedSize,
        BigDecimal maxAllowedSize,
        double riskPercent,
        double leverage,
        SizingMethod sizingMethod,
        @Nullable String reductionReason,
        double scalingFactor
) {

    // ─── Inner enum ───────────────────────────────────────────────────────────

    /** Sizing algorithm applied to determine the recommended position size. */
    public enum SizingMethod {
        /** Risk a fixed percentage of equity (e.g., 1% risk per trade). */
        FIXED_RISK,
        /** Size inversely proportional to Average True Range volatility. */
        ATR,
        /** Kelly criterion: f = (bp − q) / b, where b=payoff, p=winRate, q=1-p. */
        KELLY,
        /** Inverse volatility: larger size for lower-volatility assets. */
        VOLATILITY,
        /** Risk parity: equalize volatility-adjusted risk contribution. */
        RISK_PARITY,
        /** Scale down linearly with current drawdown severity. */
        DRAWDOWN_SCALED,
        /** Match a target exposure percentage of portfolio. */
        EXPOSURE_SCALED,
        /** Reduce size to stay within concentration limits. */
        CONCENTRATION_REDUCED,
        /** Simulation default — position size is 1 unit, no risk calculation. */
        SIMULATION_DEFAULT
    }

    // ─── Compact constructor (validation) ─────────────────────────────────────

    public PositionSizingDecision {
        if (recommendedSize == null) recommendedSize = BigDecimal.ZERO;
        if (maxAllowedSize == null)  maxAllowedSize  = BigDecimal.ZERO;
        if (sizingMethod == null)    sizingMethod     = SizingMethod.FIXED_RISK;
        riskPercent    = Math.max(0.0, Math.min(1.0, riskPercent));
        leverage       = Math.max(1.0, leverage);
        scalingFactor  = Math.max(0.0, Math.min(10.0, scalingFactor));
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Returns a neutral sizing decision with 1 unit size — suitable for simulation
     * where actual sizing is not relevant to the backtest objective.
     */
    public static PositionSizingDecision simulation() {
        return new PositionSizingDecision(
                BigDecimal.ONE, BigDecimal.ONE,
                0.01, 1.0,
                SizingMethod.SIMULATION_DEFAULT,
                null, 1.0);
    }

    /**
     * Creates a fixed-risk sizing decision.
     *
     * @param recommendedSize computed size in base currency units
     * @param riskPercent     fraction of equity at risk (e.g. 0.01 for 1%)
     */
    public static PositionSizingDecision fixedRisk(BigDecimal recommendedSize, double riskPercent) {
        return new PositionSizingDecision(
                recommendedSize, recommendedSize.multiply(BigDecimal.valueOf(2)),
                riskPercent, 1.0,
                SizingMethod.FIXED_RISK, null, 1.0);
    }

    /**
     * Creates a reduced sizing decision (e.g. due to portfolio concentration limit).
     *
     * @param originalSize    the size before reduction
     * @param scalingFactor   the fraction applied (0.0–1.0)
     * @param reason          explanation for the reduction
     * @param method          sizing method that triggered the reduction
     */
    public static PositionSizingDecision reduced(
            BigDecimal originalSize,
            double scalingFactor,
            String reason,
            SizingMethod method) {
        BigDecimal reduced = originalSize.multiply(BigDecimal.valueOf(scalingFactor));
        return new PositionSizingDecision(
                reduced, originalSize,
                0.01, 1.0,
                method, reason, scalingFactor);
    }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if the recommended size was reduced from the initial estimate. */
    public boolean wasReduced() {
        return scalingFactor < 1.0 || (reductionReason != null && !reductionReason.isBlank());
    }

    /** Returns {@code true} if the recommended size meets the minimum threshold of 0.001 units. */
    public boolean isViable() {
        return recommendedSize.compareTo(new BigDecimal("0.001")) >= 0;
    }

    /** Returns the effective size clamped to the maximum allowed. */
    public BigDecimal effectiveSize() {
        if (recommendedSize.compareTo(maxAllowedSize) > 0) return maxAllowedSize;
        return recommendedSize;
    }
}
