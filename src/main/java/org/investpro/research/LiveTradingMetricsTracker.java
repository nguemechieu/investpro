package org.investpro.research;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Trade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Tracks live trading metrics during bot execution.
 * Collects executed trades and calculates performance metrics in real-time.
 */
@Slf4j
@Getter
public class LiveTradingMetricsTracker {
    private final List<Trade> executedTrades = new CopyOnWriteArrayList<>();
    private final Map<String, List<Trade>> tradesBySymbol = Collections.synchronizedMap(new LinkedHashMap<>());

    private double initialBalance = 0.0;
    private double currentBalance = 0.0;
    private double highWaterMark = 0.0;
    private long startTime = System.currentTimeMillis();
    private boolean isRunning = false;

    /**
     * Initialize tracker with account balance.
     */
    public void startTracking(double balance) {
        initialBalance = balance;
        currentBalance = balance;
        highWaterMark = balance;
        startTime = System.currentTimeMillis();
        isRunning = true;
        log.info("Started tracking live trading metrics with balance: {}", balance);
    }

    /**
     * Record an executed trade.
     */
    public void recordTrade(@NotNull Trade trade) {
        executedTrades.add(trade);
        tradesBySymbol.computeIfAbsent(trade.getTradePair().toString('/'), k -> new CopyOnWriteArrayList<>())
                .add(trade);
        log.debug("Recorded trade: {} for {}", trade, trade.getTradePair());
    }

    /**
     * Update current balance from account state.
     */
    public void updateBalance(double balance) {
        currentBalance = balance;
        if (balance > highWaterMark) {
            highWaterMark = balance;
        }
    }

    /**
     * Get total P&L in currency.
     */
    public double getTotalProfitLoss() {
        return currentBalance - initialBalance;
    }

    /**
     * Get total return percentage.
     */
    public double getTotalReturnPercent() {
        if (initialBalance <= 0)
            return 0.0;
        return ((currentBalance - initialBalance) / initialBalance) * 100.0;
    }

    /**
     * Get maximum drawdown percentage.
     */
    public double getMaxDrawdownPercent() {
        if (highWaterMark <= 0)
            return 0.0;
        return ((highWaterMark - currentBalance) / highWaterMark) * 100.0;
    }

    /**
     * Get winning trades count.
     */
    public int getWinningTrades() {
        return (int) executedTrades.stream().filter(t -> t.getProfit() > 0).count();
    }

    /**
     * Get losing trades count.
     */
    public int getLosingTrades() {
        return (int) executedTrades.stream().filter(t -> t.getProfit() < 0).count();
    }

    /**
     * Get win rate (0-1).
     */
    public double getWinRate() {
        int total = executedTrades.size();
        if (total == 0)
            return 0.0;
        return (double) getWinningTrades() / total;
    }

    /**
     * Get profit factor (total wins / total losses).
     */
    public double getProfitFactor() {
        double wins = executedTrades.stream()
                .filter(t -> t.getProfit() > 0)
                .mapToDouble(Trade::getProfit)
                .sum();
        double losses = Math.abs(executedTrades.stream()
                .filter(t -> t.getProfit() < 0)
                .mapToDouble(Trade::getProfit)
                .sum());

        if (losses == 0)
            return wins > 0 ? Double.MAX_VALUE : 0.0;
        return wins / losses;
    }

