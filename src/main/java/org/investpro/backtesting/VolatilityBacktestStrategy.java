package org.investpro.backtesting;

import org.investpro.data.CandleData;
import java.util.*;

/**
 * Volatility-based trading strategy using ATR (Average True Range)
 */
public class VolatilityBacktestStrategy extends BacktestStrategy {
    private int atrPeriod;
    private double atrMultiplierBuy;
    private double atrMultiplierSell;

    public VolatilityBacktestStrategy(BacktestConfig config) {
        super("Volatility Strategy", config);
        this.atrPeriod = 14;
        this.atrMultiplierBuy = 1.5;
        this.atrMultiplierSell = 2.0;

        setParameter("atrPeriod", atrPeriod);
        setParameter("atrMultiplierBuy", atrMultiplierBuy);
        setParameter("atrMultiplierSell", atrMultiplierSell);
    }

    @Override
    public List<SignalEvent> processData() {
        signals.clear();

        if (candleHistory.size() < atrPeriod + 1) {
            return signals;
        }

        for (int i = atrPeriod; i < candleHistory.size(); i++) {
            double atr = calculateATR(i, atrPeriod);
            double price = candleHistory.get(i).closePrice();
            double volatility = atr / price;

            // High volatility signal - buy on mean reversion
            if (volatility > 0.02) { // 2% volatility threshold
                CandleData candle = candleHistory.get(i);
                
                // Check if price is at lower band
                double support = price - (atr * atrMultiplierBuy);
                if (candle.lowPrice() <= support) {
                    signals.add(new SignalEvent(i, SignalEvent.Type.BUY,
                            String.format("High volatility (%.2f%%) at support", volatility * 100),
                            0.6));
                }

                // Check if price is at upper band
                double resistance = price + (atr * atrMultiplierSell);
                if (candle.highPrice() >= resistance) {
                    signals.add(new SignalEvent(i, SignalEvent.Type.SELL,
                            String.format("High volatility (%.2f%%) at resistance", volatility * 100),
                            0.6));
                }
            }
        }

        return signals;
    }

    @Override
    public void onCandleUpdate(CandleData candle, int candleIndex) {
        if (candleIndex < atrPeriod) return;

        double atr = calculateATR(candleIndex, atrPeriod);
        double price = candle.closePrice();
        double volatility = atr / price;

        if (volatility > 0.02) {
            double support = price - (atr * atrMultiplierBuy);
            double resistance = price + (atr * atrMultiplierSell);

            if (candle.lowPrice() <= support) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.BUY,
                        String.format("High volatility (%.2f%%) at support", volatility * 100)));
            } else if (candle.highPrice() >= resistance) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.SELL,
                        String.format("High volatility (%.2f%%) at resistance", volatility * 100)));
            }
        }
    }

    /**
     * Calculate Average True Range
     */
    private double calculateATR(int endIndex, int period) {
        double sumTR = 0;
        for (int i = Math.max(1, endIndex - period + 1); i <= endIndex; i++) {
            double tr = calculateTrueRange(i);
            sumTR += tr;
        }
        return sumTR / period;
    }

    /**
     * Calculate True Range for a single bar
     */
    private double calculateTrueRange(int index) {
        CandleData current = candleHistory.get(index);
        CandleData previous = index > 0 ? candleHistory.get(index - 1) : null;

        double highLow = current.highPrice() - current.lowPrice();
        
        if (previous == null) {
            return highLow;
        }

        double highClose = Math.abs(current.highPrice() - previous.closePrice());
        double lowClose = Math.abs(current.lowPrice() - previous.closePrice());

        return Math.max(highLow, Math.max(highClose, lowClose));
    }

    public void setATRPeriod(int period) {
        this.atrPeriod = period;
        setParameter("atrPeriod", period);
    }

    public void setMultipliers(double buyMultiplier, double sellMultiplier) {
        this.atrMultiplierBuy = buyMultiplier;
        this.atrMultiplierSell = sellMultiplier;
        setParameter("atrMultiplierBuy", buyMultiplier);
        setParameter("atrMultiplierSell", sellMultiplier);
    }
}
