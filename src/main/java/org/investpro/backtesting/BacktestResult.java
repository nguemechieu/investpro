package org.investpro.backtesting;

import lombok.Getter;

import java.util.*;

/**
 * Contains results and performance metrics from a completed backtest
 */
@Getter
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
    private List<TradeRecord> trades;
    private final Map<String, Object> additionalMetrics;

    public BacktestResult() {
        this.trades = new ArrayList<>();
        this.additionalMetrics = new HashMap<>();
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public void setInitialBalance(double initialBalance) {
        this.initialBalance = initialBalance;
    }

    public void setFinalBalance(double finalBalance) {
        this.finalBalance = finalBalance;
    }

    public void setTotalReturn(double totalReturn) {
        this.totalReturn = totalReturn;
    }

    public void setReturnPercent(double returnPercent) {
        this.returnPercent = returnPercent;
    }

    public void setTotalTrades(int totalTrades) {
        this.totalTrades = totalTrades;
    }

    public void setWinningTrades(int winningTrades) {
        this.winningTrades = winningTrades;
    }

    public void setLosingTrades(int losingTrades) {
        this.losingTrades = losingTrades;
    }

    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }

    public void setTotalProfit(double totalProfit) {
        this.totalProfit = totalProfit;
    }

    public void setTotalLoss(double totalLoss) {
        this.totalLoss = totalLoss;
    }

    public void setAverageWin(double averageWin) {
        this.averageWin = averageWin;
    }

    public void setAverageLoss(double averageLoss) {
        this.averageLoss = averageLoss;
    }

    public void setProfitFactor(double profitFactor) {
        this.profitFactor = profitFactor;
    }

    public void setMaxDrawdown(double maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public void setSharpeRatio(double sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public void setSortinoRatio(double sortinoRatio) {
        this.sortinoRatio = sortinoRatio;
    }

    public void setCalmarRatio(double calmarRatio) {
        this.calmarRatio = calmarRatio;
    }

    public void setExpectedValue(double expectedValue) {
        this.expectedValue = expectedValue;
    }

    public void setBackTestDuration(long backTestDuration) {
        this.backTestDuration = backTestDuration;
    }

    public void setTrades(List<TradeRecord> trades) {
        this.trades = trades;
    }

    public void addAdditionalMetric(String key, Object value) {
        this.additionalMetrics.put(key, value);
    }

    /**
     * Represents a single trade executed during backtesting
     */
    public static class TradeRecord {
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

        // Getters and Setters
        public long getEntryTime() {
            return entryTime;
        }

        public void setEntryTime(long entryTime) {
            this.entryTime = entryTime;
        }

        public long getExitTime() {
            return exitTime;
        }

        public void setExitTime(long exitTime) {
            this.exitTime = exitTime;
        }

        public String getEntrySignal() {
            return entrySignal;
        }

        public void setEntrySignal(String entrySignal) {
            this.entrySignal = entrySignal;
        }

        public String getExitSignal() {
            return exitSignal;
        }

        public void setExitSignal(String exitSignal) {
            this.exitSignal = exitSignal;
        }

        public double getEntryPrice() {
            return entryPrice;
        }

        public void setEntryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
        }

        public double getExitPrice() {
            return exitPrice;
        }

        public void setExitPrice(double exitPrice) {
            this.exitPrice = exitPrice;
        }

        public double getQuantity() {
            return quantity;
        }

        public void setQuantity(double quantity) {
            this.quantity = quantity;
        }

        public double getProfit() {
            return profit;
        }

        public void setProfit(double profit) {
            this.profit = profit;
        }

        public double getProfitPercent() {
            return profitPercent;
        }

        public void setProfitPercent(double profitPercent) {
            this.profitPercent = profitPercent;
        }

        public double getFee() {
            return fee;
        }

        public void setFee(double fee) {
            this.fee = fee;
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
