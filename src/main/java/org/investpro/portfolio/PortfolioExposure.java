package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Portfolio-level exposure summary across all dimensions.
 * Tracks what percentage of capital is exposed in each direction.
 */
@Getter
@Builder
public class PortfolioExposure {
    
    @Builder.Default
    private final double totalExposure = 0.0; // sum of all position exposures %
    
    @Builder.Default
    private final double netExposure = 0.0; // long - short exposure %
    
    @Builder.Default
    private final double grossExposure = 0.0; // |long| + |short| exposure %
    
    @Builder.Default
    private final double longExposure = 0.0; // long positions only %
    
    @Builder.Default
    private final double shortExposure = 0.0; // short positions only %
    
    // Symbol-level exposure
    @Builder.Default
    private final Map<String, Double> symbolExposure = new HashMap<>();
    
    // Asset class exposure
    @Builder.Default
    private final Map<String, Double> assetClassExposure = new HashMap<>();
    
    // Strategy exposure
    @Builder.Default
    private final Map<String, Double> strategyExposure = new HashMap<>();
    
    // Broker exposure
    @Builder.Default
    private final Map<String, Double> brokerExposure = new HashMap<>();
    
    // Contract type exposure
    @Builder.Default
    private final Map<String, Double> contractTypeExposure = new HashMap<>();
    
    // Quote currency exposure (for forex)
    @Builder.Default
    private final Map<String, Double> quoteCurrencyExposure = new HashMap<>();
    
    public double getSymbolExposure(@NotNull String symbol) {
        return symbolExposure.getOrDefault(symbol, 0.0);
    }
    
    public double getAssetClassExposure(@NotNull String assetClass) {
        return assetClassExposure.getOrDefault(assetClass, 0.0);
    }
    
    public double getStrategyExposure(@NotNull String strategyId) {
        return strategyExposure.getOrDefault(strategyId, 0.0);
    }
    
    public double getBrokerExposure(@NotNull String broker) {
        return brokerExposure.getOrDefault(broker, 0.0);
    }
    
    public boolean isLeveraged() {
        return grossExposure > 100.0;
    }
    
    public double getLeverageRatio() {
        return grossExposure > 0 ? grossExposure / 100.0 : 1.0;
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioExposure{total=%.1f%%, net=%.1f%%, gross=%.1f%%, long=%.1f%%, short=%.1f%%, leverage=%.2fx}",
                totalExposure, netExposure, grossExposure, longExposure, shortExposure, getLeverageRatio());
    }
}
