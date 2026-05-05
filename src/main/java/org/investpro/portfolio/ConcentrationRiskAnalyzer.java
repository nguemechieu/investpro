package org.investpro.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Analyzes concentration risk - when portfolio is too heavily weighted in one direction.
 */
@Slf4j
public class ConcentrationRiskAnalyzer {
    
    /**
     * Calculate concentration risk score.
     */
    public double analyzeConcentrationRisk(@NotNull PortfolioContext context) {
        double score = 0.0;
        
        // Check symbol concentration
        double symbolRisk = analyzeSymbolConcentration(context);
        score += symbolRisk * 0.3; // 30% weight
        
        // Check asset class concentration
        double assetClassRisk = analyzeAssetClassConcentration(context);
        score += assetClassRisk * 0.3; // 30% weight
        
        // Check strategy concentration
        double strategyRisk = analyzeStrategyConcentration(context);
        score += strategyRisk * 0.2; // 20% weight
        
        // Check broker concentration
        double brokerRisk = analyzeBrokerConcentration(context);
        score += brokerRisk * 0.15; // 15% weight
        
        // Check directional concentration (long vs short imbalance)
        double directionalRisk = analyzeDirectionalConcentration(context);
        score += directionalRisk * 0.05; // 5% weight
        
        return Math.min(100, score);
    }
    
    /**
     * Analyze if one symbol dominates the portfolio.
     */
    private double analyzeSymbolConcentration(@NotNull PortfolioContext context) {
        Map<String, Double> symbolExposures = context.getSymbolExposure();
        
        if (symbolExposures.isEmpty()) {
            return 0.0;
        }
        
        double maxExposure = symbolExposures.values().stream()
                .mapToDouble(d -> d).max().orElse(0.0);
        
        // Maximum acceptable single symbol exposure is 15-20%
        double limit = context.isConservativeProfile() ? 10.0 : 20.0;
        
        if (maxExposure > limit * 2) {
            log.warn("Symbol concentration too high: max {} > limit {}", maxExposure, limit * 2);
            return 100.0; // Critical concentration
        } else if (maxExposure > limit * 1.2) {
            log.warn("Symbol concentration elevated: max {} > limit {}", maxExposure, limit);
            return 70.0; // High concentration
        } else if (maxExposure > limit) {
            return 40.0; // Moderate concentration
        }
        
        return 0.0; // Acceptable
    }
    
    /**
     * Analyze if one asset class dominates.
     */
    private double analyzeAssetClassConcentration(@NotNull PortfolioContext context) {
        Map<String, Double> assetClassExposures = context.getAssetClassExposure();
        
        if (assetClassExposures.isEmpty()) {
            return 0.0;
        }
        
        double maxExposure = assetClassExposures.values().stream()
                .mapToDouble(d -> d).max().orElse(0.0);
        
        // Maximum acceptable single asset class exposure is 40-60%
        double limit = context.isConservativeProfile() ? 40.0 : 60.0;
        
        if (maxExposure > limit * 1.5) {
            log.warn("Asset class concentration too high: max {} > limit {}", maxExposure, limit * 1.5);
            return 90.0;
        } else if (maxExposure > limit) {
            log.warn("Asset class concentration elevated: max {}", maxExposure);
            return 60.0;
        }
        
        return 0.0;
    }
    
    /**
     * Analyze if one strategy dominates.
     */
    private double analyzeStrategyConcentration(@NotNull PortfolioContext context) {
        Map<String, Double> strategyExposures = context.getStrategyExposure();
        
        if (strategyExposures.isEmpty()) {
            return 0.0;
        }
        
        double maxExposure = strategyExposures.values().stream()
                .mapToDouble(d -> d).max().orElse(0.0);
        
        // Maximum acceptable single strategy exposure is 40-50%
        double limit = context.isConservativeProfile() ? 30.0 : 50.0;
        
        if (maxExposure > limit * 1.5) {
            log.warn("Strategy concentration too high: max {} > limit {}", maxExposure, limit * 1.5);
            return 80.0;
        } else if (maxExposure > limit) {
            return 50.0;
        }
        
        return 0.0;
    }
    
    /**
     * Analyze if one broker dominates.
     */
    private double analyzeBrokerConcentration(@NotNull PortfolioContext context) {
        Map<String, Double> brokerExposures = context.getBrokerExposure();
        
        if (brokerExposures.isEmpty()) {
            return 0.0;
        }
        
        double maxExposure = brokerExposures.values().stream()
                .mapToDouble(d -> d).max().orElse(0.0);
        
        // Brokers should be more balanced - max 70% with one broker
        if (maxExposure > 85.0) {
            log.warn("Broker concentration critical: max {}", maxExposure);
            return 80.0;
        } else if (maxExposure > 70.0) {
            log.warn("Broker concentration elevated: max {}", maxExposure);
            return 50.0;
        }
        
        return 0.0;
    }
    
    /**
     * Analyze directional imbalance (long vs short).
     */
    private double analyzeDirectionalConcentration(@NotNull PortfolioContext context) {
        double netExposure = Math.abs(context.getTotalExposure()); // Net long/short
        double grossExposure = context.getTotalExposure(); // Absolute exposure
        
        if (grossExposure <= 0) {
            return 0.0;
        }
        
        // If portfolio is heavily imbalanced to one side, risk increases
        double imbalancePercent = (netExposure / grossExposure) * 100;
        
        // 100% means completely one-sided, 0% means perfectly balanced
        double directionalRisk = Math.max(0, imbalancePercent - 70.0) / 30.0 * 100; // Start penalizing at >70%
        
        if (directionalRisk > 0) {
            log.debug("Directional imbalance: {}% net vs {}% gross", netExposure, grossExposure);
        }
        
        return Math.min(100, directionalRisk);
    }
    
    /**
     * Assess whether candidate trade would increase concentration too much.
     */
    public boolean wouldIncreaseConcentrationTooMuch(
            @NotNull PortfolioContext context,
            double candidateExposure,
            double concentrationThreshold) {
        
        double currentScore = analyzeConcentrationRisk(context);
        
        // Rough estimate: candidate adds to risk
        double estimatedNewScore = currentScore + (candidateExposure * 0.5);
        
        return estimatedNewScore > concentrationThreshold;
    }
    
    /**
     * Get detailed explanation of concentration issues.
     */
    @NotNull
    public String getConcentrationAnalysis(@NotNull PortfolioContext context) {
        StringBuilder sb = new StringBuilder();
        
        Map<String, Double> symbolExps = context.getSymbolExposure();
        if (!symbolExps.isEmpty()) {
            double maxSym = symbolExps.values().stream().mapToDouble(d -> d).max().orElse(0.0);
            String maxSymbol = symbolExps.entrySet().stream()
                    .max((a, b) -> Double.compare(a.getValue(), b.getValue()))
                    .map(Map.Entry::getKey).orElse("N/A");
            sb.append(String.format("Largest symbol: %s (%.1f%%) ", maxSymbol, maxSym));
        }
        
        Map<String, Double> assetExps = context.getAssetClassExposure();
        if (!assetExps.isEmpty()) {
            double maxAsset = assetExps.values().stream().mapToDouble(d -> d).max().orElse(0.0);
            String maxAssetClass = assetExps.entrySet().stream()
                    .max((a, b) -> Double.compare(a.getValue(), b.getValue()))
                    .map(Map.Entry::getKey).orElse("N/A");
            sb.append(String.format("Largest asset class: %s (%.1f%%) ", maxAssetClass, maxAsset));
        }
        
        return sb.toString();
    }
}
