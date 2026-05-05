package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for chart indicators - handles adding, removing, and calculating indicators.
 */
@Getter
@Setter
public class Indicators {
    
    private List<ChartIndicator> indicators;
    private Map<String, ChartIndicator> indicatorsByName;
    
    public Indicators() {
        this.indicators = new ArrayList<>();
        this.indicatorsByName = new HashMap<>();
    }
    
    /**
     * Add an indicator to the manager
     */
    public void addIndicator(ChartIndicator indicator) {
        if (indicator != null && !indicatorsByName.containsKey(indicator.getName())) {
            indicators.add(indicator);
            indicatorsByName.put(indicator.getName(), indicator);
        }
    }
    
    /**
     * Remove an indicator by name
     */
    public void removeIndicator(String indicatorName) {
        if (indicatorsByName.containsKey(indicatorName)) {
            ChartIndicator indicator = indicatorsByName.remove(indicatorName);
            indicators.remove(indicator);
        }
    }
    
    /**
     * Add indicator by type string (used from menu)
     */
    public void addIndicatorByType(String type) {
        ChartIndicator indicator = createIndicatorByType(type);
        if (indicator != null) {
            addIndicator(indicator);
        }
    }
    
    /**
     * Get indicator by name
     */
    public ChartIndicator getIndicator(String name) {
        return indicatorsByName.get(name);
    }
    
    /**
     * Get all active indicators
     */
    public List<ChartIndicator> getAllIndicators() {
        return new ArrayList<>(indicators);
    }
    
    /**
     * Check if indicator exists
     */
    public boolean hasIndicator(String name) {
        return indicatorsByName.containsKey(name);
    }
    
    /**
     * Clear all indicators
     */
    public void clearAll() {
        indicators.clear();
        indicatorsByName.clear();
    }
    
    /**
     * Calculate all indicators with given candle data
     */
    public void calculateAll(List<CandleData> candles) {
        for (ChartIndicator indicator : indicators) {
            indicator.calculate(candles);
        }
    }
    
    /**
     * Factory method to create indicator by type
     */
    public static ChartIndicator createIndicatorByType(String type) {
        return switch (type) {
            case "SMA" -> new SimpleMovingAverageIndicator(20);
            case "EMA" -> new ExponentialMovingAverageIndicator(12);
            case "RSI" -> new RSIIndicator(14);
            case "MACD" -> new MACDIndicator(12, 26, 9);
            case "BB" -> new BollingerBandsIndicator(20, 2.0);
            case "STOCH" -> new StochasticIndicator(14, 3, 3);
            case "ATR" -> new ATRIndicator(14);
            case "VWAP" -> new VWAPIndicator();
            case "ICHIMOKU" -> new IchimokuIndicator(9, 26, 52);
            case "SAR" -> new ParabolicSARIndicator(0.02, 0.20);
            case "FIBO" -> new FibonacciRetracementIndicator(20);
            case "ADX" -> new ADXIndicator(14);
            case "CCI" -> new CCIIndicator(20);
            case "ZIGZAG" -> new ZigzagIndicator(5.0);
            case "FRACTAL" -> new FractalIndicator(2);
            default -> null;
        };
    }
    
    /**
     * Get display name for indicator type
     */
    public static String getDisplayName(String type) {
        return switch (type) {
            case "SMA" -> "Simple Moving Average (SMA)";
            case "EMA" -> "Exponential Moving Average (EMA)";
            case "RSI" -> "Relative Strength Index (RSI)";
            case "MACD" -> "MACD";
            case "BB" -> "Bollinger Bands";
            case "STOCH" -> "Stochastic Oscillator";
            case "ATR" -> "Average True Range (ATR)";
            case "VWAP" -> "Volume Weighted Average Price";
            case "ICHIMOKU" -> "Ichimoku Cloud";
            case "SAR" -> "Parabolic SAR";
            case "FIBO" -> "Fibonacci Retracement";
            case "ADX" -> "Average Directional Index (ADX)";
            case "CCI" -> "Commodity Channel Index (CCI)";
            case "ZIGZAG" -> "Zigzag";
            case "FRACTAL" -> "Fractal";
            default -> type;
        };
    }
}

