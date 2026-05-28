package org.investpro.risk;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;

/**
 * Institutional Risk Management System
 * Calculates position size, stop loss, and take profit based on account risk parameters
 */
@Data
@Slf4j

public class RiskManager {
    // Risk parameters
    private double accountBalance;
    private double riskPercentage = 2.0;  // Risk 2% of account per trade
    private double rewardRiskRatio = 2.0; // Risk-reward ratio (1:2)
    private boolean useVolatilityBased = true;
    private double maxPositionSize = 10.0; // Max position size in base units

    // Volatility calculation period

    public RiskManager(double accountBalance) {
        this.accountBalance = Math.max(100, accountBalance);
        log.info("RiskManager initialized with account balance: ${}", accountBalance);
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
            log.warn("Invalid inputs for risk calculation");
            return TradeRiskParameters.EMPTY;
        }
        
        double currentPrice = (ticker.getBidPrice() + ticker.getAskPrice()) / 2.0;
        if (currentPrice <= 0) {
            currentPrice = ticker.getAskPrice();
        }
        
        // Calculate position size
        double positionSize = calculatePositionSize(currentPrice);
        
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
        
        log.info("Risk Parameters for {}: size={}, entry={}, SL={} ({}%), TP={} ({}%), risk=${}",
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
    private double calculatePositionSize(double currentPrice) {
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
    @Contract("_, _, _ -> new")
    private @NotNull StopLossLevel calculateStopLoss(double currentPrice, double volatility, boolean isBuy) {
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
        log.info("Account balance updated to: ${}", newBalance);
    }
    
    /**
     * Set risk percentage per trade
     */
    public void setRiskPercentage(double percentage) {
        this.riskPercentage = Math.max(0.1, Math.min(5.0, percentage));
        log.info("Risk percentage set to: {}%", this.riskPercentage);
    }
    
    /**
     * Set risk-reward ratio
     */
    public void setRewardRiskRatio(double ratio) {
        this.rewardRiskRatio = Math.max(1.0, Math.min(5.0, ratio));
        log.info("Reward-Risk ratio set to: 1:{}", this.rewardRiskRatio);
    }
    


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
