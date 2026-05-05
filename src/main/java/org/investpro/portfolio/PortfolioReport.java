package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portfolio summary report with current state, metrics, and health indicators.
 */
@Getter
@Builder
public class PortfolioReport {
    
    @NotNull
    private final String accountId;
    
    @Builder.Default
    private final Instant generatedAt = Instant.now();
    
    // Account metrics
    @Builder.Default
    private final double totalEquity = 0.0;
    
    @Builder.Default
    private final double availableCash = 0.0;
    
    @Builder.Default
    private final double totalMarginUsed = 0.0;
    
    @Builder.Default
    private final double marginUsagePercent = 0.0;
    
    // Performance
    @Builder.Default
    private final double dailyPnl = 0.0;
    
    @Builder.Default
    private final double dailyReturnPercent = 0.0;
    
    @Builder.Default
    private final double weeklyPnl = 0.0;
    
    @Builder.Default
    private final double monthlyPnl = 0.0;
    
    @Builder.Default
    private final double currentDrawdownPercent = 0.0;
    
    @Builder.Default
    private final double maxDrawdownPercent = 0.0;
    
    // Risk state
    @Builder.Default
    private final double portfolioHeat = 0.0;
    
    @Builder.Default
    private final double totalExposure = 0.0;
    
    @Builder.Default
    private final double netExposure = 0.0;
    
    @Builder.Default
    private final double grossExposure = 0.0;
    
    @Builder.Default
    private final PortfolioRiskState.RiskStatus riskStatus = PortfolioRiskState.RiskStatus.NORMAL;
    
    @Builder.Default
    private final int openPositionCount = 0;
    
    // Concentration
    @Builder.Default
    private final double largestPositionPercent = 0.0;
    
    @Builder.Default
    private final String largestPositionSymbol = "";
    
    @Builder.Default
    private final double largestStrategyExposure = 0.0;
    
    @Builder.Default
    private final String largestStrategyId = "";
    
    @Builder.Default
    private final double largestAssetClassExposure = 0.0;
    
    @Builder.Default
    private final String largestAssetClass = "";
    
    // Exposure breakdown
    @Builder.Default
    private final Map<String, Double> topSymbolExposures = new HashMap<>(); // Top 5 symbols
    
    @Builder.Default
    private final Map<String, Double> strategyExposures = new HashMap<>();
    
    @Builder.Default
    private final Map<String, Double> assetClassExposures = new HashMap<>();
    
    // Activity
    @Builder.Default
    private final int tradesOpenedToday = 0;
    
    @Builder.Default
    private final int tradesClosedToday = 0;
    
    @Builder.Default
    private final int rebalanceEventsToday = 0;
    
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    
    @Builder.Default
    private final List<String> alerts = new ArrayList<>();
    
    // Summary
    @Builder.Default
    private final String overallHealthStatus = "NORMAL"; // NORMAL, CAUTION, WARNING, CRITICAL
    
    @Builder.Default
    private final String summary = "";
    
    public boolean isHealthy() {
        return "NORMAL".equalsIgnoreCase(overallHealthStatus);
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasAlerts() {
        return !alerts.isEmpty();
    }
    
    public double getLeverageRatio() {
        return totalEquity > 0 ? grossExposure / 100.0 : 1.0;
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioReport{equity=%.2f, cash=%.2f, margin=%.1f%%, heat=%.1f%%, exposure=%.1f%%, dd=%.2f%%, status=%s, pos=%d}",
                totalEquity, availableCash, marginUsagePercent, portfolioHeat, totalExposure, 
                currentDrawdownPercent, riskStatus, openPositionCount);
    }
}
