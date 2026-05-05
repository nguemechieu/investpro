package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;
import java.time.LocalDateTime;

/**
 * Configuration for backtesting parameters and settings
 */
@Setter
@Getter
public class BacktestConfig {
    // Getters and Setters
    private TradePair tradePair;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private double initialBalance;
    private double commissionPercent;
    private int leverageRatio;
    private boolean marginEnabled;
    private double maxPositionSize;
    private boolean useRealFees;

    public BacktestConfig(TradePair tradePair, LocalDateTime startDate, LocalDateTime endDate, 
                          double initialBalance) {
        this.tradePair = tradePair;
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialBalance = initialBalance;
        this.commissionPercent = 0.1;
        this.leverageRatio = 1;
        this.marginEnabled = false;
        this.maxPositionSize = initialBalance;
        this.useRealFees = true;
    }

}
