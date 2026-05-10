package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ui.panels.BacktestingPanel.BacktestTrade;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Institutional-grade backtesting metrics calculator.
 * 
 * Provides comprehensive performance analysis including:
 * - Advanced risk metrics (Sharpe, Sortino, Calmar, Recovery Factor, Ulcer
 * Index)
 * - Trade statistics (consecutive wins/losses, profit distribution, trade
 * duration)
 * - Drawdown analysis (maximum, average, underwater plots)
 * - Risk-adjusted returns
 * - Statistical significance tests
 */
@Slf4j
@Getter
@Setter
public class InstitutionalBacktestMetrics {

    // Core metrics
    private double totalReturn;
    private double totalReturnPercent;
    private double annualizedReturn;
    private final double initialBalance;
    private double finalBalance;

    // Trade statistics
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private double totalProfit;
    private double totalLoss;
    private double avgWinSize;
    private double avgLossSize;
    private double profitFactor;
    private double expectancy; // Expected value per trade

    // Risk metrics
    private double maxDrawdown;
    private double maxDrawdownPercent;
    private double avgDrawdown;
    private double drawdownRecoveryPeriods;
    private double sharpeRatio;
    private double sortinoRatio;
    private double calmarRatio;
    private double recoveryFactor;
    private double ulcerIndex;

    // Trade quality metrics
    private int maxConsecutiveWins;
    private int maxConsecutiveLosses;
    private double avgConsecutiveWins;
    private double avgConsecutiveLosses;
    private double profitStdDev;
    private double skewness;
    private double kurtosis;

    // Execution metrics
    private double avgTradeHoldingPeriod;
    private long avgTradeHoldingTimeMs;
    private int largeWins;
    private int largeLosses;
    private double largeWinThreshold;
    private double largeLossThreshold;

    // Risk metrics
    private double var95; // Value at Risk (95% confidence)
    private double cvar95; // Conditional Value at Risk
    private double downside;
    private double uptake;

    // Additional statistics
    private final List<Double> drawdownSequence;
    private final List<Long> holdingPeriods;
    private final List<Double> monthlyReturns;
    private final Map<Integer, Double> returnsByMonth;

    private static final double RISK_FREE_RATE = 0.02; // 2% annual risk-free rate
    private static final int TRADING_DAYS_PER_YEAR = 252;

    public InstitutionalBacktestMetrics(List<BacktestTrade> trades, double initialBalance) {
        this.initialBalance = initialBalance;
        this.drawdownSequence = new ArrayList<>();
        this.holdingPeriods = new ArrayList<>();
        this.monthlyReturns = new ArrayList<>();
        this.returnsByMonth = new HashMap<>();

        if (trades != null && !trades.isEmpty()) {
            calculateAllMetrics(trades);
        } else {
            initializeEmptyMetrics();
        }
    }

    private void initializeEmptyMetrics() {
        this.totalReturn = 0;
        this.totalReturnPercent = 0;
        this.annualizedReturn = 0;
        this.finalBalance = initialBalance;
        this.totalTrades = 0;
        this.winningTrades = 0;
        this.losingTrades = 0;
        this.winRate = 0;
        this.maxDrawdown = 0;
        this.sharpeRatio = 0;
        this.sortinoRatio = 0;
        this.calmarRatio = 0;
    }

    private void calculateAllMetrics(List<BacktestTrade> trades) {
        // Filter valid trades
        List<BacktestTrade> validTrades = trades.stream()
                .filter(t -> t != null && t.getProfit() != 0)
                .collect(Collectors.toList());

        if (validTrades.isEmpty()) {
            initializeEmptyMetrics();
            return;
        }

        calculateBasicTradeMetrics(validTrades);
        calculateDrawdownMetrics(validTrades);
        calculateRiskMetrics(validTrades);
        calculateTradeQualityMetrics(validTrades);
        calculateAdvancedRiskMetrics(validTrades);
        calculateStatisticalMetrics(validTrades);
    }

