package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.investpro.models.trading.Position;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete context for portfolio-level trade evaluation.
 * Input to QuantPortfolioManager.
 */
@Getter
@Builder
public class PortfolioContext {
    
    // Account state
    @NotNull
    private final String accountId;
    
    @Builder.Default
    private final double accountEquity = 0.0;
    
    @Builder.Default
    private final double availableCash = 0.0;
    
    @Builder.Default
    private final double usedMargin = 0.0;
    
    @Builder.Default
    private final double freeMargin = 0.0;
    
    @Builder.Default
    private final double dailyPnl = 0.0;
    
    @Builder.Default
    private final double weeklyPnl = 0.0;
    
    @Builder.Default
    private final double monthlyPnl = 0.0;
    
    @Builder.Default
    private final double currentDrawdownPercent = 0.0;
    
    @Builder.Default
    private final double maxDrawdownPercent = 0.0;
    
    @Builder.Default
    private final String riskProfile = "BALANCED"; // CONSERVATIVE, BALANCED, AGGRESSIVE
    
    // Open positions
    @Builder.Default
    private final List<Position> openPositions = new ArrayList<>();
    
    // Candidate trade details
    @Nullable
    private final StrategySignal candidateSignal;
    
    @Nullable
    private final Object candidateRiskDecision; // RiskDecision from RiskManagementSystem
    
    @Nullable
    private final Object candidateAiDecision; // AI reasoning decision
    
    @NotNull
    private final String candidateSymbol;
    
    @Nullable
    private final Object candidateTimeframe; // Timeframe enum
    
    @NotNull
    private final String candidateStrategyId;
    
    @Nullable
    private final String candidateAssetClass;
    
    @Nullable
    private final String candidateContractType;
    
    @Nullable
    private final String candidateBroker;
    
    @Builder.Default
    private final double requestedPositionSize = 0.0; // contracts/shares requested
    
    @Builder.Default
    private final double requestedLeverage = 1.0;
    
    // Exposure state
    @Builder.Default
    private final Map<String, Double> symbolExposure = new HashMap<>(); // current exposure by symbol
    
    @Builder.Default
    private final Map<String, Double> assetClassExposure = new HashMap<>();
    
    @Builder.Default
    private final Map<String, Double> strategyExposure = new HashMap<>();
    
    @Builder.Default
    private final Map<String, Double> brokerExposure = new HashMap<>();
    
    @Builder.Default
    private final Map<String, Double> contractTypeExposure = new HashMap<>();
    
    // Correlation analysis (optional, computed if missing)
    @Nullable
    private final Map<String, Map<String, Double>> correlationMatrix; // symbol correlations
    
    // Market environment
    @Nullable
    private final String currentMarketBehavior; // TRENDING, RANGING, BREAKOUT, REVERSAL, HIGH_VOLATILITY, etc.
    
    @Builder.Default
    private final double marketVolatilityLevel = 50.0; // 0-100 scale
    
    @Builder.Default
    private final String liquidityProfile = "NORMAL"; // THIN, LOW, NORMAL, HIGH, VERY_HIGH
    
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    public double getCandidateExposureAfterTrade(double sizePercent) {
        // Rough estimate of exposure change: (requestedPositionSize * requestedLeverage) / accountEquity
        if (accountEquity <= 0) return 0;
        return (requestedPositionSize * requestedLeverage * sizePercent) / accountEquity;
    }
    
    public double getCandidateSymbolExposure() {
        return symbolExposure.getOrDefault(candidateSymbol, 0.0);
    }
    
    public double getCandidateAssetClassExposure() {
        String assetClass = candidateAssetClass != null ? candidateAssetClass : "UNKNOWN";
        return assetClassExposure.getOrDefault(assetClass, 0.0);
    }
    
    public double getCandidateStrategyExposure() {
        return strategyExposure.getOrDefault(candidateStrategyId, 0.0);
    }
    
    public double getTotalExposure() {
        return symbolExposure.values().stream().mapToDouble(d -> d).sum();
    }
    
    public boolean isHighDrawdown() {
        return currentDrawdownPercent > 5.0; // More than 5% down
    }
    
    public boolean isCriticalDrawdown() {
        return currentDrawdownPercent > 15.0; // More than 15% down
    }
    
    public double getMarginUsagePercent() {
        if (freeMargin + usedMargin <= 0) return 0;
        return 100 * usedMargin / (freeMargin + usedMargin);
    }
    
    public boolean isHighMarginUsage() {
        return getMarginUsagePercent() > 70;
    }
    
    public boolean isCriticalMarginUsage() {
        return getMarginUsagePercent() > 90;
    }
    
    public double getCashUsagePercent() {
        if (accountEquity <= 0) return 0;
        return 100 * (accountEquity - availableCash) / accountEquity;
    }
    
    public double getMaxPositionSizeWithMargin() {
        if (requestedLeverage <= 0) return 0;
        return (accountEquity * requestedLeverage) / (requestedPositionSize > 0 ? 1 : 1);
    }
    
    public boolean isConservativeProfile() {
        return "CONSERVATIVE".equalsIgnoreCase(riskProfile);
    }
    
    public boolean isBalancedProfile() {
        return "BALANCED".equalsIgnoreCase(riskProfile);
    }
    
    public boolean isAggressiveProfile() {
        return "AGGRESSIVE".equalsIgnoreCase(riskProfile);
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioContext{account=%.2f, cash=%.2f, margin=%.1f%%, dd=%.2f%%, candidate=%s %s}",
                accountEquity, availableCash, getMarginUsagePercent(), currentDrawdownPercent, 
                candidateSymbol, candidateStrategyId);
    }
}
