package org.investpro.risk;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.Position;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Monitors the health and risk of an open position.
 * Calculates PositionHealthScore based on deterministic risk rules and metrics.
 *
 * Does NOT make AI recommendations—only calculates health.
 * Risk decisions are made by PositionActionFinalGate after receiving AI recommendations.
 */
@Getter
@Setter
@AllArgsConstructor
public class OpenPositionRiskMonitor {
    
    private static final Logger logger = Logger.getLogger(OpenPositionRiskMonitor.class.getName());
    
    // =========================================================================
    // Configuration Constants
    // =========================================================================
    
    private static final double CRITICAL_RISK_PERCENT = 3.0;  // If position risk > 3% of account, critical
    private static final double WARNING_RISK_PERCENT = 2.0;   // If position risk > 2% of account, warning
    private static final double GOOD_STOP_DISTANCE_BPS = 200; // Stop distance should be > 200 bps
    private static final double CRITICAL_STOP_DISTANCE_BPS = 50; // Stop distance < 50 bps is critical
    private static final double MIN_PROFIT_FOR_TRAIL = 100;   // Don't trail stop unless profit > 100 bps
    
    /**
     * Analyze an open position and calculate its health score.
     *
     * @param context Complete position risk context
     * @return Position health score with deterministic signals
     */
    public PositionHealthScore calculateHealthScore(PositionRiskContext context) {
        if (context == null || !context.isComplete()) {
            logger.warning("Position risk context is incomplete");
            return PositionHealthScore.builder()
                    .overallScore(0.2)
                    .status(PositionHealthScore.HealthStatus.EXIT_REQUIRED)
                    .deterministicExitRequired(true)
                    .deterministicExitReason("Incomplete position data")
                    .calculatedAt(LocalDateTime.now())
                    .summary("Cannot assess position health: incomplete data")
                    .build();
        }
        
        Position position = context.getPosition();
        
        // Calculate component scores
        double pnlScore = calculatePnLScore(position, context);
        double riskScore = calculateRiskScore(position, context);
        double technicalScore = calculateTechnicalScore(position, context);
        double liquidityScore = calculateLiquidityScore(context);
        double portfolioScore = calculatePortfolioScore(position, context);
        
        // Weighted average of components
        double overallScore = (pnlScore * 0.25) +
                              (riskScore * 0.25) +
                              (technicalScore * 0.20) +
                              (liquidityScore * 0.15) +
                              (portfolioScore * 0.15);
        
        // Clamp score to 0.0-1.0
        overallScore = Math.max(0.0, Math.min(1.0, overallScore));
        
        // Calculate distance metrics
        double distanceToStop = calculateDistanceToStop(position, context);
        double distanceToTP = calculateDistanceToTP(position, context);
        double distanceToLiq = calculateDistanceToLiquidation(position, context);
        
        // Calculate profit protected
        double profitProtected = calculateProfitProtected(position, context);
        
        // Calculate risk remaining
        double riskRemaining = calculateRiskRemaining(position, context);
        
        // Calculate portfolio heat contribution
        double portfolioHeatContribution = calculatePortfolioHeatContribution(position, context);
        
        // Determine deterministic exit requirement
        boolean exitRequired = false;
        String exitReason = "";
        
        // Rule 1: Stop loss too close
        if (distanceToStop > 0 && distanceToStop < CRITICAL_STOP_DISTANCE_BPS) {
            exitRequired = true;
            exitReason = "Stop loss too close (" + String.format("%.0f bps", distanceToStop) + ")";
        }
        
        // Rule 2: Max drawdown rule (account-level)
        if (context.getCurrentDrawdown() > context.getCapitalProtection().getMaxDrawdownPercent()) {
            exitRequired = true;
            exitReason = "Account drawdown exceeded: " + String.format("%.1f%%", context.getCurrentDrawdown());
        }
        
        // Rule 3: Portfolio heat too high
        if (context.getTotalPortfolioHeat() > context.getRiskProfile().getMaxPortfolioHeatPercent()) {
            exitRequired = true;
            exitReason = "Portfolio heat exceeded: " + String.format("%.1f%%", context.getTotalPortfolioHeat());
        }
        
        // Rule 4: Position size too large (risk > 3%)
        if (riskRemaining > CRITICAL_RISK_PERCENT) {
            exitRequired = true;
            exitReason = "Position risk too high: " + String.format("%.2f%%", riskRemaining);
        }
        
        // Rule 5: Position is underwater and stop is about to hit
        if (context.getUnrealizedPnlPercent() < -2.0 && distanceToStop < 100) {
            exitRequired = true;
            exitReason = "Position deeply underwater with tight stop";
        }
        
        // Determine health status
        PositionHealthScore.HealthStatus status = PositionHealthScore.statusFromScore(overallScore);
        
        // Build health score
        PositionHealthScore.PositionHealthScoreBuilder builder = PositionHealthScore.builder()
                .overallScore(overallScore)
                .status(exitRequired ? PositionHealthScore.HealthStatus.EXIT_REQUIRED : status)
                .pnlScore(pnlScore)
                .riskScore(riskScore)
                .technicalScore(technicalScore)
                .liquidityScore(liquidityScore)
                .portfolioScore(portfolioScore)
                .distanceToStopBps(distanceToStop)
                .distanceToTakeProfitBps(distanceToTP)
                .distanceToLiquidationBps(distanceToLiq)
                .riskRemainingPercent(riskRemaining)
                .profitProtectedPercent(profitProtected)
                .portfolioHeatContributionPercent(portfolioHeatContribution)
                .deterministicExitRequired(exitRequired)
                .deterministicExitReason(exitReason)
                .calculatedAt(LocalDateTime.now())
                .secondsUntilNextReview(60);
        
        // Check if thesis is invalidated
        boolean thesisInvalidated = false;
        List<String> invalidationReasons = new ArrayList<>();
        
        if (context.getUnrealizedPnlPercent() < -5.0) {
            thesisInvalidated = true;
            invalidationReasons.add("Position in severe drawdown");
        }
        
        if (technicalScore < 0.3) {
            thesisInvalidated = true;
            invalidationReasons.add("Technical conditions deteriorated");
        }
        
        builder.thesisInvalidated(thesisInvalidated)
               .thesisInvalidationReasons(invalidationReasons)
               .summary(buildHealthSummary(overallScore, status, distanceToStop, riskRemaining, exitRequired));
        
        return builder.build();
    }
    
