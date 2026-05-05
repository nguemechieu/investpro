package org.investpro.risk;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;

/**
 * Institutional Risk Management System
 * Calculates position size, stop loss, and take profit based on account risk parameters
 */
@Getter
@Setter

public class RiskManager {
    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);
    
    // Risk parameters
    private double accountBalance;
    private double riskPercentage = 2.0;  // Risk 2% of account per trade
    private double rewardRiskRatio = 2.0; // Risk-reward ratio (1:2)
    private boolean useVolatilityBased = true;
    private double maxPositionSize = 10.0; // Max position size in base units
    
    // Volatility calculation period
    private static final int VOLATILITY_PERIOD = 14;
    
    public RiskManager(double accountBalance) {
        this.accountBalance = Math.max(100, accountBalance);
        logger.info("RiskManager initialized with account balance: ${}", accountBalance);
    }
    
    /**
     * Calculate trade parameters based on account risk
     */
    public TradeRiskParameters calculateRiskParameters(
            TradePair tradePair,
            Ticker ticker,
            double volatility,
            boolean isBuySignal) {
        
        if (ticker == null || tradePair == null) {
            logger.warn("Invalid inputs for risk calculation");
            return TradeRiskParameters.EMPTY;
        }
        
        double currentPrice = (ticker.getBidPrice() + ticker.getAskPrice()) / 2.0;
        if (currentPrice <= 0) {
            currentPrice = ticker.getAskPrice();
        }
        
        // Calculate position size
        double positionSize = calculatePositionSize(tradePair, currentPrice);
        
        // Calculate stop loss and take profit
        StopLossLevel stopLossLevel = calculateStopLoss(
            currentPrice, volatility, isBuySignal);
        
        double stopLossPrice = stopLossLevel.price;
        double stopLossPercent = Math.abs((stopLossPrice - currentPrice) / currentPrice) * 100;
        
        // Calculate take profit based on risk-reward ratio
        double takeProfitPrice = calculateTakeProfit(
            currentPrice, stopLossPrice, isBuySignal);
        
        double takeProfitPercent = Math.abs((takeProfitPrice - currentPrice) / currentPrice) * 100;
        
        // Calculate risk amount in USD
        double riskAmount = (accountBalance * riskPercentage) / 100.0;
        
        logger.info("Risk Parameters for {}: size={}, entry={}, SL={} ({}%), TP={} ({}%), risk=${}",
            tradePair, positionSize, currentPrice, stopLossPrice, stopLossPercent, 
            takeProfitPrice, takeProfitPercent, riskAmount);
        
        return new TradeRiskParameters(
            positionSize,
            currentPrice,
            stopLossPrice,
            stopLossPercent,
            takeProfitPrice,
            takeProfitPercent,
            riskAmount,
            stopLossLevel.method
        );
    }
    
    /**
     * Calculate position size using fixed fractional position sizing
     */
    private double calculatePositionSize(TradePair tradePair, double currentPrice) {
        // Risk amount = account balance * risk percentage
        double riskAmount = (accountBalance * riskPercentage) / 100.0;
        
        // Position size = risk amount / price
        double positionSize = riskAmount / currentPrice;
        
        // Cap maximum position size
        positionSize = Math.min(positionSize, maxPositionSize);
        
        // Normalize to min trade size (typically 0.001 - 1.0 depending on instrument)
        positionSize = Math.round(positionSize * 100000.0) / 100000.0;
        
        return Math.max(0.001, positionSize);
    }
    
    /**
     * Calculate stop loss using volatility-based ATR (Average True Range)
     */
    private StopLossLevel calculateStopLoss(double currentPrice, double volatility, boolean isBuy) {
        double atrValue = currentPrice * (volatility / 100.0);
        
        // Use 2x ATR for stop loss
        double stopLossATR = atrValue * 2.0;
        
        double stopLossPrice;
        if (isBuy) {
            stopLossPrice = currentPrice - stopLossATR;
        } else {
            stopLossPrice = currentPrice + stopLossATR;
        }
        
        // Minimum stop loss distance (0.5%)
        double minStopLoss = currentPrice * 0.005;
        double actualStopLoss = isBuy ? 
            Math.min(stopLossPrice, currentPrice - minStopLoss) :
            Math.max(stopLossPrice, currentPrice + minStopLoss);
        
        return new StopLossLevel(actualStopLoss, "ATR-2x");
    }
    
    /**
     * Calculate take profit using risk-reward ratio
     */
    private double calculateTakeProfit(double entryPrice, double stopLossPrice, boolean isBuy) {
        double stopDistance = Math.abs(entryPrice - stopLossPrice);
        double tpDistance = stopDistance * rewardRiskRatio;
        
        if (isBuy) {
            return entryPrice + tpDistance;
        } else {
            return entryPrice - tpDistance;
        }
    }
    
    /**
     * Update account balance (called after trades)
     */
    public void updateAccountBalance(double newBalance) {
        this.accountBalance = Math.max(100, newBalance);
        logger.info("Account balance updated to: ${}", newBalance);
    }
    
    /**
     * Set risk percentage per trade
     */
    public void setRiskPercentage(double percentage) {
        this.riskPercentage = Math.max(0.1, Math.min(5.0, percentage));
        logger.info("Risk percentage set to: {}%", this.riskPercentage);
    }
    
    /**
     * Set risk-reward ratio
     */
    public void setRewardRiskRatio(double ratio) {
        this.rewardRiskRatio = Math.max(1.0, Math.min(5.0, ratio));
        logger.info("Reward-Risk ratio set to: 1:{}", this.rewardRiskRatio);
    }
    
    /**
     * Set maximum position size
     */
    public void setMaxPositionSize(double maxSize) {
        this.maxPositionSize = Math.max(0.001, maxSize);
        logger.info("Max position size set to: {}", this.maxPositionSize);
    }
    
    /**
     * Enable/disable volatility-based stop loss
     */
    public void setUseVolatilityBased(boolean use) {
        this.useVolatilityBased = use;
        logger.info("Volatility-based stops: {}", use ? "ENABLED" : "DISABLED");
    }
    
    public double getAccountBalance() { return accountBalance; }
    public double getRiskPercentage() { return riskPercentage; }
    public double getRewardRiskRatio() { return rewardRiskRatio; }
    public double getMaxPositionSize() { return maxPositionSize; }
    public boolean isUsingVolatilityBased() { return useVolatilityBased; }
    
    @Override
    public String toString() {
        return String.format("RiskManager{balance=$%.2f, risk=%.1f%%, ratio=1:%.1f, maxSize=%.4f}",
            accountBalance, riskPercentage, rewardRiskRatio, maxPositionSize);
    }
    
    /**
     * Helper class for stop loss calculation
     */
    private static class StopLossLevel {
        double price;
        String method;
        
        StopLossLevel(double price, String method) {
            this.price = price;
            this.method = method;
        }
    }
}
