package org.investpro;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for managing chart colors, including bear/bull candlestick colors and placeholder colors.
 * Provides options for dark and light themes and allows flexibility with opacity.
 * Author: Noel Nguemechieu
 */
public final class ChartColors {

    // Color definitions for the Dark theme
    public static final class DarkTheme {
        public static final Paint BEAR_CANDLE_BORDER_COLOR = createColorWithOpacity(204, 20, 20, 0.7);
        public static final Paint BEAR_CANDLE_FILL_COLOR = createColorWithOpacity(153, 15, 1, 0.7);
        public static final Paint BULL_CANDLE_BORDER_COLOR = createColorWithOpacity(64, 175, 59, 0.7);
        public static final Paint BULL_CANDLE_FILL_COLOR = createColorWithOpacity(50, 137, 46, 0.7);
        public static final Paint PLACE_HOLDER_FILL_COLOR = createColorWithOpacity(89, 189, 189, 0.7);
        public static final Paint PLACE_HOLDER_BORDER_COLOR = createColorWithOpacity(20, 204, 204, 0.7);
        public static final Paint AXIS_TICK_LABEL_COLOR = createColorWithOpacity(204, 204, 197, 1.0);
    }

    // Color definitions for the Light theme (can be extended further)
    public static final class LightTheme {
        public static final Paint BEAR_CANDLE_BORDER_COLOR = createColorWithOpacity(204, 60, 60, 0.8);
        public static final Paint BEAR_CANDLE_FILL_COLOR = createColorWithOpacity(255, 80, 80, 0.8);
        public static final Paint BULL_CANDLE_BORDER_COLOR = createColorWithOpacity(60, 175, 60, 0.8);
        public static final Paint BULL_CANDLE_FILL_COLOR = createColorWithOpacity(70, 190, 80, 0.8);
        public static final Paint PLACE_HOLDER_FILL_COLOR = createColorWithOpacity(189, 189, 189, 0.5);
        public static final Paint PLACE_HOLDER_BORDER_COLOR = createColorWithOpacity(150, 150, 150, 0.5);
        public static final Paint AXIS_TICK_LABEL_COLOR = createColorWithOpacity(50, 50, 50, 1.0);
    }

    // Method to create color with dynamic opacity
    @Contract("_, _, _, _ -> new")
    private static @NotNull Paint createColorWithOpacity(int red, int green, int blue, double opacity) {
        return Color.rgb(red, green, blue, opacity);
    }

    // Prevent instantiation of utility class
    private ChartColors() {
        throw new AssertionError("Cannot instantiate ChartColors class");
    }
}
