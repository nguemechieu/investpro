package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestResult;

/**
 * Incremental trade and equity statistics.
 */
public final class MetricsEngine {
    private final WelfordStatistics returns = new WelfordStatistics();
    private int closedTrades;
    private int winningTrades;
    private int losingTrades;
    private double totalProfit;
    private double totalLoss;
    private double peakEquity;
    private double maxDrawdown;
    private double previousEquity;

    public void reset(double initialEquity) {
        returns.reset();
        closedTrades = 0;
        winningTrades = 0;
        losingTrades = 0;
        totalProfit = 0.0;
        totalLoss = 0.0;
        peakEquity = initialEquity;
        maxDrawdown = 0.0;
        previousEquity = initialEquity;
    }

    public void recordEquity(double equity) {
        if (equity > peakEquity) {
            peakEquity = equity;
        }
        if (peakEquity > 0.0) {
            maxDrawdown = Math.max(maxDrawdown, (peakEquity - equity) / peakEquity);
        }
        if (previousEquity > 0.0) {
            returns.add((equity - previousEquity) / previousEquity);
        }
        previousEquity = equity;
    }

    public void recordClosedTrade(BacktestResult.TradeRecord trade) {
        if (trade == null || trade.getExitTime() == 0) {
            return;
        }
        closedTrades++;
        if (trade.getProfit() > 0.0) {
            winningTrades++;
            totalProfit += trade.getProfit();
        } else {
            losingTrades++;
            totalLoss += Math.abs(trade.getProfit());
        }
    }

    public void applyTo(BacktestResult result, double initialBalance, double finalBalance) {
        result.setFinalBalance(finalBalance);
        double totalReturn = finalBalance - initialBalance;
        result.setTotalReturn(totalReturn);
        result.setReturnPercent(initialBalance > 0.0 ? totalReturn / initialBalance * 100.0 : 0.0);
        result.setTotalTrades(closedTrades);
        result.setWinningTrades(winningTrades);
        result.setLosingTrades(losingTrades);
        result.setWinRate(closedTrades > 0 ? (double) winningTrades / closedTrades : 0.0);
        result.setTotalProfit(totalProfit);
        result.setTotalLoss(totalLoss);
        result.setAverageWin(winningTrades > 0 ? totalProfit / winningTrades : 0.0);
        result.setAverageLoss(losingTrades > 0 ? totalLoss / losingTrades : 0.0);
        result.setProfitFactor(totalLoss > 0.0 ? totalProfit / totalLoss : 0.0);
        result.setExpectedValue(closedTrades > 0 ? (totalProfit - totalLoss) / closedTrades : 0.0);
        result.setMaxDrawdown(maxDrawdown * 100.0);
        double std = returns.standardDeviation();
        result.setSharpeRatio(std > 0.0 ? returns.mean() / std : 0.0);
    }
}
