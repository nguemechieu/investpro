package org.investpro.decision;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Converts plan and cost assumptions into expected-value estimates.
 */
public class TradeExpectationEstimator {

    private static final double DEFAULT_WIN_PROBABILITY = 0.55;

    @NotNull
    public TradeExpectation estimate(@NotNull TradePlan plan, @NotNull TradeCostEstimate costs,
            @NotNull AssetMarketType assetType) {
        double winProbability = adjustedWinProbability(assetType);
        BigDecimal grossReward = plan.rewardAmount();
        BigDecimal grossLoss = plan.riskAmount();
        BigDecimal totalCosts = costs.totalCost();
        BigDecimal expectedValue = grossReward.multiply(BigDecimal.valueOf(winProbability))
                .subtract(grossLoss.add(totalCosts).multiply(BigDecimal.valueOf(1.0 - winProbability)));

        BigDecimal netProfit = grossReward.subtract(totalCosts);
        double netRewardToRisk = grossLoss.signum() <= 0
                ? 0.0
                : Math.max(0.0, netProfit.divide(grossLoss, 8, java.math.RoundingMode.HALF_UP).doubleValue());
        String breakdown = String.format(
                "Gross: %.6f, Cost: %.6f, Net: %.6f, Loss: %.6f, EV: %.6f",
                grossReward, totalCosts, netProfit, grossLoss, expectedValue);

        return new TradeExpectation(
                grossReward,
                grossLoss,
                netProfit,
                expectedValue,
                winProbability,
                netRewardToRisk,
                breakdown,
                expectedValue.signum() > 0,
                netRewardToRisk >= 1.5);
    }

    private double adjustedWinProbability(AssetMarketType assetType) {
        return switch (assetType) {
            case FOREX -> DEFAULT_WIN_PROBABILITY + 0.01;
            case CRYPTO_SPOT, CRYPTO_DERIVATIVES -> DEFAULT_WIN_PROBABILITY - 0.03;
            case EQUITIES, EQUITY_DERIVATIVES -> DEFAULT_WIN_PROBABILITY;
            case COMMODITIES, FIXED_INCOME, UNKNOWN -> DEFAULT_WIN_PROBABILITY - 0.01;
        };
    }
}
