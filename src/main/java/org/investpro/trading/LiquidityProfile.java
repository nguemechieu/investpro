package org.investpro.trading;

/**
 * Enum for liquidity profiles - assesses market liquidity and trading conditions.
 */
public enum LiquidityProfile {
    DEEP_LIQUIDITY("Deep", "High volume, tight spreads, easy entry/exit", 0.001, 0.95),
    NORMAL_LIQUIDITY("Normal", "Adequate volume, reasonable spreads", 0.005, 0.85),
    THIN_LIQUIDITY("Thin", "Low volume, wide spreads, difficult execution", 0.02, 0.60),
    ILLIQUID("Illiquid", "Very low volume, large spreads, slow execution", 0.05, 0.30);

    private final String displayName;
    private final String description;
    private final double avgSpread;           // Average bid-ask spread as % of price
    private final double fillProbability;     // Likelihood of executing at or near limit

    LiquidityProfile(String displayName, String description, double avgSpread, double fillProbability) {
        this.displayName = displayName;
        this.description = description;
        this.avgSpread = avgSpread;
        this.fillProbability = fillProbability;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getAvgSpread() { return avgSpread; }
    public double getFillProbability() { return fillProbability; }
}
