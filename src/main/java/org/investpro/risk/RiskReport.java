package org.investpro.risk;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Value;

/**
 * Professional risk report for backtesting, paper trading, or live trading.
 * Generated after evaluating a TradeRiskContext.
 */
@Value
@Builder
@Data
public class RiskReport {
    // Header
    String symbol;
    String timestamp;

    @Override
    public String toString() {
        return "RiskReport{" +
                "symbol='" + symbol + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", riskProfileName='" + riskProfileName + '\'' +
                ", marketBehaviorName='" + marketBehaviorName + '\'' +
                ", liquidityProfileName='" + liquidityProfileName + '\'' +
                ", psychologyProfileName='" + psychologyProfileName + '\'' +
                ", probabilityLevelName='" + probabilityLevelName + '\'' +
                ", capitalProtectionName='" + capitalProtectionName + '\'' +
                ", executionStrategyName='" + executionStrategyName + '\'' +
                ", systemDesignName='" + systemDesignName + '\'' +
                ", approved=" + approved +
                ", decisionStatus='" + decisionStatus + '\'' +
                ", finalPositionSize=" + finalPositionSize +
                ", finalLeverage=" + finalLeverage +
                ", riskMultiplier=" + riskMultiplier +
                ", accountEquity=" + accountEquity +
                ", tradeRiskAmount=" + tradeRiskAmount +
                ", tradeRiskPercent=" + tradeRiskPercent +
                ", tradeRewardAmount=" + tradeRewardAmount +
                ", tradeRewardPercent=" + tradeRewardPercent +
                ", portfolioHeat=" + portfolioHeat +
                ", maxAllowedHeat=" + maxAllowedHeat +
                ", recommendedExecutionStrategy='" + recommendedExecutionStrategy + '\'' +
                ", estimatedSlippage=" + estimatedSlippage +
                ", rewardRiskRatio=" + rewardRiskRatio +
                ", expectedValue=" + expectedValue +
                ", blockers='" + blockers + '\'' +
                ", warnings='" + warnings + '\'' +
                ", recommendations='" + recommendations + '\'' +
                '}';
    }

    // Risk framework summary
    String riskProfileName;
    String marketBehaviorName;
    String liquidityProfileName;
    String psychologyProfileName;
    String probabilityLevelName;
    String capitalProtectionName;
    String executionStrategyName;
    String systemDesignName;

    // Decision
    boolean approved;
    String decisionStatus;

    // Position sizing
    double finalPositionSize;
    double finalLeverage;
    double riskMultiplier;

    // Trade parameters
    double accountEquity;
    double tradeRiskAmount;
    double tradeRiskPercent;
    double tradeRewardAmount;
    double tradeRewardPercent;

    // Portfolio impact
    double portfolioHeat;
    double maxAllowedHeat;

    // Execution
    String recommendedExecutionStrategy;
    double estimatedSlippage;
    double rewardRiskRatio;
    double expectedValue;

    // Details
    String blockers;        // Formatted blocker list
    String warnings;        // Formatted warning list
    String recommendations; // Formatted recommendations

    /**
     * Generate professional formatted report.
     */
    public String formatProfessionalReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                    RISK MANAGEMENT REPORT\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        // Decision
        sb.append("DECISION: ").append(decisionStatus).append("\n");
        sb.append("Symbol: ").append(symbol).append("\n");
        sb.append("Timestamp: ").append(timestamp).append("\n\n");

        // Risk Framework
        sb.append("RISK FRAMEWORK:\n");
        sb.append("  Risk Profile:          ").append(riskProfileName).append("\n");
        sb.append("  Market Behavior:       ").append(marketBehaviorName).append("\n");
        sb.append("  Liquidity:             ").append(liquidityProfileName).append("\n");
        sb.append("  Psychology:            ").append(psychologyProfileName).append("\n");
        sb.append("  Probability:           ").append(probabilityLevelName).append("\n");
        sb.append("  Capital Protection:    ").append(capitalProtectionName).append("\n");
        sb.append("  Execution Strategy:    ").append(executionStrategyName).append("\n");
        sb.append("  System Design:         ").append(systemDesignName).append("\n\n");

        // Position Sizing
        sb.append("POSITION SIZING:\n");
        sb.append(String.format("  Final Position Size:   %.4f\n", finalPositionSize));
        sb.append(String.format("  Final Leverage:        %.2fx\n", finalLeverage));
        sb.append(String.format("  Risk Multiplier:       %.2f\n", riskMultiplier)).append("\n");

        // Trade Risk/Reward
        sb.append("TRADE RISK/REWARD:\n");
        sb.append(String.format("  Account Equity:        $%.2f\n", accountEquity));
        sb.append(String.format("  Risk Amount:           $%.2f (%.2f%% of account)\n", tradeRiskAmount, tradeRiskPercent));
        sb.append(String.format("  Reward Amount:         $%.2f (%.2f%% of account)\n", tradeRewardAmount, tradeRewardPercent));
        sb.append(String.format("  Reward/Risk Ratio:     %.2f\n", rewardRiskRatio));
        sb.append(String.format("  Expected Value:        $%.2f\n", expectedValue)).append("\n");

        // Portfolio Impact
        sb.append("PORTFOLIO IMPACT:\n");
        sb.append(String.format("  Total Portfolio Heat:  %.2f%% (max: %.2f%%)\n", portfolioHeat, maxAllowedHeat));
        sb.append("  Execution Strategy:    ").append(recommendedExecutionStrategy).append("\n");
        sb.append(String.format("  Est. Slippage:         %.4f%%\n", estimatedSlippage)).append("\n");

        // Feedback
        if (blockers != null && !blockers.isEmpty()) {
            sb.append("BLOCKERS (CRITICAL):\n");
            sb.append(blockers).append("\n");
        }
        if (warnings != null && !warnings.isEmpty()) {
            sb.append("WARNINGS:\n");
            sb.append(warnings).append("\n");
        }
        if (recommendations != null && !recommendations.isEmpty()) {
            sb.append("RECOMMENDATIONS:\n");
            sb.append(recommendations).append("\n");
        }

        sb.append("═══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
