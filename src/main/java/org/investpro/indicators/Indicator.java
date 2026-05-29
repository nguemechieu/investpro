package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.List;
import java.util.Map;

/**
 * Interface for all technical indicators that can be displayed on charts.
 */
public interface Indicator {
    
    /**
     * Get the name of the indicator
     */
    String getName();
    
    /**
     * Calculate indicator values from candle data
     */
    void calculate(List<CandleData> candles);
    
    /**
     * Get calculated values for this indicator
     * Returns a map of line names to their values
     */
    Map<String, double[]> getValues();
    
    /**
     * Get the data points needed (e.g., 20 for SMA20)
     */
    int getPeriod();
    
    /**
     * Check if indicator has been calculated
     */
    boolean isCalculated();
    
    /**
     * Reset the indicator
     */
    void reset();
}
