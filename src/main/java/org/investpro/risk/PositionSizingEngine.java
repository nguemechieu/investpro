package org.investpro.risk;

import lombok.Data;
import lombok.ToString;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;

/**
 * Calculates appropriate position size based on risk parameters.
 * Implements Kelly Criterion and fixed fraction position sizing.
 */
@Data
@ToString
public class PositionSizingEngine {

    /**
     * Calculate position size using fixed fraction of account equity.
     * 
     * @param accountEquity Total account equity
     * @param maxRiskPercent Maximum risk per trade as % of account
     * @param entryPrice Entry price per unit
     * @param stopLossPrice Stop loss price per unit
     * @param riskProfile Risk profile (used for leverage limits)
     * @return Position size in units
     */
    public static double calculateFixedFractionSize(
            double accountEquity,
            double maxRiskPercent,
            double entryPrice,
            double stopLossPrice,
            RiskProfile riskProfile) {

        if (accountEquity <= 0 || maxRiskPercent <= 0 || entryPrice <= 0 || stopLossPrice <= 0) {
            return 0;
        }

        double riskPerTrade = accountEquity * (maxRiskPercent / 100.0);
        double priceRisk = Math.abs(entryPrice - stopLossPrice);
        double positionSize = riskPerTrade / priceRisk;

        // Enforce max leverage limit
        double maxLeveragePosition = accountEquity * riskProfile.getMaxLeverage() / entryPrice;
        return Math.min(positionSize, maxLeveragePosition);
    }

    /**
     * Calculate position size using Kelly Criterion.
     * F = (bp - q) / b, where:
     *   F = fraction of capital to bet
     *   b = odds (reward/risk)
     *   p = win probability
     *   q = loss probability (1 - p)
     * <p>
     * Applies 25% of full Kelly for safety ("fractional Kelly").
     * 
     * @param accountEquity Total account equity
     * @param entryPrice Entry price
     * @param stopLossPrice Stop loss price
     * @param takeProfitPrice Take profit price
     * @param winRate Expected win rate (0.0 to 1.0)
     * @param riskProfile Risk profile for leverage limits
     * @return Position size in units
     */
    public static double calculateKellyCriterion(
            double accountEquity,
            double entryPrice,
            double stopLossPrice,
            double takeProfitPrice,
            double winRate,
            RiskProfile riskProfile) {

        if (accountEquity <= 0 || entryPrice <= 0 || stopLossPrice <= 0 || takeProfitPrice <= 0) {
            return 0;
        }

        double priceRisk = Math.abs(entryPrice - stopLossPrice);
        double priceReward = Math.abs(takeProfitPrice - entryPrice);

        if (priceReward <= 0) {
            return 0;
        }

        double odds = priceReward / priceRisk;  // b in Kelly formula
        double p = Math.max(0.01, Math.min(0.99, winRate));  // Clamp to avoid edge cases
        double q = 1.0 - p;

        // Kelly fraction: F = (bp - q) / b
        double kellyFraction = ((odds * p) - q) / odds;

        // Use only 25% of full Kelly (conservative fractional Kelly)
        double fractionalKelly = kellyFraction * 0.25;

        if (fractionalKelly <= 0) {
            return 0;
        }

        // Position size = (kelly fraction * account) / price per unit
        double positionSize = (fractionalKelly * accountEquity) / entryPrice;

        // Enforce max leverage limit
        double maxLeveragePosition = accountEquity * riskProfile.getMaxLeverage() / entryPrice;
        return Math.min(positionSize, maxLeveragePosition);
    }

    /**
     * Apply psychology-based reduction to position size.
     * Impulsive and fearful traders get smaller positions.
     * 
     * @param basePositionSize Initial calculated position size
     * @param psychologyProfile Trader psychology classification
     * @return Adjusted position size
     */
    public static double applyPsychologyAdjustment(double basePositionSize, PsychologyProfile psychologyProfile) {
        if (basePositionSize <= 0) {
            return 0;
        }

        double adjustment = psychologyProfile.getSuccessProbability();
        return basePositionSize * adjustment;
    }

    /**
     * Apply liquidity-based reduction to position size.
     * Thin/illiquid markets get smaller positions.
     * 
     * @param basePositionSize Initial calculated position size
     * @param liquidityProfile Market liquidity classification
     * @return Adjusted position size
     */
    public static double applyLiquidityAdjustment(double basePositionSize, LiquidityProfile liquidityProfile) {
        if (basePositionSize <= 0) {
            return 0;
        }

        double adjustment = liquidityProfile.getFillProbability();
        return basePositionSize * adjustment;
    }

    /**
     * Apply volatility-based reduction to position size.
     * Higher volatility = smaller positions.
     * 
     * @param basePositionSize Initial calculated position size
     * @param volatility Current market volatility (0.0 to 1.0 scale)
     * @return Adjusted position size
     */
    public static double applyVolatilityAdjustment(double basePositionSize, double volatility) {
        if (basePositionSize <= 0 || volatility < 0 || volatility > 1.0) {
            return basePositionSize;
        }

        // In high volatility (0.8-1.0), reduce size to 60%
        // In low volatility (0.0-0.2), use full size
        double normalizedVolatility = Math.max(0.0, Math.min(1.0, volatility));
        double adjustment = 1.0 - (normalizedVolatility * 0.4);  // Range: 0.6 to 1.0


        return basePositionSize * adjustment;
    }
}
