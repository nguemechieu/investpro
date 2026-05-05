package org.investpro.risk;

/**
 * Calculates expected value of a trade based on probabilities and reward/risk.
 * Critical for filtering trades with positive mathematical expectation.
 */
public class ExpectedValueCalculator {

    /**
     * Calculate expected value of a trade.
     * EV = (Win% × Avg Win) - (Loss% × Avg Loss)
     * 
     * @param winRate Win rate as percentage (0.0 to 100.0)
     * @param averageWin Average profit per winning trade (currency)
     * @param averageLoss Average loss per losing trade (currency, positive number)
     * @return Expected value in currency
     */
    public static double calculateExpectedValue(
            double winRate,
            double averageWin,
            double averageLoss) {

        double winPercent = winRate / 100.0;
        double lossPercent = 1.0 - winPercent;

        return (winPercent * averageWin) - (lossPercent * averageLoss);
    }

    /**
     * Calculate expected value for context using standard reward/risk.
     * Assumes average win = stop risk distance, and standard 2:1 reward/risk.
     * 
     * @param context Trade risk context with win rate and reward/risk ratio
     * @return Expected value in currency
     */
    public static double calculateExpectedValueFromContext(TradeRiskContext context) {
        if (context.getEntryPrice() <= 0 || context.getStopLossPrice() <= 0) {
            return 0;
        }

        double tradeRisk = context.calculateTradeRisk();
        double avgWin = tradeRisk * context.getExpectedRewardRiskRatio();
        double avgLoss = tradeRisk;

        return calculateExpectedValue(context.getExpectedWinRate() * 100.0, avgWin, avgLoss);
    }

    /**
     * Calculate required win rate for a given average win/loss setup to break even.
     * Breakeven WR = Loss / (Loss + Win)
     * 
     * @param averageWin Average win per trade
     * @param averageLoss Average loss per trade (positive number)
     * @return Required win rate (0.0 to 1.0)
     */
    public static double calculateBreakevenWinRate(double averageWin, double averageLoss) {
        double totalPerTrade = averageWin + averageLoss;
        if (totalPerTrade <= 0) {
            return 0.5;  // Default to 50%
        }
        return averageLoss / totalPerTrade;
    }

    /**
     * Check if a trade setup has positive expected value.
     * 
     * @param winRate Win rate (0.0 to 1.0)
     * @param averageWin Average win per trade
     * @param averageLoss Average loss per trade
     * @return true if EV > 0
     */
    public static boolean hasPositiveExpectedValue(double winRate, double averageWin, double averageLoss) {
        double ev = calculateExpectedValue(winRate * 100.0, averageWin, averageLoss);
        return ev > 0;
    }

    /**
     * Calculate profit factor (gross profit / gross loss).
     * Used to assess trade system quality. Good systems have PF > 1.5.
     * 
     * @param totalWins Sum of all winning trades
     * @param totalLosses Sum of all losing trades (positive number)
     * @return Profit factor (PF > 1.0 is profitable)
     */
    public static double calculateProfitFactor(double totalWins, double totalLosses) {
        if (totalLosses <= 0) {
            return totalWins > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return totalWins / totalLosses;
    }

    /**
     * Calculate required win rate given a target profit factor.
     * Used for planning trading systems.
     * 
     * @param targetProfitFactor Target PF (e.g., 2.0)
     * @param averageWin Average win size
     * @param averageLoss Average loss size
     * @return Required win rate (0.0 to 1.0) to achieve target PF
     */
    public static double calculateWinRateForTargetProfitFactor(
            double targetProfitFactor,
            double averageWin,
            double averageLoss) {

        if (averageWin <= 0 || averageLoss <= 0) {
            return 0.5;
        }

        // From: PF = (W * P) / (L * (1-P)), solve for P
        // P = L / (L + W/PF)
        double denominator = averageLoss + (averageWin / targetProfitFactor);
        if (denominator <= 0) {
            return 0.5;
        }

        return averageLoss / denominator;
    }
}
