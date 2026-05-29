package org.investpro.backtesting;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Market simulator that executes trading strategies on historical data
 */
@Data
@Slf4j
public class Simulator {
    private BacktestStrategy strategy;
    private BacktestConfig config;
    private List<CandleData> historicalData;
    private PortfolioState portfolioState;
    private List<BacktestResult.TradeRecord> executedTrades;
    private List<Double> equityCurve;

    public Simulator(BacktestStrategy strategy, BacktestConfig config) {
        this.strategy = strategy;
        this.config = config;
        this.executedTrades = new ArrayList<>();
        this.equityCurve = new ArrayList<>();
        this.portfolioState = new PortfolioState(config.getInitialBalance());
    }

    /**
     * Run backtest on historical data
     */
    public BacktestResult run(List<CandleData> historicalData) {
        long startTime = System.currentTimeMillis();
        this.historicalData = historicalData;
        this.executedTrades.clear();
        this.equityCurve.clear();
        this.portfolioState.reset(config.getInitialBalance());

        if (historicalData == null || historicalData.isEmpty()) {
            throw new IllegalArgumentException("Historical data cannot be empty");
        }

        // Initialize strategy
        strategy.initialize(historicalData);

        // Process data and get signals
        List<BacktestStrategy.SignalEvent> signals = strategy.processData();

        // Execute trades based on signals
        for (BacktestStrategy.SignalEvent signal : signals) {
            int idx = signal.candleIndex();
            if (idx < 0 || idx >= historicalData.size()) continue;

            CandleData candle = historicalData.get(idx);
            double price = candle.closePrice();

            switch (signal.type()) {
                case BUY:
                    if (portfolioState.getCash() > 0 && !portfolioState.hasPosition()) {
                        executeBuy(idx, price, signal);
                    }
                    break;
                case SELL:
                    if (portfolioState.hasPosition()) {
                        executeSell(idx, price, signal);
                    }
                    break;
                default:
                    break;
            }

            // Record equity at each candle
            equityCurve.add(portfolioState.getTotalEquity(price));
        }

        // Close any open position at the end
        if (portfolioState.hasPosition() && !historicalData.isEmpty()) {
            int lastIdx = historicalData.size() - 1;
            CandleData lastCandle = historicalData.get(lastIdx);
            executeSell(lastIdx, lastCandle.closePrice(), null);
        }

        long duration = System.currentTimeMillis() - startTime;
        return calculateResults(duration);
    }

    private void executeBuy(int candleIndex, double price, BacktestStrategy.SignalEvent signal) {
        double commission = price * portfolioState.getCash() * config.getCommissionPercent() / 100.0;
        double investAmount = portfolioState.getCash() - commission;
        double quantity = investAmount / price;

        portfolioState.enterPosition(quantity, price, commission);

        BacktestResult.TradeRecord trade = new BacktestResult.TradeRecord(
                candleIndex, price, quantity
        );
        trade.setEntrySignal(signal != null ? signal.reason() : "BUY");
        trade.setFee(commission);
        executedTrades.add(trade);
    }

    private void executeSell(int candleIndex, double price, BacktestStrategy.SignalEvent signal) {

        BacktestResult.TradeRecord trade = executedTrades.stream()
                .filter(t -> t.getExitTime() == 0)
                .findFirst()
                .orElse(null);

        if (trade != null) {
            double exitCommission = price * portfolioState.getQuantity() * config.getCommissionPercent() / 100.0;
            double proceeds = portfolioState.getQuantity() * price - exitCommission;
            double profit = proceeds - (trade.getEntryPrice() * portfolioState.getQuantity());

            trade.setExitTime(candleIndex);
            trade.setExitPrice(price);
            trade.setExitSignal(signal != null ? signal.reason() : "SELL");
            trade.setProfit(profit);
            trade.setProfitPercent((profit / (trade.getEntryPrice() * portfolioState.getQuantity())) * 100.0);
            trade.setFee(trade.getFee() + exitCommission);

            portfolioState.exitPosition(proceeds);
        }

    }