    // =========================================================================
    // Component Score Calculations
    // =========================================================================
    
    private double calculatePnLScore(Position position, PositionRiskContext context) {
        double pnlPercent = context.getUnrealizedPnlPercent();
        
        if (pnlPercent > 5.0) return 1.0;      // Very profitable
        if (pnlPercent > 2.0) return 0.9;      // Profitable
        if (pnlPercent > 0.0) return 0.85;     // Slightly profitable
        if (pnlPercent > -1.0) return 0.7;     // Slightly negative
        if (pnlPercent > -2.0) return 0.5;     // Moderately negative
        if (pnlPercent > -5.0) return 0.3;     // Significantly negative
        return 0.1;                             // Severely negative
    }
    
    private double calculateRiskScore(Position position, PositionRiskContext context) {
        double distanceToStop = calculateDistanceToStop(position, context);
        
        if (distanceToStop < 0) return 0.8;    // No stop set (risky but not catastrophic)
        if (distanceToStop > 500) return 1.0;  // Very wide stop
        if (distanceToStop > 200) return 0.9;  // Good stop distance
        if (distanceToStop > 100) return 0.6;  // Tight stop
        if (distanceToStop > 50) return 0.3;   // Very tight stop
        return 0.1;                             // Critical stop distance
    }
    
    private double calculateTechnicalScore(Position position, PositionRiskContext context) {
        // Simplified: based on volatility and support/resistance
        double volatility = context.getVolatility();
        
        if (volatility < 20) return 0.9;       // Calm market
        if (volatility < 40) return 0.8;       // Normal volatility
        if (volatility < 60) return 0.6;       // Elevated volatility
        if (volatility < 100) return 0.4;      // High volatility
        return 0.2;                             // Extreme volatility
    }
    
