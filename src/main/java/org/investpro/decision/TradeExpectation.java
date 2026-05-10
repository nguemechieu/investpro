package org.investpro.decision;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Expected profit/loss calculations for a potential trade.
 * Includes gross profit, net profit (after costs), expected value, and risk
 * metrics.
 */
public record TradeExpectation(
        BigDecimal expectedGrossProfit, // profit before costs
        BigDecimal expectedLossIfWrong, // loss if trade goes against us
        BigDecimal expectedNetProfit, // gross profit - costs
        BigDecimal expectedValue, // (win probability * net profit) - (loss probability * loss)
        double winProbability, // 0.0-1.0: estimated probability of winning
        double riskRewardRatio, // expectedGrossProfit / expectedLossIfWrong
        @NotNull String profitBreakdown, // formatted string explaining expected profit
        boolean isPositiveExpectancy, // true if expected value > 0
        boolean isAcceptableRiskReward // true if risk/reward >= 1.5
) {

    public boolean isUnacceptableRiskReward() {
        return !isAcceptableRiskReward;
    }

    /**
     * Return risk/reward as formatted percentage string
     */
    public String getRiskRewardFormatted() {
        return String.format("%.2f:1", riskRewardRatio);
    }

    /**
     * Return expected value as formatted string
     */
    public String getExpectedValueFormatted() {
        return String.format("%.2f", expectedValue);
    }
}