    private @NonNull BacktestResult calculateResults(long duration) {
        BacktestResult result = new BacktestResult();
        result.setStrategyName(strategy.getStrategyName());
        result.setInitialBalance(config.getInitialBalance());
        result.setFinalBalance(portfolioState.getCash());
        result.setBackTestDuration(duration);
        result.setTrades(new ArrayList<>(executedTrades));

        // Calculate metrics
        double totalReturn = portfolioState.getCash() - config.getInitialBalance();
        result.setTotalReturn(totalReturn);
        result.setReturnPercent((totalReturn / config.getInitialBalance()) * 100.0);

        // Trade statistics
        int closedTrades = (int) executedTrades.stream()
                .filter(t -> t.getExitTime() != 0)
                .count();
        result.setTotalTrades(closedTrades);

        int winningTrades = (int) executedTrades.stream()
                .filter(t -> t.getExitTime() != 0 && t.getProfit() > 0)
                .count();
        result.setWinningTrades(winningTrades);

        int losingTrades = closedTrades - winningTrades;
        result.setLosingTrades(losingTrades);

        if (closedTrades > 0) {
            result.setWinRate((double) winningTrades / closedTrades);
        }

        double totalProfit = executedTrades.stream()
                .filter(t -> t.getProfit() > 0)
                .mapToDouble(BacktestResult.TradeRecord::getProfit)
                .sum();
        result.setTotalProfit(totalProfit);

        double totalLoss = Math.abs(executedTrades.stream()
                .filter(t -> t.getProfit() < 0)
                .mapToDouble(BacktestResult.TradeRecord::getProfit)
                .sum());
        result.setTotalLoss(totalLoss);

        if (winningTrades > 0) {
            result.setAverageWin(totalProfit / winningTrades);
        }
        if (losingTrades > 0) {
            result.setAverageLoss(totalLoss / losingTrades);
        }

        if (totalLoss > 0) {
            result.setProfitFactor(totalProfit / totalLoss);
        }

        // Risk metrics
        result.setMaxDrawdown(calculateMaxDrawdown());
        result.setSharpeRatio(calculateSharpeRatio());
        result.setExpectedValue(calculateExpectedValue());

        return result;
    }

    private double calculateMaxDrawdown() {
        if (equityCurve.isEmpty()) return 0.0;

        double maxDD = 0.0;
        double peak = equityCurve.get(0);

        for (double equity : equityCurve) {
            if (equity > peak) {
                peak = equity;
            }
            double dd = (peak - equity) / peak;
            maxDD = Math.max(maxDD, dd);
        }

        return maxDD * 100.0;
    }

    private double calculateSharpeRatio() {
        if (equityCurve.size() < 2) return 0.0;

        double[] returns = new double[equityCurve.size() - 1];
        for (int i = 1; i < equityCurve.size(); i++) {
            returns[i - 1] = (equityCurve.get(i) - equityCurve.get(i - 1)) / equityCurve.get(i - 1);
        }

        double meanReturn = Arrays.stream(returns).average().orElse(0.0);
        double variance = Arrays.stream(returns)
                .map(r -> Math.pow(r - meanReturn, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return stdDev > 0 ? meanReturn / stdDev : 0.0;
    }

    private double calculateExpectedValue() {
        if (executedTrades.isEmpty()) return 0.0;

        double sumProfit = executedTrades.stream()
                .filter(t -> t.getExitTime() != 0)
                .mapToDouble(BacktestResult.TradeRecord::getProfit)
                .sum();

        int closedTrades = (int) executedTrades.stream()
                .filter(t -> t.getExitTime() != 0)
                .count();

        return closedTrades > 0 ? sumProfit / closedTrades : 0.0;
    }

    /**
     * Internal portfolio state management
     */
    @Getter
    @Setter
    @Slf4j
    private static class PortfolioState {
        private double cash;

        private double quantity;
        private double entryPrice;
        private double initialBalance;

        public PortfolioState(double initialBalance) {
            this.initialBalance = initialBalance;
            this.cash = initialBalance;
            this.quantity = 0;
            this.entryPrice = 0;
        }

        public void reset(double balance) {
            this.initialBalance = balance;
            this.cash = balance;
            this.quantity = 0;
            this.entryPrice = 0;
        }

        public void enterPosition(double qty, double price, double commission) {
            this.quantity = qty;
            this.entryPrice = price;
            this.cash -= (qty * price + commission);
        }

        public void exitPosition(double proceeds) {
            this.cash += proceeds;
            this.quantity = 0;
            this.entryPrice = 0;
        }

        public boolean hasPosition() {
            return quantity > 0;
        }

        public double getTotalEquity(double currentPrice) {
            return cash + (quantity * currentPrice);
        }
    }

    public List<BacktestResult.TradeRecord> getExecutedTrades() {
        return new ArrayList<>(executedTrades);
    }

    public List<Double> getEquityCurve() {
        return new ArrayList<>(equityCurve);
    }
}