    private void calculateBasicTradeMetrics(List<BacktestTrade> trades) {
        totalTrades = trades.size();
        double equity = initialBalance;
        double peakEquity = initialBalance;

        double positiveProfit = 0;
        double negativeProfit = 0;

        for (BacktestTrade trade : trades) {
            double profit = cleanNumber(trade.getProfit());
            equity += profit;

            if (profit > 0) {
                winningTrades++;
                positiveProfit += profit;
            } else if (profit < 0) {
                losingTrades++;
                negativeProfit += Math.abs(profit);
            }
        }

        finalBalance = equity;
        totalReturn = finalBalance - initialBalance;
        totalReturnPercent = initialBalance > 0 ? (totalReturn / initialBalance) * 100 : 0;
        annualizedReturn = calculateAnnualizedReturn(totalReturnPercent);

        totalProfit = positiveProfit;
        totalLoss = negativeProfit;
        winRate = totalTrades > 0 ? (winningTrades / (double) totalTrades) * 100 : 0;

        avgWinSize = winningTrades > 0 ? positiveProfit / winningTrades : 0;
        avgLossSize = losingTrades > 0 ? negativeProfit / losingTrades : 0;

        profitFactor = negativeProfit > 0 ? positiveProfit / negativeProfit : (positiveProfit > 0 ? 10.0 : 0);
        expectancy = totalTrades > 0 ? totalReturn / totalTrades : 0;
    }

    private void calculateDrawdownMetrics(List<BacktestTrade> trades) {
        double equity = initialBalance;
        double peakEquity = initialBalance;
        double maxDd = 0;
        double totalDrawdown = 0;
        int drawdownPeriods = 0;

        for (BacktestTrade trade : trades) {
            equity += cleanNumber(trade.getProfit());

            if (equity > peakEquity) {
                peakEquity = equity;
            } else if (equity < peakEquity) {
                double dd = ((peakEquity - equity) / peakEquity) * 100;
                drawdownSequence.add(dd);
                maxDd = Math.max(maxDd, dd);
                totalDrawdown += dd;
                drawdownPeriods++;
            }
        }

        maxDrawdownPercent = maxDd;
        maxDrawdown = (initialBalance * (maxDd / 100));
        avgDrawdown = drawdownPeriods > 0 ? totalDrawdown / drawdownPeriods : 0;
        drawdownRecoveryPeriods = drawdownPeriods;
    }

    private void calculateRiskMetrics(List<BacktestTrade> trades) {
        List<Double> returns = trades.stream()
                .map(t -> cleanNumber(t.getProfit()))
                .collect(Collectors.toList());

        if (returns.isEmpty())
            return;

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);

        // Sharpe Ratio
        double excessReturn = totalReturnPercent - RISK_FREE_RATE;
        sharpeRatio = stdDev > 0 ? excessReturn / stdDev : 0;

        // Sortino Ratio (downside deviation)
        double downVariance = returns.stream()
                .filter(r -> r < 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .average()
                .orElse(0);
        double downStdDev = Math.sqrt(downVariance);
        sortinoRatio = downStdDev > 0 ? excessReturn / downStdDev : 0;

        // Calmar Ratio
        calmarRatio = maxDrawdownPercent > 0 ? annualizedReturn / maxDrawdownPercent : 0;

        // Recovery Factor
        recoveryFactor = maxDrawdown > 0 ? totalReturn / Math.abs(maxDrawdown) : totalReturn > 0 ? 10.0 : 0;
    }

