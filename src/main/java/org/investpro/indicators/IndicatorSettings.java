package org.investpro.indicators;

import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration settings for each indicator type
 */
@Setter
@Getter
public class IndicatorSettings {
    private IndicatorType type;
    private int period;
    private Color color;
    private double strokeWidth;
    private boolean visible;
    
    // Additional parameters for specific indicators
    private double stdDeviation = 2.0; // For Bollinger Bands
    private int secondaryPeriod = 9;   // For MACD signal line
    
    public IndicatorSettings(IndicatorType type) {
        this.type = type;
        this.visible = true;
        this.strokeWidth = 2.0;
        
        // Set defaults per indicator type
        switch (type) {
            case SMA:
                this.period = 20;
                this.color = Color.web("#FF9500");
                break;
            case EMA:
                this.period = 12;
                this.color = Color.web("#00D9FF");
                break;
            case RSI:
                this.period = 14;
                this.color = Color.web("#FFD700");
                break;
            case MACD:
                this.period = 12;
                this.color = Color.web("#00FF00");
                break;
            case BOLLINGER:
                this.period = 20;
                this.color = Color.web("#FF1493");
                break;
            case STOCHASTIC:
                this.period = 14;
                this.color = Color.web("#00CED1");
                break;
            default:
                this.period = 20;
                this.color = Color.WHITE;
        }
    }

}
