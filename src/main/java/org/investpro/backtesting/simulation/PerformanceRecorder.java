package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores sampled equity and completed trades. The equity sampler prevents one
 * value per candle from becoming a large long-session memory sink.
 */
public final class PerformanceRecorder {
    private final List<BacktestResult.TradeRecord> trades = new ArrayList<>(256);
    private final List<Double> sampledEquityCurve = new ArrayList<>(1024);
    private final int equitySampling;

    public PerformanceRecorder(int equitySampling) {
        this.equitySampling = Math.max(1, equitySampling);
    }

    public void reset() {
        trades.clear();
        sampledEquityCurve.clear();
    }

    public void recordEquity(int candleIndex, double equity) {
        if (candleIndex % equitySampling == 0) {
            sampledEquityCurve.add(equity);
        }
    }

    public void recordFinalEquity(double equity) {
        if (sampledEquityCurve.isEmpty()
                || Double.compare(sampledEquityCurve.get(sampledEquityCurve.size() - 1), equity) != 0) {
            sampledEquityCurve.add(equity);
        }
    }

    public void recordTrade(BacktestResult.TradeRecord trade) {
        if (trade != null) {
            trades.add(trade);
        }
    }

    public List<BacktestResult.TradeRecord> tradesCopy() {
        return new ArrayList<>(trades);
    }

    public List<Double> equityCurveCopy() {
        return new ArrayList<>(sampledEquityCurve);
    }

    public int tradeCount() {
        return trades.size();
    }
}
