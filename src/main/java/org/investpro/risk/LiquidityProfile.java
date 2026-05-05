package org.investpro.risk;

import lombok.Getter;

/**
 * Assesses market liquidity conditions.
 * Affects execution feasibility and slippage expectations.
 */
@Getter
public enum LiquidityProfile {
    DEEP_LIQUIDITY("Deep Liquidity", "High volume, tight spread", 0.10, 0.95),
    NORMAL_LIQUIDITY("Normal Liquidity", "Standard market conditions", 0.50, 0.85),
    THIN_LIQUIDITY("Thin Liquidity", "Low volume, wider spread", 2.0, 0.60),
    ILLIQUID("Illiquid", "Very limited trading activity", 5.0, 0.30);

    private final String displayName;
    private final String description;
    private final double avgSpreadPercent;
    private final double fillProbability;

    LiquidityProfile(String displayName, String description, double avgSpreadPercent, double fillProbability) {
        this.displayName = displayName;
        this.description = description;
        this.avgSpreadPercent = avgSpreadPercent;
        this.fillProbability = fillProbability;
    }

    public static LiquidityProfile fromString(String value) {
        if (value == null || value.isEmpty()) return NORMAL_LIQUIDITY;
        try {
            return LiquidityProfile.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return NORMAL_LIQUIDITY;
        }
    }
}
