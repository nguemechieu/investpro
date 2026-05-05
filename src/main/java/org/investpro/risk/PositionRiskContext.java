package org.investpro.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.Position;

import java.time.LocalDateTime;

/**
 * Complete context for analyzing open position risk.
 * Contains all the data needed to calculate PositionHealthScore and PositionRiskDecision.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class PositionRiskContext {
    
    // =========================================================================
    // Position Data
    // =========================================================================
    
    private Position position;
    
    // =========================================================================
    // Market Data
    // =========================================================================
    
    private double currentPrice;
    private double volatility;
    private double atr;
    private double spread;
    
    // =========================================================================
    // Account Data
    // =========================================================================
    
    private double accountEquity;
    private double totalPortfolioHeat;
    private double currentDrawdown;
    
    // =========================================================================
    // Risk Configuration
    // =========================================================================
    
    private RiskProfile riskProfile;
    private CapitalProtection capitalProtection;
    private MarketBehavior marketBehavior;
    private LiquidityProfile liquidityProfile;
    
    // =========================================================================
    // Time Data
    // =========================================================================
    
    private LocalDateTime now;
    private long positionAgeMinutes;
    
    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Check if context has all required data.
     */
    public boolean isComplete() {
        return position != null
                && accountEquity > 0
                && Double.isFinite(currentPrice)
                && Double.isFinite(volatility)
                && riskProfile != null
                && capitalProtection != null
                && now != null;
    }
    
    /**
     * Get position notional value (quantity * current price).
     */
    public double getPositionNotional() {
        if (position == null) return 0;
        return Math.abs(position.getQuantity()) * currentPrice;
    }
    
    /**
     * Get position exposure as % of account.
     */
    public double getExposurePercent() {
        if (accountEquity <= 0) return 0;
        return (getPositionNotional() / accountEquity) * 100;
    }
    
    /**
     * Get unrealized P&L.
     */
    public double getUnrealizedPnl() {
        if (position == null) return 0;
        double priceDiff = currentPrice - position.getEntryPrice();
        if (position.isBuy()) {
            return priceDiff * position.getQuantity();
        } else {
            return -priceDiff * position.getQuantity();
        }
    }
    
    /**
     * Get unrealized P&L percentage.
     */
    public double getUnrealizedPnlPercent() {
        if (position == null || position.getEntryPrice() <= 0) return 0;
        double priceDiff = currentPrice - position.getEntryPrice();
        return (priceDiff / position.getEntryPrice()) * 100;
    }
}
