package org.investpro.research;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scores and ranks strategies based on backtest results.
 * Uses multi-factor risk-adjusted scoring: 35% profit + 25% risk + 20%
 * consistency + 10% execution + 10% stability.
 */
@Slf4j
public class StrategyRankingEngine {

    /**
     * Scores a single backtest result into a StrategyScore.
     */
    @NotNull
    public StrategyScore scoreStrategy(@NotNull StrategyBacktestResult result) {
        double profitabilityScore = calculateProfitabilityScore(result);
        double riskScore = calculateRiskScore(result);
        double consistencyScore = calculateConsistencyScore(result);
        double executionScore = calculateExecutionScore(result);
        double stabilityScore = calculateStabilityScore(result);
        double overfittingPenalty = calculateOverfittingPenalty(result);

        double totalScore = StrategyScore.calculateTotalScore(
                profitabilityScore, riskScore, consistencyScore, executionScore, stabilityScore, overfittingPenalty);

        List<String> reasons = buildReasons(result, profitabilityScore, riskScore, consistencyScore);
        List<String> warnings = buildWarnings(result);

        return StrategyScore.builder()
                .strategyId(result.getStrategyId())
                .totalScore(totalScore)
                .profitabilityScore(profitabilityScore)
                .riskScore(riskScore)

                .consistencyScore(consistencyScore)
                .executionScore(executionScore)
                .stabilityScore(stabilityScore)
                .overfittingPenalty(overfittingPenalty)
                .reasons(reasons)
                .warnings(warnings)
                .build();


    }

    /**
     * Scores multiple backtest results and returns them ranked by total score
     * (highest first).
     */
    @NotNull
    public List<StrategyScore> rankStrategies(@NotNull List<StrategyBacktestResult> results) {
        return results.stream()
                .map(this::scoreStrategy)
                .sorted(Comparator.comparingDouble(StrategyScore::getTotalScore).reversed())
                .collect(Collectors.toList());
    }

    // Scoring component methods

    private double calculateProfitabilityScore(@NotNull StrategyBacktestResult result) {
        // Base score on total return
        double returnScore = Math.min(100, Math.max(0, result.getTotalReturnPercent() / 5.0)); // 5% = 100 points

        // Adjust by profit factor (1.5+ = good, 1.0 = break-even, <1 = loss)
        double pfScore = Math.min(100, result.getProfitFactor() * 50.0);

        // Expectancy: positive expected value is key
        double expScore = result.getExpectancy() > 0 ? 75 : (result.getExpectancy() < -0.5 ? 0 : 50);

        return (returnScore * 0.5 + pfScore * 0.3 + expScore * 0.2);
    }

    private double calculateRiskScore(@NotNull StrategyBacktestResult result) {
        // Lower drawdown = higher score
        double ddScore = Math.max(0, 100 - (result.getMaxDrawdownPercent() * 2.0));

        // Risk-adjusted return (Sharpe ratio)
        double sharpeScore = Math.min(100, Math.max(0, result.getSharpeRatio() * 10.0 + 50));

        // Avoid large individual losses
        double maxLossScore = result.getLargestLoss() > 0 ? Math.max(0, 100 - (Math.abs(result.getLargestLoss()) / 2.0))
                : 100;

        return (ddScore * 0.5 + sharpeScore * 0.3 + maxLossScore * 0.2);
    }

    private double calculateConsistencyScore(@NotNull StrategyBacktestResult result) {
        // Win rate consistency (55%+ = good)
        double winRateScore = Math.min(100, result.getWinRate() * 150.0);

        // Avoid extreme consecutive losses
        int maxConsecutiveLosses = Math.min(5, result.getConsecutiveLosses());
        double consecutiveScore = Math.max(0, 100 - (maxConsecutiveLosses * 15.0));

        // Profit factor consistency
        double pfConsistency = Math.min(100, result.getProfitFactor() * 40.0);

        return (winRateScore * 0.4 + consecutiveScore * 0.4 + pfConsistency * 0.2);
    }

    private double calculateExecutionScore(@NotNull StrategyBacktestResult result) {
        // Number of trades (more trades = better statistical significance)
        double tradeCountScore = Math.min(100, result.getTotalTrades() / 20.0 * 100);

        // Fees and slippage impact
        double feeImpact = Math.max(0, 100 - ((result.getFeesPaid() + result.getSlippageCost()) * 10.0));

        // Average holding time consistency
        double holdingTimeScore = result.getAverageHoldingTime() > 0 ? 75 : 50;

        return (tradeCountScore * 0.5 + feeImpact * 0.3 + holdingTimeScore * 0.2);
    }

    private double calculateStabilityScore(@NotNull StrategyBacktestResult result) {
        // For now, use Calmar ratio as proxy for stability
        // In production, would compare in-sample vs out-of-sample performance
        double calmarScore = Math.min(100, Math.max(0, result.getCalmarRatio() * 20.0 + 50));

        // Consistent positive returns
        double positiveReturnScore = result.getTotalReturnPercent() > 0 ? 80 : 40;

        return (calmarScore * 0.6 + positiveReturnScore * 0.4);
    }

    private double calculateOverfittingPenalty(@NotNull StrategyBacktestResult result) {
        double penalty = 0.0;

        // Too few trades suggests overfitting
        if (result.getTotalTrades() < 10) {
            penalty -= 20;
        }

        // Unreasonably high returns suggest overfitting
        if (result.getTotalReturnPercent() > 100) {
            penalty -= 15;
        }

        // Very high Sharpe ratio suggests overfitting
        if (result.getSharpeRatio() > 3.0) {
            penalty -= 10;
        }

        // Perfect or near-perfect win rate suggests overfitting
        if (result.getWinRate() > 0.95) {
            penalty -= 25;
        }

        return Math.max(-50, penalty);
    }

    private List<String> buildReasons(@NotNull StrategyBacktestResult result, double profit, double risk,
            double consistency) {
        List<String> reasons = new ArrayList<>();

        if (profit > 70) {
            reasons.add("Strong profitability: " + String.format("%.1f", profit) + "/100");
        }

        if (risk > 70) {
            reasons.add("Excellent risk management: " + String.format("%.1f", risk) + "/100");
        }

        if (consistency > 70) {
            reasons.add("Consistent win rate: " + String.format("%.1f%%", result.getWinRate() * 100));
        }

        if (result.meetsQualityStandards()) {
            reasons.add("Meets quality standards for live trading");
        }

        if (result.getProfitFactor() > 2.0) {
            reasons.add("Excellent profit factor: " + String.format("%.2f", result.getProfitFactor()));
        }

        return reasons;
    }

    private List<String> buildWarnings(@NotNull StrategyBacktestResult result) {
        List<String> warnings = new ArrayList<>();

        if (result.getTotalTrades() < 20) {
            warnings.add("Low trade count (" + result.getTotalTrades() + ") - limited statistical significance");
        }

        if (result.getMaxDrawdownPercent() > 40) {
            warnings.add("High drawdown: " + String.format("%.1f%%", result.getMaxDrawdownPercent()));
        }

        if (result.getConsecutiveLosses() > 5) {
            warnings.add("Long losing streak: " + result.getConsecutiveLosses() + " consecutive losses");
        }

        if (result.getWinRate() < 0.40) {
            warnings.add("Low win rate: " + String.format("%.1f%%", result.getWinRate() * 100));
        }

        if (!result.isProfitable()) {
            warnings.add("Strategy is unprofitable");
        }

        return warnings;
    }
}