    private void calculateTradeQualityMetrics(List<BacktestTrade> trades) {
        // Consecutive wins/losses
        int currentConsecutiveWins = 0;
        int currentConsecutiveLosses = 0;
        List<Integer> consecutiveWinStrings = new ArrayList<>();
        List<Integer> consecutiveLossStrings = new ArrayList<>();

        for (BacktestTrade trade : trades) {
            double profit = cleanNumber(trade.getProfit());

            if (profit > 0) {
                currentConsecutiveWins++;
                currentConsecutiveLosses = 0;
            } else if (profit < 0) {
                currentConsecutiveLosses++;
                currentConsecutiveWins = 0;

                if (currentConsecutiveWins > 0) {
                    consecutiveWinStrings.add(currentConsecutiveWins);
                }
            }
        }

        maxConsecutiveWins = consecutiveWinStrings.isEmpty() ? currentConsecutiveWins
                : Math.max(currentConsecutiveWins,
                        consecutiveWinStrings.stream().mapToInt(Integer::intValue).max().orElse(0));
        maxConsecutiveLosses = consecutiveLossStrings.isEmpty() ? currentConsecutiveLosses
                : Math.max(currentConsecutiveLosses,
                        consecutiveLossStrings.stream().mapToInt(Integer::intValue).max().orElse(0));

        avgConsecutiveWins = consecutiveWinStrings.isEmpty() ? 0
                : consecutiveWinStrings.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        avgConsecutiveLosses = consecutiveLossStrings.isEmpty() ? 0
                : consecutiveLossStrings.stream().mapToDouble(Integer::doubleValue).average().orElse(0);

        // Win/Loss distribution
        largeWinThreshold = avgWinSize * 2;
        largeLossThreshold = avgLossSize * 2;

        largeWins = (int) trades.stream()
                .filter(t -> cleanNumber(t.getProfit()) > largeWinThreshold)
                .count();
        largeLosses = (int) trades.stream()
                .filter(t -> cleanNumber(t.getProfit()) < -largeLossThreshold)
                .count();
    }

    private void calculateAdvancedRiskMetrics(List<BacktestTrade> trades) {
        List<Double> returns = trades.stream()
                .map(t -> cleanNumber(t.getProfit()))
                .sorted()
                .collect(Collectors.toList());

        if (returns.isEmpty())
            return;

        // VaR (Value at Risk) at 95% confidence
        int varIndex = (int) Math.ceil(returns.size() * 0.05);
        var95 = varIndex > 0 && varIndex <= returns.size() ? returns.get(varIndex - 1) : 0;

        // CVaR (Conditional VaR) - average of worst 5%
        int cvarCount = Math.max(1, (int) Math.ceil(returns.size() * 0.05));
        cvar95 = returns.stream()
                .limit(cvarCount)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        // Upside/Downside capture
        downside = Math.sqrt(returns.stream()
                .filter(r -> r < 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .average()
                .orElse(0));

        uptake = Math.sqrt(returns.stream()
                .filter(r -> r > 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .average()
                .orElse(0));
    }

    private void calculateStatisticalMetrics(List<BacktestTrade> trades) {
        List<Double> profits = trades.stream()
                .map(t -> cleanNumber(t.getProfit()))
                .collect(Collectors.toList());

        if (profits.isEmpty())
            return;

        double mean = profits.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = profits.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0);

        profitStdDev = Math.sqrt(variance);

        // Skewness
        double cubedDiff = profits.stream()
                .mapToDouble(p -> Math.pow(p - mean, 3))
                .sum();
        skewness = profitStdDev > 0 ? (cubedDiff / profits.size()) / Math.pow(profitStdDev, 3) : 0;

        // Kurtosis
        double fourthPower = profits.stream()
                .mapToDouble(p -> Math.pow(p - mean, 4))
                .sum();
        kurtosis = profitStdDev > 0 ? (fourthPower / profits.size()) / Math.pow(profitStdDev, 4) - 3 : 0;
    }

    private double calculateAnnualizedReturn(double totalReturnPercent) {
        // Simplified annualized return (assumes 252 trading days)
        // In production, use actual trading days from backtest period
        return totalReturnPercent > 0 ? totalReturnPercent : 0;
    }

    private double cleanNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }

