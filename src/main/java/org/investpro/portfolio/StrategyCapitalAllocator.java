package org.investpro.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Allocates capital to strategies based on performance, risk, and drift metrics.
 */
@Slf4j
public class StrategyCapitalAllocator {
    
    /**
     * Allocate capital across strategies based on their scores and performance.
     */
    @NotNull
    public Map<String, Double> allocateCapitalToStrategies(
            @NotNull PortfolioContext context,
            double totalCapitalToAllocate) {
        
        Map<String, Double> allocations = new HashMap<>();
        
        if (totalCapitalToAllocate <= 0) {
            return allocations;
        }
        
        // For now, we'll use strategy exposure as the basis
        // In production, this would integrate with StrategyScore from the strategy framework
        Map<String, Double> strategyExposures = context.getStrategyExposure();
        
        if (strategyExposures.isEmpty()) {
            // If no strategies yet, allocate to the candidate strategy
            allocations.put(context.getCandidateStrategyId(), totalCapitalToAllocate);
            return allocations;
        }
        
        // Get total existing exposure to normalize
        double totalExposure = strategyExposures.values().stream().mapToDouble(d -> d).sum();
        if (totalExposure <= 0) totalExposure = 1.0;
        
        // Allocate proportionally but apply drift penalty
        for (Map.Entry<String, Double> entry : strategyExposures.entrySet()) {
            String strategyId = entry.getKey();
            double currentExposure = entry.getValue();
            
            // Start with proportional allocation
            double allocation = (currentExposure / totalExposure) * totalCapitalToAllocate;
            
            // Apply drift penalty if strategy is showing drift
            allocation *= getStrategyDriftFactor(strategyId, context);
            
            // Apply performance score factor
            allocation *= getStrategyPerformanceFactor(strategyId, context);
            
            allocations.put(strategyId, Math.max(0, allocation));
            
            log.debug("Strategy {} allocated: {}", strategyId, allocation);
        }
        
        // Ensure allocations sum to total available (may be less due to drift/performance penalties)
        double totalAllocated = allocations.values().stream().mapToDouble(d -> d).sum();
        
        if (totalAllocated < totalCapitalToAllocate * 0.5) {
            // Allocate remainder to best performing strategies
            double remainder = totalCapitalToAllocate - totalAllocated;
            allocateBestPerformers(allocations, strategyExposures, remainder);
        }
        
        return allocations;
    }
    
    /**
     * Get drift factor for a strategy (penalties for drift).
     */
    private double getStrategyDriftFactor(@NotNull String strategyId, @NotNull PortfolioContext context) {
        // This would be integrated with StrategyDriftDetector in production
        // For now, return 1.0 (no penalty)
        // In reality:
        // - Check live vs backtest performance
        // - If drawdown worse: reduce to 0.5-0.8
        // - If win rate dropped: reduce to 0.6-0.9
        // - If volatility increased: reduce to 0.7-0.95
        
        return 1.0; // No drift penalty by default
    }
    
    /**
     * Get performance factor for a strategy.
     */
    private double getStrategyPerformanceFactor(@NotNull String strategyId, @NotNull PortfolioContext context) {
        // This would integrate with StrategyScore and recent performance tracking
        // For now, return neutral
        // In reality:
        // - High score (80+): 1.2 (increase allocation)
        // - Medium score (60-80): 1.0 (maintain)
        // - Low score (<60): 0.5 (reduce)
        // - Recent losses: 0.7-0.8
        
        return 1.0;
    }
    
    private void allocateBestPerformers(
            @NotNull Map<String, Double> allocations,
            @NotNull Map<String, Double> strategyExposures,
            double remainderCapital) {
        
        if (strategyExposures.isEmpty() || remainderCapital <= 0) {
            return;
        }
        
        // Find strategy with highest exposure
        String bestStrategy = strategyExposures.entrySet().stream()
                .max((a, b) -> Double.compare(a.getValue(), b.getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
        
        if (bestStrategy != null) {
            allocations.merge(bestStrategy, remainderCapital, Double::sum);
            log.debug("Allocated remainder to best strategy: {} (+{})", bestStrategy, remainderCapital);
        }
    }
    
    /**
     * Determine if a strategy should be temporarily disabled due to poor performance or drift.
     */
    public boolean shouldDisableStrategy(@NotNull String strategyId, @NotNull PortfolioContext context) {
        // In production, this would check:
        // - Live vs backtest performance divergence
        // - Recent consecutive losses
        // - Volatility increase
        // - Data quality issues
        // - Overfitting indicators
        
        // For now, always allow
        return false;
    }
    
    /**
     * Reduce capital allocation for a specific strategy due to risk conditions.
     */
    public double reduceAllocationForRiskConditions(
            @NotNull String strategyId,
            double currentAllocation,
            @NotNull PortfolioContext context) {
        
        double reducedAllocation = currentAllocation;
        
        // Reduce during high drawdown
        if (context.isHighDrawdown()) {
            reducedAllocation *= 0.8; // 20% reduction
        }
        
        if (context.isCriticalDrawdown()) {
            reducedAllocation *= 0.5; // 50% reduction
        }
        
        // Reduce if strategy dominates portfolio (concentration risk)
        double strategyExposure = context.getStrategyExposure().getOrDefault(strategyId, 0.0);
        if (strategyExposure > 40) { // Strategy already has >40% exposure
            reducedAllocation *= 0.7; // Reduce by 30%
        }
        
        // Reduce in conservative profile
        if (context.isConservativeProfile()) {
            reducedAllocation *= 0.9;
        }
        
        return Math.max(0, reducedAllocation);
    }
    
    /**
     * Calculate total strategy allocation from portfolio allocation plan.
     */
    public double getTotalStrategyAllocation(@NotNull PortfolioAllocationPlan plan) {
        return plan.getStrategyAllocation().values().stream()
                .mapToDouble(d -> d).sum();
    }
    
    /**
     * Get recommended allocation ratio for a strategy based on score.
     */
    public double getRecommendedAllocationRatio(double strategyScore) {
        // Score 0-100 maps to allocation ratio
        if (strategyScore < 50) {
            return 0.0; // Don't allocate
        } else if (strategyScore < 60) {
            return 0.1; // 10% of available
        } else if (strategyScore < 70) {
            return 0.3; // 30%
        } else if (strategyScore < 80) {
            return 0.6; // 60%
        } else {
            return 1.0; // 100%
        }
    }
}
