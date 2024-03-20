package org.investpro;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/**
 * @author NOEL NGUEMECHIEU
 */
public final class ChartColors {
    public static final Paint BEAR_CANDLE_BORDER_COLOR = Color.rgb(204, 20, 20, 0.7);
    public static final Paint BEAR_CANDLE_FILL_COLOR = Color.rgb(113, 23, 31);
    public static final Paint BULL_CANDLE_BORDER_COLOR = Color.rgb(13, 223, 1);
    public static final Paint BULL_CANDLE_FILL_COLOR = Color.rgb(13, 223, 1);
    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.rgb(19, 189, 189, 0.7);
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.rgb(204, 204, 204, 0.7);
    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(20, 204, 197);

    private ChartColors() {
        throw new AssertionError();
    }
}