    private double calculateLiquidityScore(PositionRiskContext context) {
        double spread = context.getSpread();
        
        if (spread < 5) return 1.0;            // Tight spread
        if (spread < 10) return 0.9;           // Good spread
        if (spread < 20) return 0.8;           // Acceptable spread
        if (spread < 50) return 0.5;           // Wide spread
        return 0.2;                             // Very wide spread
    }
    
    private double calculatePortfolioScore(Position position, PositionRiskContext context) {
        double heat = context.getTotalPortfolioHeat();
        double maxHeat = context.getRiskProfile().getMaxPortfolioHeatPercent();
        
        double utilization = heat / maxHeat;
        
        if (utilization < 0.3) return 1.0;     // Well within limits
        if (utilization < 0.5) return 0.9;     // Comfortable
        if (utilization < 0.7) return 0.7;     // Getting warm
        if (utilization < 0.9) return 0.4;     // High heat
        return 0.1;                             // Dangerously high heat
    }
    
    // =========================================================================
    // Distance & Risk Calculations
    // =========================================================================
    
    private double calculateDistanceToStop(Position position, PositionRiskContext context) {
        if (position.getStopLoss() == 0.0) return -1;
        
        double diff = Math.abs(context.getCurrentPrice() - position.getStopLoss());
        return (diff / context.getCurrentPrice()) * 10000; // Convert to basis points
    }
    
    private double calculateDistanceToTP(Position position, PositionRiskContext context) {
        if (position.getTakeProfit() == 0.0) return -1;
        
        double diff = Math.abs(context.getCurrentPrice() - position.getTakeProfit());
        return (diff / context.getCurrentPrice()) * 10000;
    }
    
    private double calculateDistanceToLiquidation(Position position, PositionRiskContext context) {
        if (position.getLeverage() <= 1.0) return -1; // No liquidation without leverage
        
        // Simplified calculation: liquidation ~ 95% drawdown on leveraged position
        double maxLoss = position.getEntryPrice() / position.getLeverage();
        double diff = Math.abs(context.getCurrentPrice() - maxLoss);
        return (diff / context.getCurrentPrice()) * 10000;
    }
    
    private double calculateProfitProtected(Position position, PositionRiskContext context) {
        double pnl = context.getUnrealizedPnl();
        if (pnl <= 0) return 0;
        
        double stopLoss = position.getStopLoss() != 0.0 ? position.getStopLoss() : 0;
        if (stopLoss == 0) return 0;
        
        double stopLossPnL = Math.abs(stopLoss - position.getEntryPrice()) * Math.signum(context.getUnrealizedPnl());
        return (stopLossPnL / pnl) * 100;
    }
    
    private double calculateRiskRemaining(Position position, PositionRiskContext context) {
        if (position.getStopLoss() == 0.0) {
            return (context.getPositionNotional() / context.getAccountEquity()) * 100;
        }
        
        double risk = Math.abs(context.getCurrentPrice() - position.getStopLoss()) * Math.abs(position.getQuantity());
        return (risk / context.getAccountEquity()) * 100;
    }
    
    private double calculatePortfolioHeatContribution(Position position, PositionRiskContext context) {
        if (context.getTotalPortfolioHeat() == 0) return 0;
        return calculateRiskRemaining(position, context);
    }
    
    // =========================================================================
    // Summary Building
    // =========================================================================
    
    private String buildHealthSummary(double score, PositionHealthScore.HealthStatus status, 
                                     double stopDistance, double riskRemaining, boolean exitRequired) {
        if (exitRequired) {
            return "CRITICAL: Position requires immediate action";
        }
        
        return String.format("Status: %s, Score: %.2f, Stop Distance: %.0f bps, Risk: %.2f%%",
                status.getLabel(),
                score,
                stopDistance,
                riskRemaining);
    }
}
