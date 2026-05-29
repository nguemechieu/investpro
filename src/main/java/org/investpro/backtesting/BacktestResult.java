package org.investpro.backtesting;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Contains results and performance metrics from a completed backtest
 */
@Data
public class BacktestResult {
    // Core metrics
    private String strategyName;
    private double initialBalance;
    private double finalBalance;
    private double totalReturn;
    private double returnPercent;
    // Trade statistics
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private double totalProfit;
    private double totalLoss;
    private double averageWin;
    private double averageLoss;
    private double profitFactor;
    // Risk metrics
    private double maxDrawdown;
    private double sharpeRatio;
    private double sortinoRatio;
    private double calmarRatio;
    private double expectedValue;
    // Execution metrics
    private long backTestDuration; // in milliseconds
    private final List<TradeRecord> trades;
    private final Map<String, Object> additionalMetrics;

    public BacktestResult() {
        this.trades = new ArrayList<>();
        this.additionalMetrics = new HashMap<>();
    }


    public void addAdditionalMetric(String key, Object value) {
        this.additionalMetrics.put(key, value);
    }

    public void setTrades(ArrayList<TradeRecord> tradeRecords) {
        this.trades.clear();
        this.trades.addAll(tradeRecords);
    }

    /**
     * Represents a single trade executed during backtesting
     */
    @Setter
    @Getter

    public static class TradeRecord {
        // Getters and Setters
        private long entryTime;
        private long exitTime;
        private String entrySignal;
        private String exitSignal;
        private double entryPrice;
        private double exitPrice;
        private double quantity;
        private double profit;
        private double profitPercent;
        private double fee;

        public TradeRecord(long entryTime, double entryPrice, double quantity) {
            this.entryTime = entryTime;
            this.entryPrice = entryPrice;
            this.quantity = quantity;
        }

    }

    @Override
    public String toString() {
        return String.format(
            "BacktestResult{strategy='%s', return=%.2f%%, sharpe=%.2f, winRate=%.2f%%, trades=%d, maxDD=%.2f%%}",
            strategyName, returnPercent, sharpeRatio, winRate * 100, totalTrades, maxDrawdown
        );
    }
}
