package org.investpro.backtesting;

import org.investpro.data.CandleData;
import java.util.*;

/**
 * Abstract base class for trading strategies used in backtesting
 */
public abstract class BacktestStrategy {
    protected String strategyName;
    protected BacktestConfig config;
    protected List<CandleData> candleHistory;
    protected Map<String, Object> parameters;
    protected List<SignalEvent> signals;

    public BacktestStrategy(String strategyName, BacktestConfig config) {
        this.strategyName = strategyName;
        this.config = config;
        this.candleHistory = new ArrayList<>();
        this.parameters = new HashMap<>();
        this.signals = new ArrayList<>();
    }

    /**
     * Initialize the strategy with candle data
     */
    public void initialize(List<CandleData> candleData) {
        this.candleHistory = new ArrayList<>(candleData);
    }

    /**
     * Process historical data and generate trading signals
     * Should be overridden by subclasses to implement strategy logic
     */
    public abstract List<SignalEvent> processData();

    /**
     * Called when a new candle is received (for live trading)
     */
    public abstract void onCandleUpdate(CandleData candle, int candleIndex);

    /**
     * Validate if entry signal is valid
     */
    public boolean shouldEnter(int candleIndex, String signal) {
        return candleIndex >= 0 && candleIndex < candleHistory.size();
    }

    /**
     * Validate if exit signal is valid
     */
    public boolean shouldExit(int candleIndex, String signal) {
        return candleIndex >= 0 && candleIndex < candleHistory.size();
    }

    /**
     * Get strategy parameter
     */
    public Object getParameter(String key) {
        return parameters.get(key);
    }

    /**
     * Set strategy parameter
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    /**
     * Get all generated signals
     */
    public List<SignalEvent> getSignals() {
        return new ArrayList<>(signals);
    }

    /**
     * Clear all signals
     */
    public void clearSignals() {
        signals.clear();
    }

    /**
     * Add a signal
     */
    protected void addSignal(SignalEvent signal) {
        signals.add(signal);
    }

    /**
     * Get strategy name
     */
    public String getStrategyName() {
        return strategyName;
    }

    /**
     * Get historical candle data
     */
    public List<CandleData> getCandleHistory() {
        return new ArrayList<>(candleHistory);
    }

    /**
     * Get candle at specific index
     */
    public CandleData getCandle(int index) {
        if (index >= 0 && index < candleHistory.size()) {
            return candleHistory.get(index);
        }
        return null;
    }

    /**
     * Get the last N candles
     */
    public List<CandleData> getLastCandles(int count) {
        int size = candleHistory.size();
        int start = Math.max(0, size - count);
        return new ArrayList<>(candleHistory.subList(start, size));
    }

    /**
     * Trading signal event
     */
    public static class SignalEvent {
        public enum Type { BUY, SELL, HOLD }
        
        private int candleIndex;
        private Type type;
        private String reason;
        private double strength; // 0.0 to 1.0

        public SignalEvent(int candleIndex, Type type, String reason) {
            this(candleIndex, type, reason, 1.0);
        }

        public SignalEvent(int candleIndex, Type type, String reason, double strength) {
            this.candleIndex = candleIndex;
            this.type = type;
            this.reason = reason;
            this.strength = Math.min(1.0, Math.max(0.0, strength));
        }

        public int getCandleIndex() {
            return candleIndex;
        }

        public Type getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public double getStrength() {
            return strength;
        }

        @Override
        public String toString() {
            return String.format("Signal[idx=%d, type=%s, reason='%s', strength=%.2f]",
                    candleIndex, type, reason, strength);
        }
    }
}
