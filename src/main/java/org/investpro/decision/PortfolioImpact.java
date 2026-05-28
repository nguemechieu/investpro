package org.investpro.decision;

/**
 * Portfolio-level impact analysis for a proposed trade.
 *
 * <p>Quantifies how a new trade would affect the portfolio's aggregate risk profile:
 * total exposure, sector/asset-class concentration, correlation, volatility, and
 * hedging effects. This analysis runs between signal validation and risk evaluation.</p>
 *
 * <p>All values are in normalized units unless indicated (e.g., "percentOfPortfolio").</p>
 */
public record PortfolioImpact(

        /** Increase in total portfolio exposure as a fraction (e.g., 0.05 = +5%). */
        double exposureIncrease,

        /**
         * New concentration in this asset/sector if the trade is executed.
         * Expressed as a fraction of total portfolio value (0.0–1.0).
         */
        double concentrationFraction,

        /**
         * Pearson correlation of this trade's expected return with the existing portfolio.
         * Range: -1.0 (perfect hedge) to +1.0 (perfectly correlated).
         */
        double correlationWithPortfolio,

        /**
         * Hedge effectiveness if this trade acts as a partial hedge.
         * 0.0 = no hedging effect, 1.0 = full offset of existing risk.
         */
        double hedgeEffect,

        /** Change in portfolio-level volatility (annualized) if this trade is added. */
        double volatilityImpact,

        /** Resulting sector exposure fraction (after adding this trade). */
        double sectorExposureFraction,

        /** Resulting crypto asset class exposure fraction. */
        double cryptoExposureFraction,

        /** Resulting FX exposure fraction. */
        double fxExposureFraction,

        /**
         * Whether this trade would breach the portfolio's maximum single-position limit.
         */
        boolean breachesConcentrationLimit,

        /**
         * Whether this trade would bring the portfolio's total exposure above the
         * maximum allowed level.
         */
        boolean breachesExposureLimit

) {

    /**
     * Returns a neutral PortfolioImpact representing no existing portfolio positions.
     * Suitable for paper trading or when the portfolio analyzer is not available.
     */
    public static PortfolioImpact neutral() {
        return new PortfolioImpact(
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                false, false);
    }

    /** Returns true if any portfolio-level limit would be breached by this trade. */
    public boolean hasBreaches() {
        return breachesConcentrationLimit || breachesExposureLimit;
    }

    /** Returns the net correlation-adjusted exposure (considers hedge offset). */
    public double netExposure() {
        return exposureIncrease * (1.0 - hedgeEffect) * correlationWithPortfolio;
    }

    /**
     * Null-object constant representing an empty portfolio (no existing positions).
     * Use instead of {@code null} to avoid null-pointer chains in pipeline code.
     */
    public static final PortfolioImpact NEUTRAL = neutral();
}
