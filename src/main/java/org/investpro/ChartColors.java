package org.investpro;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import static javafx.scene.paint.Color.rgb;

/**
 * @author Nguemechieu Noel Martial
 */
public class ChartColors {

    //CandleSticks chart colors
    public static Paint BEAR_CANDLE_BORDER_COLOR = Color.RED;
    public static final Paint BEAR_CANDLE_FILL_COLOR = rgb(255, 28, 34, 1.00);
    public static final Paint BULL_CANDLE_BORDER_COLOR = Color.GREEN;
    public static final Paint BULL_CANDLE_FILL_COLOR = rgb(29, 255, 34, 1.00);
    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.GREEN;
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.GOLD;
    public static final Paint AXIS_TICK_LABEL_COLOR = rgb(24, 204, 197);

    private ChartColors() {
    }
}

