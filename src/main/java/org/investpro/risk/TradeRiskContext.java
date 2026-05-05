package org.investpro.risk;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable context capturing all risk inputs for a potential trade.
 * Serves as the input to RiskManagementSystem.evaluateTrade().
 * 
 * This is designed to be serializable and server-capable for future
 * integration with authoritative decision services.
 */
@Value
@Builder
public class TradeRiskContext {
    // Trade identification
    String symbol;
    String assetClass;          // from exchange.AssetClass
    String contractType;        // from exchange.ContractType
    String broker;

    // Account state
    double accountEquity;
    double availableCash;
    double currentOpenRisk;     // Total $ at risk from open positions

    // Trade parameters
    double requestedPositionSize;
    double requestedLeverage;
    double entryPrice;
    double stopLossPrice;
    double takeProfitPrice;

    // Trade expectation
    double expectedWinRate;         // 0.0 to 1.0
    double expectedRewardRiskRatio; // Reward / Risk

    // Risk framework selections
    RiskProfile riskProfile;
    MarketBehavior marketBehavior;
    ExecutionStrategy executionStrategy;
    LiquidityProfile liquidityProfile;
    PsychologyProfile psychologyProfile;
    ProbabilityLevel probabilityLevel;
    CapitalProtection capitalProtection;
    SystemDesign systemDesign;

    // Derived / calculated fields (optional)
    double volatility;              // Current market volatility (0-1 scale)
    double maxRiskPerTrade;         // User's max risk per trade %
    double maxCumulativeRisk;       // User's max cumulative risk %

    /**
     * Calculate trade's risk in currency amount.
     */
    public double calculateTradeRisk() {
        if (entryPrice <= 0 || stopLossPrice <= 0) return 0;
        double priceRisk = Math.abs(entryPrice - stopLossPrice);
        return priceRisk * requestedPositionSize;
    }

    /**
     * Calculate trade's risk as percentage of account equity.
     */
    public double calculateTradeRiskPercent() {
        if (accountEquity <= 0) return 0;
        return (calculateTradeRisk() / accountEquity) * 100.0;
    }

    /**
     * Calculate potential reward in currency amount.
     */
    public double calculateTradeReward() {
        if (entryPrice <= 0 || takeProfitPrice <= 0) return 0;
        double priceReward = Math.abs(takeProfitPrice - entryPrice);
        return priceReward * requestedPositionSize;
    }

    /**
     * Calculate potential reward as percentage of account equity.
     */
    public double calculateTradeRewardPercent() {
        if (accountEquity <= 0) return 0;
        return (calculateTradeReward() / accountEquity) * 100.0;
    }

    /**
     * Calculate unrealized portfolio heat if this trade is added.
     */
    public double calculateTotalPortfolioRisk() {
        return currentOpenRisk + calculateTradeRisk();
    }

    /**
     * Calculate total portfolio heat as percentage of account equity.
     */
    public double calculateTotalPortfolioRiskPercent() {
        if (accountEquity <= 0) return 0;
        return (calculateTotalPortfolioRisk() / accountEquity) * 100.0;
    }
}
