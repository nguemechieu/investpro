package org.investpro.enums;

import lombok.Getter;

/**
 * Represents the liquidity condition of a tradable market.
 *
 * LiquidityProfile helps the trading system estimate:
 * - expected spread/slippage
 * - probability of order fill
 * - whether position size should be reduced
 * - whether the market should be blocked from trading
 */
@Getter
public enum LiquidityProfile {

    DEEP(
            "Deep Liquidity",
            "High-volume market with tight spreads and easy entry/exit.",
            0.001,
            0.95,
            1.00
    ),

    NORMAL(
            "Normal Liquidity",
            "Adequate volume with reasonable spreads and acceptable execution quality.",
            0.005,
            0.85,
            0.75
    ),

    THIN(
            "Thin Liquidity",
            "Low-volume market with wider spreads and more difficult execution.",
            0.020,
            0.60,
            0.40
    ),

    ILLIQUID(
            "Illiquid",
            "Very low-volume market with large spreads and poor execution reliability.",
            0.050,
            0.30,
            0.10
    );

    /**
     * Human-friendly name for UI/logging.
     */
    private final String displayName;

    /**
     * Description of this liquidity profile.
     */
    private final String description;

    /**
     * Estimated average bid/ask spread as a fraction of price.
     *
     * Example:
     * 0.001 = 0.10%
     * 0.005 = 0.50%
     */
    private final double avgSpread;

    /**
     * Estimated probability of getting filled near the expected price.
     *
     * Example:
     * 0.95 = 95%
     */
    private final double fillProbability;

    /**
     * Position-size multiplier based on liquidity risk.
     *
     * Example:
     * 1.00 = full size
     * 0.75 = reduce to 75%
     * 0.40 = reduce to 40%
     * 0.10 = almost block trading
     */
    private final double sizeMultiplier;

    LiquidityProfile(
            String displayName,
            String description,
            double avgSpread,
            double fillProbability,
            double sizeMultiplier
    ) {
        this.displayName = displayName;
        this.description = description;
        this.avgSpread = avgSpread;
        this.fillProbability = fillProbability;
        this.sizeMultiplier = sizeMultiplier;
    }

    public boolean isTradable() {
        return this != ILLIQUID;
    }

    public boolean requiresSizeReduction() {
        return this == THIN || this == ILLIQUID;
    }

    public boolean isHighQuality() {
        return this == DEEP || this == NORMAL;
    }

    public boolean isDangerous() {
        return this == ILLIQUID || fillProbability < 0.50;
    }

    public double adjustPositionSize(double requestedSize) {
        if (requestedSize <= 0) {
            return 0.0;
        }

        return requestedSize * sizeMultiplier;
    }

    public static LiquidityProfile classify(double volume24h, double spreadPercent) {
        if (volume24h >= 1_000_000_000 && spreadPercent <= 0.001) {
            return DEEP;
        }

        if (volume24h >= 50_000_000 && spreadPercent <= 0.005) {
            return NORMAL;
        }

        if (volume24h >= 5_000_000 && spreadPercent <= 0.020) {
            return THIN;
        }

        return ILLIQUID;
    }
}