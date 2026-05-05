package org.investpro.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Position;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Calculates portfolio heat (total open risk as % of equity).
 * Portfolio heat = sum of all position risks / account equity * 100
 */
@Slf4j
public class PortfolioHeatCalculator {
    
    // Risk limits by portfolio state
    private static final double NORMAL_HEAT_LIMIT = 10.0; // 10% max heat in normal conditions
    private static final double WATCH_HEAT_LIMIT = 7.0;
    private static final double DEFENSIVE_HEAT_LIMIT = 3.0;
    private static final double DANGER_HEAT_LIMIT = 1.0;
    private static final double CRITICAL_STOP_HEAT_LIMIT = 0.5;
    
    // Risk estimate for positions without stop loss
    private static final double DEFAULT_RISK_PERCENT = 2.0; // Assume 2% risk if stop loss missing
    
    /**
     * Calculate total portfolio heat from open positions.
     */
    public double calculatePortfolioHeat(@NotNull List<Position> positions, double accountEquity) {
        if (accountEquity <= 0 || positions.isEmpty()) {
            return 0.0;
        }
        
        double totalRisk = 0.0;
        
        for (Position position : positions) {
            double positionRisk = calculatePositionRisk(position);
            totalRisk += positionRisk;
            log.debug("Position {}: risk={}", position, positionRisk);
        }
        
        double portfolioHeat = (totalRisk / accountEquity) * 100;
        log.debug("Portfolio heat: {} positions, total risk: {}, equity: {}, heat: {}%", 
                  positions.size(), totalRisk, accountEquity, portfolioHeat);
        
        return Math.min(100.0, portfolioHeat); // Cap at 100%
    }
    
    /**
     * Calculate risk for a single position.
     * If stop loss available: |entryPrice - stopLoss| * quantity
     * If no stop loss: Use conservative estimated risk
     */
    private double calculatePositionRisk(@NotNull Position position) {
        double entryPrice = position.getEntryPrice();
        double currentPrice = position.getCurrentPrice();
        double quantity = Math.abs(position.getQuantity());
        
        // If stop loss is set, use it for risk calculation
        if (hasStopLoss(position)) {
            double stopLoss = position.getStopLoss();
            double riskPerUnit = Math.abs(entryPrice - stopLoss);
            double positionRisk = riskPerUnit * quantity;
            
            // Adjust for leverage/margin if applicable
            if (position.getLeverage() > 1) {
                positionRisk *= position.getLeverage();
            }
            
            return positionRisk;
        }
        
        // No stop loss: use conservative estimate
        // Assume risk is DEFAULT_RISK_PERCENT of entry price per unit
        double conservativeRisk = (entryPrice * quantity * DEFAULT_RISK_PERCENT / 100.0);
        
        if (position.getLeverage() > 1) {
            conservativeRisk *= position.getLeverage();
        }
        
        log.warn("Position {} has no stop loss - using conservative risk estimate: {}", 
                 position, conservativeRisk);
        
        return conservativeRisk;
    }
    
    /**
     * Determine risk status based on portfolio heat.
     */
    @NotNull
    public PortfolioRiskState.RiskStatus determineRiskStatus(double portfolioHeat, double currentDrawdown) {
        // Adjust limits based on drawdown
        double effectiveHeatLimit = NORMAL_HEAT_LIMIT;
        
        if (currentDrawdown > 15) {
            effectiveHeatLimit = DANGER_HEAT_LIMIT;
        } else if (currentDrawdown > 10) {
            effectiveHeatLimit = DEFENSIVE_HEAT_LIMIT;
        } else if (currentDrawdown > 5) {
            effectiveHeatLimit = WATCH_HEAT_LIMIT;
        }
        
        if (portfolioHeat >= effectiveHeatLimit * 2) {
            return PortfolioRiskState.RiskStatus.STOP_TRADING;
        } else if (portfolioHeat >= effectiveHeatLimit * 1.5) {
            return PortfolioRiskState.RiskStatus.DANGER;
        } else if (portfolioHeat >= effectiveHeatLimit * 1.1) {
            return PortfolioRiskState.RiskStatus.DEFENSIVE;
        } else if (portfolioHeat >= effectiveHeatLimit * 0.8) {
            return PortfolioRiskState.RiskStatus.WATCH;
        } else {
            return PortfolioRiskState.RiskStatus.NORMAL;
        }
    }
    
    /**
     * Calculate portfolio heat after adding a candidate trade.
     */
    public double calculatePortfolioHeatAfterTrade(
            @NotNull List<Position> currentPositions,
            double accountEquity,
            double candidateEntryPrice,
            double candidateStopLoss,
            double candidateQuantity,
            double candidateLeverage) {
        
        double currentHeat = calculatePortfolioHeat(currentPositions, accountEquity);
        
        double candidateRisk;
        if (candidateStopLoss > 0 && Math.abs(candidateEntryPrice - candidateStopLoss) > 0.00001) {
            candidateRisk = Math.abs(candidateEntryPrice - candidateStopLoss) * candidateQuantity;
        } else {
            candidateRisk = (candidateEntryPrice * candidateQuantity * DEFAULT_RISK_PERCENT / 100.0);
            log.warn("Candidate trade has no stop loss - using conservative estimate");
        }
        
        if (candidateLeverage > 1) {
            candidateRisk *= candidateLeverage;
        }
        
        double additionalHeat = (candidateRisk / accountEquity) * 100;
        return currentHeat + additionalHeat;
    }
    
    /**
     * Get the heat limit for a given risk status.
     */
    public static double getHeatLimitForStatus(@NotNull PortfolioRiskState.RiskStatus status) {
        return switch (status) {
            case NORMAL -> NORMAL_HEAT_LIMIT;
            case WATCH -> WATCH_HEAT_LIMIT;
            case DEFENSIVE -> DEFENSIVE_HEAT_LIMIT;
            case DANGER -> DANGER_HEAT_LIMIT;
            case STOP_TRADING -> CRITICAL_STOP_HEAT_LIMIT;
        };
    }
    
    private boolean hasStopLoss(@NotNull Position position) {
        return position.hasStopLoss() && position.getStopLoss() > 0;
    }
}
