package org.investpro.investpro.oanda;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import javafx.util.Pair;
import org.investpro.investpro.*;
import org.investpro.investpro.BinanceUs.Binance;
import org.investpro.investpro.Coinbase.Coinbase;
import org.investpro.investpro.Coinbase.CoinbaseCandleStickChart;
import org.investpro.investpro.Coinbase.CoinbasePro;
import org.investpro.investpro.Currency;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.investpro.CandleStickChartUtils.*;
import static org.investpro.investpro.ChartColors.*;

public class OandaException extends Throwable {
    @Serial
    private static final long serialVersionUID = 1L;

    public OandaException() {
        super();
    }

    public OandaException(String message) {
        super(message);
    }

    public OandaException(String message, Throwable cause) {
        super(message, cause);
    }


    /**
     * A resizable chart that allows for analyzing the trading activity of a commodity over time. The chart is made up of
     * fixed-duration bars that range vertically from the price of the commodity at the beginning of the duration
     * (the open price) to the price at the end of the duration (the close price). Superimposed on these bars is a
     * line that ranges from the lowest price the commodity reached during the duration, to the highest price
     * reached. Hence the name candle-stick chart (the line being the wick of a candle...although in this case it's
     * a double-ended wick!). The candles are color-coded to represent the type of activity that occurred during the
     * duration of the candle, if the price of the commodity increased during the duration, the candle is colored
     * green and represents a "bullish" trading period. Conversely, if the price decreased then the candle is colored
     * red which represents a "bearish" period. To display a {@code CandleStickChart} in a scene one must use
     * a {@link OandaClient.CandleStickChartContainer}. To enforce this usage, the constructors for this class are package-private.
     * <p>
     * JavaFX offers various charts in it's javafx.scene.chart package, but does not offer a candle-stick
     * chart out-of-the-box. It does however offer an XYChart which could be used as a starting-point for a candle-stick
     * chart. This is the <a href="http://hg.openjdk.java.net/openjfx/9-dev/rt/file/tip/apps/samples/Ensemble8/
     * src/samples/java/ensemble/samples/charts/candlestick/CandleStickChart.java">approach</a>
     * taken by the JavaFX developers for the <a href="http://www.oracle.com/technetwork/java/javase/overview/
     * javafx-samples-2158687.html">Ensemble demos</a> and also by <a href="https://github.com/rterp/StockChartsFX"
     * >StockChartsFX</a>. Indeed, this is the approach that we went with originally but decided to switch to the
     * present {@link Canvas}-based implementation that is contained herein.
     * <p>
     * The main reason for choosing a Canvas-based implementation is that by using a Canvas we obtain pixel-perfect
     * drawing capabilities and precise control over what should be displayed in response to panning and zooming. With the
     * old approach the drawing of the volume bars and the panning and zooming capabilities were all extremely ad-hoc and
     * buggy. For example the panning was simulated by using a ScrollPane which functioned very poorly when paging in
     * new candles (as the bounds of the pane were changing while scrolling was happening so "jumps" would occur).
     * Also in order to implement panning and zooming we needed access to all the chart's internal data (and then some)
     * and so the encapsulation of the chart's data by the Chart class was being completely bypassed.
     *
     * @author NOEL M NGUEMECHIEU
     */

}
