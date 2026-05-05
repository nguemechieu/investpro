package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Capital allocation plan across strategies, asset classes, and risk levels.
 * Adapts based on market regime, drawdown, strategy performance.
 */
@Getter
@Builder
public class PortfolioAllocationPlan {
    
    // Total capital allocation across all strategies
    @Builder.Default
    private final double totalAllocatedCapital = 0.0;
    
    @Builder.Default
    private final double totalAvailableCapital = 0.0;
    
    @Builder.Default
    private final double allocationUtilizationPercent = 0.0; // % of available capital allocated
    
    // Capital allocation by strategy
    @Builder.Default
    private final Map<String, Double> strategyAllocation = new HashMap<>(); // strategy ID -> capital allocated
    
    @Builder.Default
    private final Map<String, Double> strategyAllocationPercent = new HashMap<>(); // strategy ID -> % of total
    
    // Capital allocation by asset class
    @Builder.Default
    private final Map<String, Double> assetClassAllocation = new HashMap<>();
    
    @Builder.Default
    private final Map<String, Double> assetClassAllocationPercent = new HashMap<>();
    
    // Capital allocation by risk profile
    @Builder.Default
    private final double conservativeAllocation = 0.0; // % for safe strategies
    
    @Builder.Default
    private final double aggressiveAllocation = 0.0; // % for aggressive strategies
    
    @Builder.Default
    private final double defensiveAllocation = 0.0; // % for defensive positions
    
    // Current portfolio state that influenced this allocation
    @Builder.Default
    private final double drawdownAtTimeOfAllocation = 0.0;
    
    @Builder.Default
    private final double portfolioHeatAtTimeOfAllocation = 0.0;
    
    @Builder.Default
    private final double volatilityEnvironment = 0.0; // 0-100, current market volatility
    
    public double getStrategyAllocation(@NotNull String strategyId) {
        return strategyAllocation.getOrDefault(strategyId, 0.0);
    }
    
    public double getStrategyAllocationPercent(@NotNull String strategyId) {
        return strategyAllocationPercent.getOrDefault(strategyId, 0.0);
    }
    
    public double getAssetClassAllocation(@NotNull String assetClass) {
        return assetClassAllocation.getOrDefault(assetClass, 0.0);
    }
    
    public double getAssetClassAllocationPercent(@NotNull String assetClass) {
        return assetClassAllocationPercent.getOrDefault(assetClass, 0.0);
    }
    
    public boolean isDiversified() {
        // If no single strategy has >40% and no single asset class has >50%, call it diversified
        double maxStrategyPercent = strategyAllocationPercent.values().stream().mapToDouble(d -> d).max().orElse(0.0);
        double maxAssetClassPercent = assetClassAllocationPercent.values().stream().mapToDouble(d -> d).max().orElse(0.0);
        return maxStrategyPercent < 40 && maxAssetClassPercent < 50;
    }
    
    public boolean isConcentrated() {
        return !isDiversified();
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioAllocationPlan{allocated=%.2f, utilization=%.1f%%, strategies=%d, heat=%.1f%%, drawdown=%.2f%%}",
                totalAllocatedCapital, allocationUtilizationPercent, strategyAllocation.size(), 
                portfolioHeatAtTimeOfAllocation, drawdownAtTimeOfAllocation);
    }
}
