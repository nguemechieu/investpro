package org.investpro.ui.charts;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/**
 * Professional color scheme for candlestick charts
 * Optimized for dark theme with good contrast and visual clarity
 * 
 * @author NOEL NGUEMECHIEU
 */
public final class ChartColors {
    // Bullish Candle Colors (Green - Price went UP)
    public static final Paint BULL_CANDLE_FILL_COLOR = Color.rgb(8, 153, 129, 0.96);
    public static final Paint BULL_CANDLE_BORDER_COLOR = Color.rgb(8, 153, 129);

    // Bearish Candle Colors (Red - Price went DOWN)
    public static final Paint BEAR_CANDLE_FILL_COLOR = Color.rgb(242, 54, 69, 0.96);
    public static final Paint BEAR_CANDLE_BORDER_COLOR = Color.rgb(242, 54, 69);

    // Placeholder Candle (Currently being formed)
    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.rgb(59, 130, 246, 0.6); // Blue
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.rgb(59, 130, 246); // Brighter blue

    // Axis labels
    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(120, 134, 156);

    // Tooltip text
    public static final Paint TOOLTIP_TEXT_COLOR = Color.rgb(241, 245, 249); // Light text

    private ChartColors() {
        throw new AssertionError();
    }
}
