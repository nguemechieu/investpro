package org.investpro.market;

/**
 * BIS-derived FX market liquidity tier classification.
 * <p>
 * Based on BIS Triennial Central Bank Survey data on FX turnover.
 * Controls risk multiplier, auto-trading eligibility, and strategy recommendations.
 */
public enum LiquidityTier {

    /** EUR/USD, USD/JPY, GBP/USD, USD/CHF, AUD/USD, USD/CAD, NZD/USD — deepest markets */
    TIER_1_MAJOR(1.00, true, "Scalping, Trend Following, Mean Reversion",
            "Major pair — deepest global FX liquidity"),

    /** Major currency crosses: EUR/GBP, EUR/JPY, GBP/JPY, AUD/JPY, etc. */
    TIER_2_MAJOR_CROSS(0.75, true, "Trend Following, Swing Trading",
            "Major cross — high liquidity, wider spreads than Tier 1"),

    /** Less-liquid crosses and secondary pairs */
    TIER_3_MINOR(0.50, true, "Swing Trading, Trend Following (wider spread tolerance required)",
            "Minor pair — moderate liquidity, elevated spread risk"),

    /** EM currencies, exotic pairs — thin markets, high spread/gap risk */
    TIER_4_EXOTIC(0.25, false, "Manual Review Only — paper trade first",
            "Exotic pair — thin liquidity, high spread and gap risk"),

    /** Pair cannot be classified from available dataset */
    UNKNOWN(0.10, false, "Paper Trading Only — market structure data unavailable",
            "Unknown pair — no market structure data; treat as highest risk");

    private final double riskMultiplier;
    private final boolean autoTradingAllowed;
    private final String recommendedStrategyStyle;
    private final String description;

    LiquidityTier(double riskMultiplier, boolean autoTradingAllowed,
                  String recommendedStrategyStyle, String description) {
        this.riskMultiplier = riskMultiplier;
        this.autoTradingAllowed = autoTradingAllowed;
        this.recommendedStrategyStyle = recommendedStrategyStyle;
        this.description = description;
    }

    public double getRiskMultiplier() {
        return riskMultiplier;
    }

    public boolean isAutoTradingAllowed() {
        return autoTradingAllowed;
    }

    public String getRecommendedStrategyStyle() {
        return recommendedStrategyStyle;
    }

    public String getDescription() {
        return description;
    }

    public String getDisplayName() {
        return switch (this) {
            case TIER_1_MAJOR -> "Tier 1 — Major";
            case TIER_2_MAJOR_CROSS -> "Tier 2 — Major Cross";
            case TIER_3_MINOR -> "Tier 3 — Minor";
            case TIER_4_EXOTIC -> "Tier 4 — Exotic";
            case UNKNOWN -> "Unknown";
        };
    }

    /** True when auto-trading should be blocked by risk policy. */
    public boolean isAutoTradingBlocked() {
        return !autoTradingAllowed;
    }
}
