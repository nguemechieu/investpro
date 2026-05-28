package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;


import java.util.ArrayList;
import java.util.List;

/**
 * Current risk state of the entire portfolio.
 * Used to assess portfolio health and adapt decisions.
 */
@Getter
@Builder
public class PortfolioRiskState {
    
    public enum RiskStatus {
        NORMAL("Portfolio operating within normal parameters"),
        WATCH("Elevated risk - monitor closely"),
        DEFENSIVE("Reduce exposure - entering defensive mode"),
        DANGER("Critical risk - should not open new positions"),
        STOP_TRADING("Portfolio on pause - risk too high");
        
        public final String description;
        
        RiskStatus(String description) {
            this.description = description;
        }
    }
    
    @Builder.Default
    private final double portfolioHeat = 0.0; // % of equity at risk
    
    @Builder.Default
    private final double totalExposure = 0.0; // % of capital exposed
    
    @Builder.Default
    private final double netExposure = 0.0; // net long/short exposure
    
    @Builder.Default
    private final double grossExposure = 0.0; // absolute exposure
    
    @Builder.Default
    private final double marginUsagePercent = 0.0; // % of available margin used
    
    @Builder.Default
    private final double cashUsagePercent = 0.0; // % of cash deployed
    
    @Builder.Default
    private final double currentDrawdownPercent = 0.0; // current drawdown from peak
    
    @Builder.Default
    private final double dailyLossPercent = 0.0; // daily loss %
    
    @Builder.Default
    private final double largestPositionPercent = 0.0; // largest position % of equity
    
    @Builder.Default
    private final double largestStrategyExposurePercent = 0.0; // largest strategy % of portfolio
    
    @Builder.Default
    private final double largestAssetClassExposurePercent = 0.0; // largest asset class % of portfolio
    
    @Builder.Default
    private final double largestBrokerExposurePercent = 0.0; // largest broker % of portfolio
    
    @Builder.Default
    private final int openPositionCount = 0; // number of open positions
    
    @Builder.Default
    private final RiskStatus riskStatus = RiskStatus.NORMAL;
    
    @Builder.Default
    private final List<String> reasons = new ArrayList<>();
    
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    
    public boolean isInNormalState() {
        return riskStatus == RiskStatus.NORMAL;
    }
    
    public boolean isInWatchState() {
        return riskStatus == RiskStatus.WATCH;
    }
    
    public boolean isDefensive() {
        return riskStatus == RiskStatus.DEFENSIVE || riskStatus == RiskStatus.DANGER || riskStatus == RiskStatus.STOP_TRADING;
    }
    
    public boolean isCritical() {
        return riskStatus == RiskStatus.DANGER || riskStatus == RiskStatus.STOP_TRADING;
    }
    
    public boolean shouldStopTrading() {
        return riskStatus == RiskStatus.STOP_TRADING;
    }
    
    public boolean isLeveraged() {
        return grossExposure > 100.0;
    }
    
    public double getLeverageRatio() {
        return grossExposure > 0 ? grossExposure / 100.0 : 1.0;
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioRiskState{heat=%.1f%%, exposure=%.1f%%, margin=%.1f%%, dd=%.2f%%, status=%s, pos=%d}",
                portfolioHeat, totalExposure, marginUsagePercent, currentDrawdownPercent, riskStatus, openPositionCount);
    }
}
