package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.models.trading.TradePair;
import java.time.LocalDateTime;

/**
 * Configuration for backtesting parameters and settings
 */
@Setter
@Getter
@Slf4j
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
    private int equityCurveSampling;
    private boolean enableMetrics;
    private boolean enableResourceProtection;
    private int maxTradesPerSimulation;
    private boolean enableIncrementalStatistics;
    private long simulatedLatencyMillis;
    private double slippagePercent;
    private double spreadPercent;

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
        this.equityCurveSampling = Math.max(1, AppConfig.getInt("backtest.equityCurveSampling", 4));
        this.enableMetrics = AppConfig.getBoolean("backtest.enableMetrics", true);
        this.enableResourceProtection = AppConfig.getBoolean("backtest.enableResourceProtection", true);
        this.maxTradesPerSimulation = Math.max(1, AppConfig.getInt("backtest.maxTradesPerSimulation", 10000));
        this.enableIncrementalStatistics = AppConfig.getBoolean("backtest.enableIncrementalStatistics", true);
        this.simulatedLatencyMillis = Math.max(0L, AppConfig.getLong("backtest.simulatedLatencyMillis", 0L));
        this.slippagePercent = Math.max(0.0, AppConfig.getDouble("backtest.slippagePercent", 0.02));
        this.spreadPercent = Math.max(0.0, AppConfig.getDouble("backtest.spreadPercent", 0.01));
        log.debug("BacktestConfig initialBalance: {}", initialBalance);
    }

}
