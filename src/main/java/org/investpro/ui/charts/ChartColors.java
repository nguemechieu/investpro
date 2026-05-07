package org.investpro.ui.charts;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.extern.slf4j.Slf4j;

/**
 * Professional color scheme for candlestick charts
 * Optimized for dark theme with good contrast and visual clarity
 * 
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
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

    // Axis and Grid
    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(120, 134, 156);
    public static final Paint GRID_LINE_COLOR = Color.rgb(42, 52, 68, 0.42);
    public static final Paint AXIS_COLOR = Color.rgb(54, 65, 83);

    // Volume Bar
    public static final Paint VOLUME_BAR_COLOR = Color.rgb(59, 130, 246, 0.2); // Light blue
    public static final Paint VOLUME_BAR_BULL = Color.rgb(16, 185, 129, 0.3); // Light green
    public static final Paint VOLUME_BAR_BEAR = Color.rgb(239, 68, 68, 0.3); // Light red

    // Indicator Lines
    public static final Paint MOVING_AVERAGE_COLOR = Color.rgb(245, 158, 11); // Amber
    public static final Paint RSI_COLOR = Color.rgb(168, 85, 247); // Purple
    public static final Paint MACD_COLOR = Color.rgb(34, 197, 94); // Green

    // Selection and Hover
    public static final Paint SELECTION_COLOR = Color.rgb(59, 130, 246, 0.2); // Light blue highlight
    public static final Paint HOVER_COLOR = Color.rgb(59, 130, 246, 0.3); // Darker blue highlight

    // Text and Tooltips
    public static final Paint TOOLTIP_BACKGROUND = Color.rgb(30, 41, 59, 0.95); // Dark blue
    public static final Paint TOOLTIP_TEXT_COLOR = Color.rgb(241, 245, 249); // Light text

    private ChartColors() {
        throw new AssertionError();
    }
}