    /**
     * Get average win.
     */
    public double getAverageWin() {
        List<Double> wins = executedTrades.stream()
                .map(Trade::getProfit)
                .filter(profit -> profit > 0)
                .toList();

        if (wins.isEmpty())
            return 0.0;
        return wins.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Get average loss.
     */
    public double getAverageLoss() {
        List<Double> losses = executedTrades.stream()
                .map(Trade::getProfit)
                .filter(profit -> profit < 0)
                .toList();

        if (losses.isEmpty())
            return 0.0;
        return losses.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Get expectancy (average profit per trade).
     */
    public double getExpectancy() {
        if (executedTrades.isEmpty())
            return 0.0;
        return executedTrades.stream()
                .mapToDouble(Trade::getProfit)
                .average()
                .orElse(0.0);
    }

    /**
     * Get largest win.
     */
    public double getLargestWin() {
        return executedTrades.stream()
                .filter(t -> t.getProfit() > 0)
                .mapToDouble(Trade::getProfit)
                .max()
                .orElse(0.0);
    }

    /**
     * Get largest loss.
     */
    public double getLargestLoss() {
        return executedTrades.stream()
                .filter(t -> t.getProfit() < 0)
                .mapToDouble(Trade::getProfit)
                .min()
                .orElse(0.0);
    }

    /**
     * Get total trading time in hours.
     */
    public double getTradingTimeHours() {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed / (1000.0 * 60.0 * 60.0);
    }

    /**
     * Get sharpe ratio (simplified: return / volatility).
     */
    public double getSharpeRatio() {
        List<Double> returns = executedTrades.stream()
                .map(t -> (t.getProfit() / initialBalance) * 100.0)
                .toList();

        if (returns.size() < 2)
            return 0.0;

        double avg = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - avg, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0)
            return 0.0;
        return avg / stdDev;
    }

    /**
     * Get consecutive wins.
     */
    public int getConsecutiveWins() {
        int maxConsecutive = 0;
        int current = 0;
        for (Trade trade : executedTrades) {
            if (trade.getProfit() > 0) {
                current++;
                maxConsecutive = Math.max(maxConsecutive, current);
            } else {
                current = 0;
            }
        }
        return maxConsecutive;
    }

    /**
     * Get consecutive losses.
     */
    public int getConsecutiveLosses() {
        int maxConsecutive = 0;
        int current = 0;
        for (Trade trade : executedTrades) {
            if (trade.getProfit() < 0) {
                current++;
                maxConsecutive = Math.max(maxConsecutive, current);
            } else {
                current = 0;
            }
        }
        return maxConsecutive;
    }

    /**
     * Get profit curve (cumulative profits over time).
     */
    public List<Double> getProfitCurve() {
        List<Double> curve = new ArrayList<>();
        double cumulative = 0.0;
        for (Trade trade : executedTrades) {
            cumulative += trade.getProfit();
            curve.add(cumulative);
        }
        return curve;
    }

    /**
     * Stop tracking and return final metrics.
     */
    public void stopTracking() {
        isRunning = false;
        log.info("Stopped tracking live trading metrics. Total trades: {}, Win rate: {:.1%}, Total P&L: {}",
                executedTrades.size(), getWinRate(), getTotalProfitLoss());
    }

    /**
     * Reset tracker.
     */
    public void reset() {
        executedTrades.clear();
        tradesBySymbol.clear();
        initialBalance = 0.0;
        currentBalance = 0.0;
        highWaterMark = 0.0;
        startTime = System.currentTimeMillis();
        isRunning = false;
    }

    /**
     * Convert live metrics to backtest result format for display.
     */
    public @Nullable StrategyBacktestResult toBacktestResult(@NotNull String strategyId,
            @NotNull String symbol) {
        if (executedTrades.isEmpty())
            return null;

        return StrategyBacktestResult.builder()
                .strategyId(strategyId)
                .symbol(symbol)
                .totalTrades(executedTrades.size())
                .winningTrades(getWinningTrades())
                .losingTrades(getLosingTrades())
                .winRate(getWinRate())
                .profitFactor(getProfitFactor())
                .expectancy(getExpectancy())
                .averageWin(getAverageWin())
                .averageLoss(getAverageLoss())
                .maxDrawdownPercent(getMaxDrawdownPercent())
                .totalReturnPercent(getTotalReturnPercent())
                .sharpeRatio(getSharpeRatio())
                .largestWin(getLargestWin())
                .largestLoss(getLargestLoss())
                .consecutiveWins(getConsecutiveWins())
                .consecutiveLosses(getConsecutiveLosses())
                .build();
    }
}