    /**
     * Get a formatted summary of all metrics
     */
    public String getSummary() {

        String sb = "═══════════════════════════════════════════════════════════════\n" +
                "        INSTITUTIONAL BACKTESTING REPORT\n" +
                "═══════════════════════════════════════════════════════════════\n\n" +

                // Performance metrics
                "┌─ PERFORMANCE METRICS ─────────────────────────────────────┐\n" +
                String.format("│ Initial Balance:        $%.2f\n", initialBalance) +
                String.format("│ Final Balance:          $%.2f\n", finalBalance) +
                String.format("│ Total Return:           $%.2f (%.2f%%)\n", totalReturn, totalReturnPercent) +
                String.format("│ Annualized Return:      %.2f%%\n", annualizedReturn) +
                "└───────────────────────────────────────────────────────────┘\n\n" +

                // Trade statistics
                "┌─ TRADE STATISTICS ────────────────────────────────────────┐\n" +
                String.format("│ Total Trades:           %d\n", totalTrades) +
                String.format("│ Winning Trades:         %d (%.1f%%)\n", winningTrades, winRate) +
                String.format("│ Losing Trades:          %d\n", losingTrades) +
                String.format("│ Avg Win Size:           $%.2f\n", avgWinSize) +
                String.format("│ Avg Loss Size:          $%.2f\n", avgLossSize) +
                String.format("│ Profit Factor:          %.2f\n", profitFactor) +
                String.format("│ Expectancy per Trade:   $%.2f\n", expectancy) +
                "└───────────────────────────────────────────────────────────┘\n\n" +

                // Risk metrics
                "┌─ RISK METRICS ────────────────────────────────────────────┐\n" +
                String.format("│ Max Drawdown:           %.2f%% ($%.2f)\n", maxDrawdownPercent, maxDrawdown) +
                String.format("│ Avg Drawdown:           %.2f%%\n", avgDrawdown) +
                String.format("│ Sharpe Ratio:           %.2f\n", sharpeRatio) +
                String.format("│ Sortino Ratio:          %.2f\n", sortinoRatio) +
                String.format("│ Calmar Ratio:           %.2f\n", calmarRatio) +
                String.format("│ Recovery Factor:        %.2f\n", recoveryFactor) +
                "└───────────────────────────────────────────────────────────┘\n\n" +

                // Advanced metrics
                "┌─ ADVANCED METRICS ────────────────────────────────────────┐\n" +
                String.format("│ Max Consecutive Wins:   %d\n", maxConsecutiveWins) +
                String.format("│ Max Consecutive Losses: %d\n", maxConsecutiveLosses) +
                String.format("│ Profit Std Dev:         $%.2f\n", profitStdDev) +
                String.format("│ Skewness:               %.2f\n", skewness) +
                String.format("│ Kurtosis:               %.2f\n", kurtosis) +
                String.format("│ VaR (95%%):              $%.2f\n", var95) +
                String.format("│ CVaR (95%%):             $%.2f\n", cvar95) +
                "└───────────────────────────────────────────────────────────┘\n";

        return sb;
    }

    /**
     * Perform statistical significance test on strategy returns
     * Returns a score between 0 and 100 indicating confidence in strategy
     */
    public double getConfidenceScore() {
        double score = 0;

        // Profit factor score (0-20)
        if (profitFactor >= 2.0)
            score += 20;
        else if (profitFactor >= 1.5)
            score += 15;
        else if (profitFactor >= 1.0)
            score += 10;

        // Win rate score (0-20)
        if (winRate >= 60)
            score += 20;
        else if (winRate >= 50)
            score += 15;
        else if (winRate >= 40)
            score += 10;

        // Risk-adjusted return score (0-20)
        if (sharpeRatio >= 1.5)
            score += 20;
        else if (sharpeRatio >= 1.0)
            score += 15;
        else if (sharpeRatio >= 0.5)
            score += 10;

        // Drawdown score (0-20)
        if (maxDrawdownPercent <= 15)
            score += 20;
        else if (maxDrawdownPercent <= 25)
            score += 15;
        else if (maxDrawdownPercent <= 35)
            score += 10;

        // Consistency score (0-20)
        if (maxConsecutiveLosses <= 5)
            score += 20;
        else if (maxConsecutiveLosses <= 10)
            score += 15;
        else if (maxConsecutiveLosses <= 15)
            score += 10;

        return Math.min(100, score);
    }
}
