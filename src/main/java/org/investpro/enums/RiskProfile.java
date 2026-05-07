package org.investpro.enums;

import lombok.Getter;

/**
 * Defines portfolio-level risk limits and trading constraints.
 *
 * Values are stored as decimal fractions:
 * - 0.02 = 2%
 * - 0.05 = 5%
 * - 0.30 = 30%
 */
@Getter
public enum RiskProfile {

    CONSERVATIVE(
            "Conservative",
            "Low risk tolerance with capital preservation as the priority.",
            1.0,
            0.02,
            0.05,
            0.05
    ),

    MODERATE(
            "Moderate",
            "Balanced risk-return profile with growth and controlled drawdown.",
            3.0,
            0.05,
            0.15,
            0.15
    ),

    AGGRESSIVE(
            "Aggressive",
            "High risk tolerance with growth-focused positioning.",
            5.0,
            0.10,
            0.30,
            0.25
    ),

    EXTREME(
            "Extreme",
            "Maximum leverage and speculative trading. Use with caution.",
            10.0,
            0.20,
            0.50,
            0.40
    );

    /**
     * Human-friendly name for UI/logging.
     */
    private final String displayName;

    /**
     * Description of the risk profile.
     */
    private final String description;

    /**
     * Maximum allowed leverage ratio.
     *
     * Example:
     * 3.0 = 3x leverage
     */
    private final double maxLeverage;

    /**
     * Maximum single position size as a fraction of portfolio equity.
     *
     * Example:
     * 0.05 = 5% of portfolio
     */
    private final double maxPositionSize;

    /**
     * Maximum acceptable drawdown as a fraction of portfolio equity.
     *
     * Example:
     * 0.15 = 15% drawdown
     */
    private final double maxDrawdownThreshold;

    /**
     * Maximum total portfolio heat as a fraction of equity.
     *
     * Portfolio heat = total open risk across all positions.
     *
     * Example:
     * 0.15 = max 15% total portfolio risk exposure
     */
    private final double maxPortfolioHeat;

    RiskProfile(
            String displayName,
            String description,
            double maxLeverage,
            double maxPositionSize,
            double maxDrawdownThreshold,
            double maxPortfolioHeat
    ) {
        this.displayName = displayName;
        this.description = description;
        this.maxLeverage = maxLeverage;
        this.maxPositionSize = maxPositionSize;
        this.maxDrawdownThreshold = maxDrawdownThreshold;
        this.maxPortfolioHeat = maxPortfolioHeat;
    }

    public double getMaxLeveragePercent() {
        return maxLeverage * 100.0;
    }

    public double getMaxPositionSizePercent() {
        return maxPositionSize * 100.0;
    }

    public double getMaxDrawdownThresholdPercent() {
        return maxDrawdownThreshold * 100.0;
    }

    public double getMaxPortfolioHeatPercent() {
        return maxPortfolioHeat * 100.0;
    }

    public boolean allowsLeverage(double requestedLeverage) {
        return requestedLeverage > 0 && requestedLeverage <= maxLeverage;
    }

    public boolean allowsPositionSize(double requestedPositionSizeFraction) {
        return requestedPositionSizeFraction > 0
                && requestedPositionSizeFraction <= maxPositionSize;
    }

    public boolean allowsPortfolioHeat(double portfolioHeatFraction) {
        return portfolioHeatFraction >= 0
                && portfolioHeatFraction <= maxPortfolioHeat;
    }

    public boolean isDrawdownBreached(double currentDrawdownFraction) {
        return currentDrawdownFraction >= maxDrawdownThreshold;
    }

    public double capLeverage(double requestedLeverage) {
        if (requestedLeverage <= 0) {
            return 1.0;
        }

        return Math.min(requestedLeverage, maxLeverage);
    }

    public double capPositionSize(double requestedPositionSizeFraction) {
        if (requestedPositionSizeFraction <= 0) {
            return 0.0;
        }

        return Math.min(requestedPositionSizeFraction, maxPositionSize);
    }

    public boolean isSpeculative() {
        return this == AGGRESSIVE || this == EXTREME;
    }

    public boolean isCapitalPreservationFocused() {
        return this == CONSERVATIVE;
    }
}