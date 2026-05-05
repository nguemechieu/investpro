package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.*;

/**
 * Manages calculation and rendering of technical indicators
 */
@Getter
public class IndicatorManager {
    private final Map<IndicatorType, IndicatorSettings> indicatorSettings = new HashMap<>();
    /**
     * -- SETTER --
     *  Set the active indicator to display
     */
    @Setter
    private IndicatorType activeIndicator = IndicatorType.NONE;
    private List<CandleData> candleData = new ArrayList<>();
    
    public IndicatorManager() {
        // Initialize default settings for each indicator
        for (IndicatorType type : IndicatorType.values()) {
            if (type != IndicatorType.NONE) {
                indicatorSettings.put(type, new IndicatorSettings(type));
            }
        }
    }
    
    /**
     * Set candle data for indicator calculations
     */
    public void setCandleData(List<CandleData> data) {
        this.candleData = new ArrayList<>(data);
    }


    /**
     * Get settings for an indicator type
     */
    public IndicatorSettings getSettings(IndicatorType type) {
        return indicatorSettings.get(type);
    }
}
