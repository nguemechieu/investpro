package org.investpro.backtesting;

import org.investpro.data.CandleData;
import java.util.*;

/**
 * Simple Moving Average crossover trading strategy
 */
public class SimpleMABacktestStrategy extends BacktestStrategy {
    private int shortPeriod;
    private int longPeriod;

    public SimpleMABacktestStrategy(BacktestConfig config) {
        super("Simple MA Crossover", config);
        this.shortPeriod = 10;
        this.longPeriod = 20;
        
        setParameter("shortPeriod", shortPeriod);
        setParameter("longPeriod", longPeriod);
    }

    @Override
    public List<SignalEvent> processData() {
        signals.clear();

        if (candleHistory.size() < longPeriod) {
            return signals;
        }

        // Calculate moving averages
        double prevShortMA = 0;
        double prevLongMA = 0;

        for (int i = longPeriod; i < candleHistory.size(); i++) {
            double shortMA = calculateMA(i, shortPeriod);
            double longMA = calculateMA(i, longPeriod);

            if (i == longPeriod) {
                prevShortMA = shortMA;
                prevLongMA = longMA;
                continue;
            }

            // Buy signal: short MA crosses above long MA
            if (prevShortMA <= prevLongMA && shortMA > longMA) {
                signals.add(new SignalEvent(i, SignalEvent.Type.BUY,
                        String.format("SMA%d(%.2f) > SMA%d(%.2f)", shortPeriod, shortMA, longPeriod, longMA),
                        0.7));
            }

            // Sell signal: short MA crosses below long MA
            if (prevShortMA >= prevLongMA && shortMA < longMA) {
                signals.add(new SignalEvent(i, SignalEvent.Type.SELL,
                        String.format("SMA%d(%.2f) < SMA%d(%.2f)", shortPeriod, shortMA, longPeriod, longMA),
                        0.7));
            }

            prevShortMA = shortMA;
            prevLongMA = longMA;
        }

        return signals;
    }

    @Override
    public void onCandleUpdate(CandleData candle, int candleIndex) {
        if (candleIndex < longPeriod) return;

        double shortMA = calculateMA(candleIndex, shortPeriod);
        double longMA = calculateMA(candleIndex, longPeriod);

        if (candleIndex > 1) {
            double prevShortMA = calculateMA(candleIndex - 1, shortPeriod);
            double prevLongMA = calculateMA(candleIndex - 1, longPeriod);

            if (prevShortMA <= prevLongMA && shortMA > longMA) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.BUY,
                        String.format("SMA%d(%.2f) > SMA%d(%.2f)", shortPeriod, shortMA, longPeriod, longMA)));
            } else if (prevShortMA >= prevLongMA && shortMA < longMA) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.SELL,
                        String.format("SMA%d(%.2f) < SMA%d(%.2f)", shortPeriod, shortMA, longPeriod, longMA)));
            }
        }
    }

    private double calculateMA(int endIndex, int period) {
        int start = Math.max(0, endIndex - period + 1);
        double sum = 0;
        for (int i = start; i <= endIndex; i++) {
            sum += candleHistory.get(i).closePrice();
        }
        return sum / (endIndex - start + 1);
    }

    public void setPeriods(int shortPeriod, int longPeriod) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        setParameter("shortPeriod", shortPeriod);
        setParameter("longPeriod", longPeriod);
    }
}
