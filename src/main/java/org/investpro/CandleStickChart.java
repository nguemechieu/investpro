package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.CandleStickChartUtils.*;


/**
 * A resizable chart that allows for analyzing the trading activity of a commodity over time. The chart is made up of
 * fixed-duration bars that range vertically from the price of the commodity at the beginning of the duration
 * (the open price) to the price at the end of the duration (the close price). Superimposed on these bars is a
 * line that ranges from the lowest price the commodity reached during the duration, to the highest price
 * reached. Hence, the name candle-stick chart (the line being the wick of a candle...although in this case it's
 * a double-ended wick!). The candles are color-coded to represent the type of activity that occurred during the
 * duration of the candle, if the price of the commodity increased during the duration, the candle is colored
 * green and represents a "bullish" trading period. Conversely, if the price decreased then the candle is colored
 * red which represents a "bearish" period. To display a {@code CandleStickChart} in a scene one must use
 * a {@link ChartContainer}. To enforce this usage, the constructors for this class are package-private.
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
 * old approach, the drawing of the volume bars and the panning and zooming capabilities were all extremely ad-hoc and
 * buggy. For example, the panning was simulated by using a ScrollPane which functioned very poorly when paging in
 * new candles (as the bounds of the pane were changing while scrolling was happening, so "jumps" would occur).
 * Also, to implement panning and zooming, we needed access to all the chart's internal data (and then some),
 * and so the encapsulation of the chart's data by the Chart class was being completely bypassed.
 *
 * @author NOEL NGUEMECHIEU
 */
public class CandleStickChart extends Region {

    public static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);

    private final CandleDataPager candleDataPager;
    private final CandleStickChartOptions chartOptions;
    private static final DecimalFormat MARKER_FORMAT = new DecimalFormat("#.000000000");
    public static final Paint BEAR_CANDLE_BORDER_COLOR = Color.rgb(204, 20, 20, 0.7);
    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final Consumer<List<CandleData>> candlePageConsumer;
    private final ScheduledExecutorService updateInProgressCandleExecutor;
    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    public static final Paint BEAR_CANDLE_FILL_COLOR = Color.rgb(193, 15, 1, 0.7);
    private final StableTicksAxis xAxis;
    private final StableTicksAxis yAxis;
    private final StableTicksAxis extraAxis;
    private final ProgressIndicator progressIndicator;
    private final Line extraAxisExtension;
    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private final EventHandler<ScrollEvent> scrollHandler;
    private final EventHandler<KeyEvent> keyHandler;
    private final Font canvasNumberFont;
    private final int secondsPerCandle;
    public static final Paint BULL_CANDLE_BORDER_COLOR = Color.rgb(6, 175, 59, 0.7);
    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private int candleWidth = 10;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;
    private double chartWidth = 900;
    private double chartHeight = 700;
    private int inProgressCandleLastDraw = -1;
    private volatile ZoomLevel currZoomLevel;
    private volatile boolean paging;
    public static final Paint BULL_CANDLE_FILL_COLOR = Color.rgb(20, 198, 65, 0.7);

    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.rgb(189, 189, 189, 0.7);
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.rgb(204, 204, 204, 0.7);

    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(234, 154, 17);
    /**
     * Maps an open time (as a Unix timestamp) to the computed candle data (high price, low price, etc.) for a trading
     * period beginning with that opening time. Thus, the key "1,601,798,498" would be mapped to the candle data for trades
     * from the period of 1,601,798,498 to 1,601,798,498 + secondsPerCandle.
     */
    private final NavigableMap<Integer, CandleData> data;
    TradeHistory stat;
    TradingAI tradingAI;
    private InProgressCandle inProgressCandle = new InProgressCandle();
    List<Trade> currentCandleTrades;

    CandleStickChart(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier,
                     boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                     ObservableNumberValue containerHeight) throws IOException {
        logger.debug(String.valueOf(this));
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(candleDataSupplier);
        this.tradePair = tradePair;
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("CandleStickChart must be constructed on the JavaFX Application Thread but was called from %s".formatted(Thread.currentThread()));
        }
        this.exchange = exchange;


        stat = new TradeHistory();
        stat.getRecentTrades(1000);

        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;
        zoomLevelMap = new ConcurrentHashMap<>();
        candleDataPager = new CandleDataPager(this, candleDataSupplier);
        data = Collections.synchronizedNavigableMap(new TreeMap<>(Integer::compare));
        chartOptions = new CandleStickChartOptions();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 15);

        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis = new StableTicksAxis(
                0,
                secondsPerCandle
        );
        yAxis = new StableTicksAxis(0, 1000000);
        yAxis.setSide(Side.RIGHT);
        extraAxis = new StableTicksAxis(0, 1000000);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        xAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(false);
        xAxis.setForceZeroInRange(false);
        extraAxis.setAutoRanging(false);
        xAxis.setSide(Side.BOTTOM);
        extraAxis.setSide(Side.LEFT);
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        xAxis.setForceZeroInRange(true);
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        yAxis.setForceZeroInRange(true);

        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));
        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 14);
        yAxis.setTickLabelFont(axisFont);
        xAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);
        Text loadingText = new Text("");
        loadingText.setFill(Color.BLUE);

        VBox loadingIndicatorContainer = new VBox(progressIndicator, loadingText);
        loadingIndicatorContainer.setPadding(new Insets(20));
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        progressIndicator.setPrefSize(40, 40);
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);
        loadingText.setFont(axisFont);
        loadingText.setFill(Color.WHITE);
        loadingText.setMouseTransparent(true);
        if (progressIndicator.isMouseTransparent()) {
            loadingText.setText("Loading...");
        }


        // We want to extend the extra axis (volume) visually so that it encloses the chart area.
        extraAxisExtension = new Line();

        Paint lineColor = Color.rgb(95, 195, 195);
        extraAxisExtension.setFill(lineColor);
        extraAxisExtension.setStroke(lineColor);
        extraAxisExtension.setSmooth(false);
        extraAxisExtension.setStrokeWidth(2);

        extraAxisExtension.setStartX(0);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndX(0);
        extraAxisExtension.setEndY(chartHeight);
        extraAxisExtension.setStrokeWidth(2);


        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth,
                containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        if (!liveSyncing) {

            updateInProgressCandleTask = new UpdateInProgressCandleTask();
            updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                    new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));

            CompletableFuture.runAsync(() -> {


                try {
                    exchange.streamLiveTrades(tradePair, updateInProgressCandleTask);
                            Thread.sleep(1000); // Wait for a bit to ensure the websocket is ready to receive live trades.
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }


                    updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);

            });
        } else {
            inProgressCandle = null;
            updateInProgressCandleTask = null;
            updateInProgressCandleExecutor = null;
        }

        candlePageConsumer = new CandlePageConsumer();
        mouseDraggedHandler = new MouseDraggedHandler();
        scrollHandler = new ScrollEventHandler();
        keyHandler = new KeyEventHandler();

        // When the application starts up and tries to initialize a candle stick chart the size can
        // fluctuate. So we wait to get the "final" size before laying out the chart. After we get
        // the size, we remove this listener from the gotFirstSize property.
        ChangeListener<? super Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                double numberOfVisibleWholeCandles = Math.floor(containerWidth.getValue().doubleValue() / candleWidth);
                chartWidth = (numberOfVisibleWholeCandles * candleWidth) - 60 + ((double) candleWidth / 2);
                chartWidth = (Math.floor(containerWidth.getValue().doubleValue() / candleWidth) * candleWidth) - 60 +
                        ((double) candleWidth / 2);
                chartHeight = containerHeight.getValue().doubleValue();
                canvas = new Canvas(chartWidth - 100, chartHeight - 100);

                canvas.setLayoutY(
                        (containerHeight.getValue().doubleValue() - chartHeight) / 2);
                canvas.setLayoutX((containerWidth.getValue().doubleValue() - chartWidth) / 2);
                canvas.widthProperty().addListener(sizeListener);
                canvas.heightProperty().addListener(sizeListener);
                extraAxisExtension.setTranslateX(-chartWidth + 37.8 + 70);
                extraAxis.setTranslateX(-chartWidth + 37.8 + 10);
                yAxis.setTranslateX(chartWidth - 37.8);
                StackPane chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
                chartStackPane.setTranslateX(64); // Only necessary when wrapped in StackPane...why?
                getChildren().addFirst(chartStackPane);
                canvas.setOnMouseEntered(_ -> canvas.getScene().setCursor(Cursor.HAND));
                canvas.setOnMouseExited(_ -> canvas.getScene().setCursor(Cursor.DEFAULT));
                graphicsContext = canvas.getGraphicsContext2D();
                Label exchangeMessage = new Label("");
                exchangeMessage.setLayoutX(10);
                exchangeMessage.setLayoutY(10);
                exchangeMessage.setText(exchange.getExchangeMessage());
                exchangeMessage.setTranslateX((-chartWidth + 37.8));
                exchangeMessage.setTranslateY((chartHeight - 37.8) / 2);
                getChildren().add(exchangeMessage);
                layoutChart();
                initializeEventHandlers();
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(candleDataPager.getCandleDataPreProcessor());
                gotFirstSize.removeListener(this);
            }
        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);

        chartOptions.horizontalGridLinesVisibleProperty().addListener((_, _, _) ->
                drawChartContents(true));
        chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) ->
                drawChartContents(true));
        chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents(true));
    }



    private void initializeEventHandlers() {
        if (canvas.getParent() != null) {
            canvas.getParent().addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                mousePrevX = -1;
                mousePrevY = -1;
            });

            canvas.getParent().addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            canvas.getParent().addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            canvas.getParent().addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        } else {
            canvas.parentProperty().addListener((_, _, newValue) -> {
                if (newValue != null) {
                    newValue.addEventFilter(MouseEvent.MOUSE_RELEASED, _ -> {
                        mousePrevX = -1;
                        mousePrevY = -1;
                    });

                    newValue.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    newValue.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
                    newValue.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                }
            });
        }
    }

    private void moveAlongX(int deltaX, boolean skipDraw) {
        if (deltaX != 1 && deltaX != -1) {
            throw new RuntimeException("deltaX must be 1 or -1 but was: %d".formatted(deltaX));
        }
        CompletableFuture<Boolean> progressIndicatorVisibleFuture = new CompletableFuture<>();
        Platform.runLater(() -> progressIndicatorVisibleFuture.complete(progressIndicator.isVisible()));
        progressIndicatorVisibleFuture.thenAccept(progressIndicatorVisible -> {
            // This is run on the JavaFX application thread.
            if (!progressIndicatorVisible) {
                int desiredXLowerBound = (int) xAxis.getLowerBound() + (deltaX == 1 ? secondsPerCandle : -secondsPerCandle);

                // Prevent moving in the positive direction past the point where only "minCandlesRemaining" candles
                // remain on the left-most part of the chart.
                int minCandlesRemaining = 3;
                if (desiredXLowerBound <= data.lastEntry().getValue().getOpenTime() -
                        (minCandlesRemaining - 1) * secondsPerCandle) {
                    if (desiredXLowerBound <= currZoomLevel.getMinXValue()) {
                        CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                                candleDataPager.getCandleDataPreProcessor()).whenComplete((result, throwable) -> {
                            // Show the loading indicator and freeze the chart during the time that the new data is
                            // being paged in.
                            if (throwable != null) {
                                logger.error("exception: ", throwable);
                                return;
                            }
                            paging = true;
                            progressIndicator.setVisible(true);
                            setAxisBoundsForMove(deltaX);
                            setYAndExtraAxisBounds();
                            if (!skipDraw) {
                                drawChartContents(true);
                            }
                            progressIndicator.setVisible(false);
                            paging = false;
                        });
                    } else {
                        setAxisBoundsForMove(deltaX);
                        setYAndExtraAxisBounds();
                        if (!skipDraw) {
                            drawChartContents(true);
                        }
                    }
                }
            }
        });
    }

    /**
     * Sets the bounds of the x-axis either one full candle to the right or left, depending on the sign
     * of deltaX. Currently, the size of deltaX does not matter (each call this method only moves
     * the duration of one full candle).
     *
     * @param deltaX set the bounds either one candle over to the right or left from the current position
     */
    private void setAxisBoundsForMove(int deltaX) {
        if (deltaX == 1) {
            xAxis.setUpperBound(xAxis.getUpperBound() + secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() + secondsPerCandle);
        } else if (deltaX == -1) {
            xAxis.setUpperBound(xAxis.getUpperBound() - secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() - secondsPerCandle);
        } else {
            throw new IllegalArgumentException("deltaX must be 1 or -1 but was: %d".formatted(deltaX));
        }
    }

    /**
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private void setYAndExtraAxisBounds() {
        logger.info("xAxis lower bound:%d".formatted((int) xAxis.getLowerBound()));
        final double idealBufferSpaceMultiplier = 0.35;
        if (!currZoomLevel.getExtremaForCandleRangeMap().containsKey((int) xAxis.getLowerBound())) {
            //  Does this *always* represent a coding error on our end, or can this happen during
            // normal chart functioning, and could we handle it more gracefully?
            logger.error("The extrema map did not contain extrema for x-value: %d".formatted((int) xAxis.getLowerBound()));
            logger.error("extrema map: %s".formatted(new TreeMap<>(currZoomLevel.getExtremaForCandleRangeMap())));
        }

        // The y-axis and extra axis extrema are obtained using a key offset by minus one candle duration. This makes
        // the chart work correctly. I don't fully understand the logic behind it, so I am leaving a note for
        // my future self.
        Pair<Extrema<Integer>, Extrema<Integer>> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle);
        if (extremaForRange == null) logger.error("extremaForRange was null!%d".formatted(secondsPerCandle));


        assert extremaForRange != null;
        Integer yAxisMax = extremaForRange.getValue().getMax();
        Integer yAxisMin = extremaForRange.getValue().getMin();
        double yAxisDelta = yAxisMax - yAxisMin;
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier)));

        extraAxis.setUpperBound(currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle).getKey().getMax());
    }

    private void layoutChart() {
        logger.info("CandleStickChart.layoutChart start");
        extraAxisExtension.setStartX(chartWidth - 37.5);
        extraAxisExtension.setEndX(chartWidth - 37.5);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndY((chartHeight - 100) * 0.75);

        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 100);
        double top = snappedTopInset();
        double left = snappedLeftInset();
        top = snapPositionY(top);
        left = snapPositionX(left);

        // try and work out width and height of axes
        double xAxisWidth;
        double xAxisHeight = 25; // guess x axis height to start with
        double yAxisWidth = 0;
        double yAxisHeight;
        for (int count = 0; count < 3; count++) {
            yAxisHeight = snapSizeY(chartHeight - xAxisHeight);
            if (yAxisHeight < 0) {
                yAxisHeight = 0;
            }
            yAxisWidth = yAxis.prefWidth(yAxisHeight);
            xAxisWidth = snapSizeX(chartWidth - yAxisWidth);
            if (xAxisWidth < 0) {
                xAxisWidth = 0;
            }
            double newXAxisHeight = xAxis.prefHeight(xAxisWidth);
            if (newXAxisHeight == xAxisHeight) {
                break;
            }
            xAxisHeight = newXAxisHeight;
        }

        xAxisHeight = Math.ceil(xAxisHeight);
        yAxisWidth = Math.ceil(yAxisWidth);

        // calc yAxis x-pos
        double yAxisX = left + 1;
        left += yAxisWidth;
        xAxis.setLayoutX(left);
        yAxis.setLayoutX(yAxisX);
        xAxis.setPrefSize(chartWidth - 100, xAxisHeight);
        yAxis.setPrefSize(yAxisWidth, chartHeight - 100);
        extraAxis.setPrefSize(yAxisWidth, (chartHeight - 100) * 0.25);
        xAxis.setLayoutY(chartHeight - 100);
        yAxis.setLayoutY(top);
        extraAxis.setLayoutX(chartWidth - 38);
        extraAxis.setLayoutY((chartHeight - 100) * 0.75);
        xAxis.requestAxisLayout();
        xAxis.layout();
        yAxis.requestAxisLayout();
        yAxis.layout();
        extraAxis.requestAxisLayout();
        extraAxis.layout();
        canvas.setLayoutX(left);
        canvas.setLayoutY(top);
        logger.info("CandleStickChart.layoutChart end");
    }

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis
     * bounds.
     */
    private void drawChartContents(boolean clearCanvas) {
        // TODO should this expression start with (xAxis.getUpperBound() - secondsPerCandle)?
        // This value allows for us to go past the highest x-value by skipping the drawing of some candles.
        int numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) /
                secondsPerCandle, 0);

        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            // The duration of the last in-progress candle has ended, see if it is visible on screen.
            if (xAxis.getUpperBound() >= inProgressCandleLastDraw && xAxis.getUpperBound() <
                    inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)) {
                // If the new in-progress candle would be drawn off-screen, first move one candle duration
                // in the positive direction (so that the newest data is kept on-screen).
                if (numCandlesToSkip == 0) {
                    // Make room for the new in-progress candle.
                    moveAlongX(1, true);
                    numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() -
                            data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);
                }
            }
            inProgressCandleLastDraw = inProgressCandle.getOpenTime();
        }

        if (clearCanvas) {
            graphicsContext.setFill(Color.BLACK);
            graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 100);
        }

        double monetaryUnitsPerPixel = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        double pixelsPerMonetaryUnit = 1d / monetaryUnitsPerPixel;
        NavigableMap<Integer, CandleData> candlesToDraw = data.subMap(((int) xAxis.getUpperBound() - secondsPerCandle) -
                        (((int) currZoomLevel.getNumVisibleCandles()) * secondsPerCandle), true,
                ((int) xAxis.getUpperBound() - secondsPerCandle) - (numCandlesToSkip * secondsPerCandle), true);

        logger.info("Drawing " + candlesToDraw.size() + " candles.");
        if (chartOptions.isHorizontalGridLinesVisible()) {
            // Draw horizontal grid lines aligned with y-axis major tick marks
            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
                graphicsContext.setLineWidth(1.5);
                graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
            }
        }

        if (chartOptions.isVerticalGridLinesVisible()) {
            // Draw vertical grid lines aligned with x-axis major tick marks
            for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(189, 189, 189, 0));
                graphicsContext.setLineWidth(1.5);
                graphicsContext.strokeLine(tickMark.getPosition(), 0, tickMark.getPosition(), canvas.getHeight());
            }
        }

        int candleIndex = numCandlesToSkip;
        double highestCandleValue = Double.MIN_VALUE;
        double lowestCandleValue = Double.MAX_VALUE;
        int candleIndexOfHighest = -1;
        int candleIndexOfLowest = -1;
        int volumeBarMaxHeight = 150;
        double volumeScale = volumeBarMaxHeight / extraAxis.getUpperBound();
        double halfCandleWidth = candleWidth * 0.5;
        double lastClose = -1;
        for (CandleData candleDatum : candlesToDraw.descendingMap().values()) {
            // TODO(mike): We could change the sliding window extrema function to map to doubles instead of ints
            // and use that here instead of iterating over the candle data again.
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
                // We don't want to draw the high/low markers off-screen, so we guard it with the above condition.
                if (candleDatum.getHighPrice() > highestCandleValue) {
                    highestCandleValue = candleDatum.getHighPrice();
                    candleIndexOfHighest = candleIndex;
                }

                if (candleDatum.getLowPrice() < lowestCandleValue) {
                    lowestCandleValue = candleDatum.getLowPrice();
                    candleIndexOfLowest = candleIndex;
                }
            }

            if (candleDatum.isPlaceHolder()) {
                // A placeholder candle is placed in a duration where no trading activity occurred.
                graphicsContext.beginPath();
                double candleOpenPrice = candleDatum.getOpenPrice();
                if (chartOptions.isAlignOpenClose() && lastClose != -1) {
                    candleOpenPrice = lastClose;
                }

                double candleYOrigin = cartesianToScreenCoords((candleOpenPrice - yAxis.getLowerBound()) *
                        pixelsPerMonetaryUnit);

                graphicsContext.beginPath();
                graphicsContext.moveTo((canvas.getWidth() - (candleIndex * candleWidth)) + 1, candleYOrigin);
                graphicsContext.rect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin,
                        candleWidth - 1, 1);
                graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
                graphicsContext.fill();
                graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
                graphicsContext.setLineWidth(1);
                graphicsContext.stroke();
            } else {
                Paint candleBorderColor;
                Paint candleFillColor;

                double candleOpenPrice = candleDatum.getOpenPrice();
                if (chartOptions.isAlignOpenClose() && lastClose != -1) {
                    candleOpenPrice = lastClose;
                }

                boolean openAboveClose = candleOpenPrice > candleDatum.getClosePrice();

                if (openAboveClose) {
                    candleBorderColor = BEAR_CANDLE_BORDER_COLOR;
                    candleFillColor = BEAR_CANDLE_FILL_COLOR;
                } else {
                    candleBorderColor = BULL_CANDLE_BORDER_COLOR;
                    candleFillColor = BULL_CANDLE_FILL_COLOR;
                }

                double candleYOrigin;

                if (openAboveClose) {
                    candleYOrigin = cartesianToScreenCoords((candleOpenPrice -
                            yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
                } else {
                    candleYOrigin = cartesianToScreenCoords((candleDatum.getClosePrice() -
                            yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
                }

                double candleHeight = Math.abs(candleOpenPrice - candleDatum.getClosePrice()) * pixelsPerMonetaryUnit;

                // draw the candle bar
                graphicsContext.beginPath();
                graphicsContext.moveTo((canvas.getWidth() - (candleIndex * candleWidth)) + 2, candleYOrigin);
                graphicsContext.rect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin,
                        candleWidth - 2, candleHeight - 2);
                graphicsContext.setFill(candleFillColor);
                graphicsContext.fill();
                graphicsContext.setStroke(candleBorderColor);
                graphicsContext.setLineWidth(2);
                graphicsContext.stroke();
                //graphicsContext.beginPath(); // TODO(mike): Delete this line?

                // Draw high line (skip draw if the open (or close) is the same as the high.
                boolean drawHighLine = true;
                if (openAboveClose) {
                    if (candleOpenPrice == candleDatum.getHighPrice()) {
                        drawHighLine = false;
                    }

                    if (chartOptions.isAlignOpenClose()) {
                        if (candleOpenPrice > candleDatum.getHighPrice()) {
                            drawHighLine = false;
                        }
                    }
                } else {
                    if (candleDatum.getClosePrice() == candleDatum.getHighPrice()) {
                        drawHighLine = false;
                    }
                }

                if (drawHighLine) {
                    double candleHighValue = cartesianToScreenCoords((candleDatum.getHighPrice() -
                            yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
                    graphicsContext.moveTo(((canvas.getWidth() - (candleIndex * candleWidth)) + halfCandleWidth) - 1,
                            candleYOrigin);
                    graphicsContext.lineTo(((canvas.getWidth() - (candleIndex * candleWidth)) + halfCandleWidth) - 1,
                            candleHighValue);
                    graphicsContext.stroke();
                }

                // Draw low line (skip draw if the close (or open) is the same as the low.
                boolean drawLowLine = true;
                if (openAboveClose) {
                    if (candleDatum.getClosePrice() == candleDatum.getLowPrice()) {
                        drawLowLine = false;
                    }
                } else {
                    if (candleOpenPrice == candleDatum.getLowPrice()) {
                        drawLowLine = false;
                    }

                    if (chartOptions.isAlignOpenClose()) {
                        if (candleOpenPrice < candleDatum.getLowPrice()) {
                            drawLowLine = false;
                        }
                    }

                }
                if (drawLowLine) {
                    double candleLowValue = cartesianToScreenCoords((candleDatum.getLowPrice() -
                            yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
                    graphicsContext.moveTo(((canvas.getWidth() - (candleIndex * candleWidth)) + halfCandleWidth) - 1,
                            candleYOrigin + candleHeight);
                    graphicsContext.lineTo(((canvas.getWidth() - (candleIndex * candleWidth)) + halfCandleWidth) - 1,
                            candleLowValue);
                    graphicsContext.stroke();
                }

                // draw volume bar
                if (chartOptions.isShowVolume()) {
                    double candleVolumeYOrigin = cartesianToScreenCoords(candleDatum.getVolume() * volumeScale);
                    graphicsContext.beginPath();
                    graphicsContext.moveTo((canvas.getWidth() - (candleIndex * candleWidth)) + 2, candleVolumeYOrigin);
                    graphicsContext.rect(canvas.getWidth() - (candleIndex * candleWidth), candleVolumeYOrigin,
                            candleWidth - 2, candleVolumeYOrigin - 2);
                    graphicsContext.setFill(candleFillColor);
                    graphicsContext.fill();
                    graphicsContext.setStroke(candleBorderColor);
                    graphicsContext.setLineWidth(2);
                    graphicsContext.stroke();
                }
            }

            lastClose = candleDatum.getClosePrice();
            candleIndex++;
        }

        // Draw arrows to the extrema for the currently visible candles (helps to easily see the highs and lows of
        // the current range without needing to visually trace to the axis).
        graphicsContext.setFont(canvasNumberFont);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(AXIS_TICK_LABEL_COLOR);
        graphicsContext.setFontSmoothingType(FontSmoothingType.LCD);
        double highMarkYPos = cartesianToScreenCoords((highestCandleValue - yAxis.getLowerBound()) *
                pixelsPerMonetaryUnit) - 1;
        double lowMarkYPos = cartesianToScreenCoords((lowestCandleValue - yAxis.getLowerBound()) *
                pixelsPerMonetaryUnit) + 1;

        // Prevent the high and low markers from overlapping (this can happen if there is very little volatility
        // between candles and very few candles are on-screen).
        boolean skipLowMark = lowMarkYPos - highMarkYPos < canvasNumberFont.getSize() &&
                candleIndexOfHighest == candleIndexOfLowest;

        if (candleIndexOfHighest > currZoomLevel.getNumVisibleCandles() * 0.5) {
            // draw high marker to the right of the candle (arrow points to the left)
            double xPos = ((canvas.getWidth() - (candleIndexOfHighest * candleWidth)) + halfCandleWidth) + 2;
            graphicsContext.setTextAlign(TextAlignment.LEFT);
            graphicsContext.fillText("← " + MARKER_FORMAT.format(highestCandleValue), xPos, highMarkYPos);
        } else {
            // draw high marker to the left of the candle (arrow points to the right)
            double xPos = ((canvas.getWidth() - (candleIndexOfHighest * candleWidth)) + halfCandleWidth) - 3;
            graphicsContext.setTextAlign(TextAlignment.RIGHT);
            graphicsContext.fillText(MARKER_FORMAT.format(highestCandleValue) + " -→", xPos, highMarkYPos);
        }
        graphicsContext.setFill(Color.WHITE);
        graphicsContext.fillText(tradePair.toString(), canvas.getWidth() / 2, canvas.getHeight() / 3);
        graphicsContext.setStroke(Color.ORANGE);


        if (!skipLowMark) {
            if (candleIndexOfLowest > currZoomLevel.getNumVisibleCandles() * 0.5) {
                // draw a low marker to the right of the candle (arrow points to the left)
                double xPos = ((canvas.getWidth() - (candleIndexOfLowest * candleWidth)) + halfCandleWidth) + 2;
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                graphicsContext.fillText("← " + MARKER_FORMAT.format(lowestCandleValue), xPos, lowMarkYPos);
            } else {
                // draw a low marker to the left of the candle (arrow points to the right)
                double xPos = ((canvas.getWidth() - (candleIndexOfLowest * candleWidth)) + halfCandleWidth) - 3;
                graphicsContext.setTextAlign(TextAlignment.RIGHT);
                graphicsContext.fillText(MARKER_FORMAT.format(lowestCandleValue) + " →", xPos, lowMarkYPos);
            }
        }
    }

    @Contract(pure = true)
    private @NotNull String getTimeframe(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 3600 -> "1h";
            case 21600 -> "6h";
            case 86400 -> "1d";
            case 604800 -> "1w";
            case 2592000 -> "1mo";
            case 31536000 -> "1y";
            default -> "";
        };
    }


    private double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    protected void changeZoom(ZoomDirection zoomDirection) {
        final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
        if (currZoomLevel == null) {
            logger.error("currZoomLevel was null!");
            return;
        }
        int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
        if (newCandleWidth <= 1) {
            // Can't go below one pixel for candle width.
            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go below one pixel for candle width.");

            dat.showAndWait();
            return;
        }

        int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) (canvas.getWidth() /
                newCandleWidth) * secondsPerCandle));
        if (newLowerBoundX > data.lastEntry().getValue().getOpenTime() - (2 * secondsPerCandle)) {
            // We've reached the end of the data. We can't go back further without having more data.
            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go back further without having more data.");
            dat.showAndWait();
            return;
        }

        final int nextZoomLevelId = ZoomLevel.getNextZoomLevelId(currZoomLevel, zoomDirection);
        int currMinXValue = currZoomLevel.getMinXValue();

        if (!zoomLevelMap.containsKey(nextZoomLevelId)) {
            // We can use the minXValue of the current zoom level here because, given a sequence of zoom-levels
            // z(0), z(1), ... z(n) that the chart has gone through, z(x).minXValue <= z(y).minXValue for all x > y.
            // That is, if we are currently at a max/min zoom-level in zoomLevelMap, there is no other zoom-level that
            // has a lower minXValue (assuming we did not start at the maximum or minimum zoom level).
            ZoomLevel newZoomLevel = new ZoomLevel(nextZoomLevelId, newCandleWidth, secondsPerCandle,
                    canvas.widthProperty(), getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
                    currMinXValue);

            int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) -
                    data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);

            // If there is less than numVisibleCandles on the screen, we want to be sure and check against what the
            // lower bound *would be* if we had the full amount. Otherwise, we won't be able to calculate the correct
            // extrema because the window size will be greater than the number of candles we have data for.
            if (newLowerBoundX - (numCandlesToSkip * secondsPerCandle) < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly zoom out to this level.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, (int) newZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) Math.floor(
                                    newZoomLevel.getNumVisibleCandles()), candleData.size()));
                    zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                    currZoomLevel = newZoomLevel;
                    Platform.runLater(() -> {
                        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
                        candleWidth = currZoomLevel.getCandleWidth();
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();
                        drawChartContents(true);
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
                return;
            } else {
                List<CandleData> candleData = new ArrayList<>(data.values());
                putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, (int) newZoomLevel.getNumVisibleCandles());
                putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                        candleData.size() - (int) Math.floor(newZoomLevel.getNumVisibleCandles()),
                        candleData.size()));
                zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                currZoomLevel = newZoomLevel;
            }
        } else {
            //  In this case, we only need to compute the extrema for any new live syncing data that has
            //  happened since the last time we were at this zoom level.
            currZoomLevel = zoomLevelMap.get(nextZoomLevelId);
            List<CandleData> candleData = new ArrayList<>(data.values());
            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                    (int) currZoomLevel.getNumVisibleCandles());
            putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                    candleData.size() - (int) Math.floor(currZoomLevel.getNumVisibleCandles()), candleData.size()));
            xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
            candleWidth = currZoomLevel.getCandleWidth();
            xAxis.setLowerBound(newLowerBoundX);
        }

        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
        candleWidth = currZoomLevel.getCandleWidth();
        xAxis.setLowerBound(newLowerBoundX);
        setYAndExtraAxisBounds();
        drawChartContents(true);
    }

    CandleStickChartOptions getChartOptions() {
        return chartOptions;
    }

    Consumer<List<CandleData>> getCandlePageConsumer() {
        return candlePageConsumer;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 200;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 200;
    }

    @Override
    protected double computePrefWidth(double height) {
        return chartWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return chartHeight;
    }

    private void setInitialState(List<CandleData> candleData) {
        if (!liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                candleData.getFirst().getOpenTime());
        zoomLevelMap.put(0, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                (int) Math.round(currZoomLevel.getNumVisibleCandles()));
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - (int) Math.floor(currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
        setYAndExtraAxisBounds();
        data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())));
        drawChartContents(false);
        progressIndicator.setVisible(false);
        updateInProgressCandleTask.setReady(true);
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            chartWidth = Math.max(300, Math.floor(containerWidth.getValue().doubleValue() / candleWidth) *
                    candleWidth - 60 + ((double) candleWidth / 2));
            chartHeight = Math.max(300, containerHeight.getValue().doubleValue());
            canvas.setWidth(chartWidth - 100);
            canvas.setHeight(chartHeight - 100);

            // Because the chart has been resized, the number of visible candles has changed and thus we must
            // recompute the sliding window extrema where the size of the sliding window is the new number of
            // visible candles.
            int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) currZoomLevel.getNumVisibleCandles() *
                    secondsPerCandle));
            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly resize the chart.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, (int) Math.round(currZoomLevel.getNumVisibleCandles()));
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) Math.floor(
                                    currZoomLevel.getNumVisibleCandles()), candleData.size()));
                    Platform.runLater(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();
                        layoutChart();
                        drawChartContents(true);
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
            } else {
                currZoomLevel.getExtremaForCandleRangeMap().clear();
                List<CandleData> candleData = new ArrayList<>(data.values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, (int) Math.round(currZoomLevel.getNumVisibleCandles()));
                putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData.subList(candleData.size() - (int) Math.floor(
                                currZoomLevel.getNumVisibleCandles()), candleData.size()));
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChartContents(true);
            }
        }
    }

    private class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
        private final BlockingQueue<Trade> liveTradesQueue;
        private boolean ready;

        UpdateInProgressCandleTask() {
            liveTradesQueue = new LinkedBlockingQueue<>();
        }







        @Override
        public void accept(Trade trade) {
            liveTradesQueue.add(trade);
        }

        @Override
        public void acceptTrades(@NotNull List<Trade> trades) {
            liveTradesQueue.addAll(trades);
        }

        @Override
        public void run() {
            if (inProgressCandle == null) {
                throw new RuntimeException("inProgressCandle was null in live syncing mode.");
            }
            if (!ready) {
                logger.info("No live trades received not updating in-progress candle!");
                return;
            }


            int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);

            graphicsContext.setFill(Color.WHITE);
            graphicsContext.setTextAlign(TextAlignment.CENTER);
            graphicsContext.fillText("Time :%s".formatted(Date.from(Instant.ofEpochSecond(data.lastEntry().getValue().getOpenTime()))), canvas.getWidth() / 6, canvas.getHeight() / 7);
            graphicsContext.fillText("TimeFrame  :%s".formatted(getTimeframe(secondsPerCandle)), 120, 20);
            graphicsContext.fillText("O :%s".formatted(data.lastEntry().getValue().getOpenPrice()), 320, 20);
            graphicsContext.fillText("H :%s".formatted(data.lastEntry().getValue().getHighPrice()), 420, 20);
            graphicsContext.fillText("L :%s".formatted(data.lastEntry().getValue().getLowPrice()), 520, 20);
            graphicsContext.fillText("C :%s".formatted(data.lastEntry().getValue().getClosePrice()), 820, 20);
            graphicsContext.fillText("V :%s".formatted(data.lastEntry().getValue().getVolume()), 1020, 20);
            graphicsContext.setStroke(Color.TRANSPARENT);
            graphicsContext.setFill(Color.WHITE);

            // Get rid of trades we already know about
            List<Trade> newTrades = liveTrades.stream().filter(trade -> trade.getTimestamp().getEpochSecond() >
                    inProgressCandle.getCurrentTill()).toList();

            // Partition the trades between the current in-progress candle and the candle after that (which we may
            // have entered after the last update).
            Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream().collect(
                    Collectors.partitioningBy(trade -> trade.getTimestamp().getEpochSecond() >=
                            inProgressCandle.getOpenTime() + secondsPerCandle));

            // Update the in-progress candle with new trades partitioned in the in-progress candle's duration
            currentCandleTrades = candlePartitionedNewTrades.get(false);

            if (!currentCandleTrades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble(),
                        inProgressCandle.getHighPriceSoFar()));
                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().getAsDouble(),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setCloseTime(currentTill);
                inProgressCandle.setLastPrice(currentCandleTrades.getLast()
                        .getPrice());
                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            graphicsContext.setFill(Color.WHITE);
            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble());
                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().getAsDouble());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                }

                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            drawChartContents(true);

            //Execute AI Training and Trade Analysis

            // Set up the attributes for the Instances (Open, High, Low, Close, Volume)
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("open"));
            attributes.add(new Attribute("high"));
            attributes.add(new Attribute("low"));
            attributes.add(new Attribute("close"));
            attributes.add(new Attribute("volume"));

            ArrayList<String> classValues = new ArrayList<>();
            classValues.add("BUY");
            classValues.add("SELL");
            classValues.add("HOLD");
            attributes.add(new Attribute("class", classValues));

            // Create an empty dataset with these attributes
            Instances trainingData = new Instances("MarketData", attributes, 0);
            trainingData.setClassIndex(Math.max((trainingData.numAttributes() - 1), 0));

            tradingAI = new TradingAI(trainingData);

            for (int i = 0; i < 100; i++) {
                DenseInstance instance = new DenseInstance(attributes.size());
                instance.setValue(attributes.get(0), data.lastEntry().getValue().getOpenPrice()); // open
                instance.setValue(attributes.get(1), data.lastEntry().getValue().getHighPrice()); // high
                instance.setValue(attributes.get(2), data.lastEntry().getValue().getLowPrice());  // low
                instance.setValue(attributes.get(3), data.lastEntry().getValue().getClosePrice()); // close
                instance.setValue(attributes.get(4), data.lastEntry().getValue().getVolume());      // volume

                tradingAI.getSignal(
                        data.lastEntry().getValue().getOpenPrice(),
                        data.lastEntry().getValue().getHighPrice(),
                        data.lastEntry().getValue().getLowPrice(),
                        data.lastEntry().getValue().getClosePrice(),
                        data.lastEntry().getValue().getVolume()); // signal: BUY, SELL, HOLD

                // class: BUY, SELL, HOLD

                trainingData.add(instance);
            }

        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        @Override
        public void accept(List<CandleData> candleData) {


            if (Platform.isFxApplicationThread()) {
                logger.error("candle data paging must not happen on FX thread!");
                throw new IllegalStateException("candle data paging must not happen on FX thread!");
            }

            if (candleData.isEmpty()) {
                logger.warn("candleData was empty");
                return;
            }

            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }

            if (data.isEmpty()) {
                if (liveSyncing) {
                    if (inProgressCandle == null) {
                        throw new RuntimeException("inProgressCandle was null in live syncing mode.");
                    }
                    // We got the first page of candle data which does *not* include the current in-progress
                    // candle. Since we are live-syncing, we need to fetch the data for what has occurred so far in
                    // the current candle.
                    long secondsIntoCurrentCandle = (Instant.now().toEpochMilli() / 1000L) -
                            (candleData.getLast().getOpenTime() + secondsPerCandle);
                    inProgressCandle.setOpenTime(candleData.getLast().getOpenTime() +
                            secondsPerCandle);

                    // We first attempt to get caught up by simply requesting shorter duration candles. Say this chart
                    // is displaying one hour per candle and secondsIntoCurrentCandle is 1800 (30 minutes). Then we
                    // would request candles starting from when the current in-progress candle started but with
                    // a supported duration closest to, but less than the current duration, 1800/200 (as 200 is the
                    // limit of candles per page). This would give us 9 second candles that we can then sum. This will
                    // catch the data up to within 9 seconds of current time (or in this case, roughly within 0.25% of
                    // current time).
                    CompletableFuture<Optional<InProgressCandleData>> inProgressCandleDataOptionalFuture = exchange
                            .fetchCandleDataForInProgressCandle(tradePair, Instant.ofEpochSecond(
                                            candleData.getLast().getOpenTime() + secondsPerCandle),
                                    secondsIntoCurrentCandle, secondsPerCandle);
                    inProgressCandleDataOptionalFuture.whenComplete((inProgressCandleDataOptional, throwable) -> {
                        if (throwable == null) {
                            if (inProgressCandleDataOptional.isPresent()) {
                                InProgressCandleData inProgressCandleData = inProgressCandleDataOptional.get();

                                // Our second attempt to get caught up requests all trades that have happened since
                                // the time of the last sub-candle (the 9 seconds long candles from above). This will
                                // get us caught up to the current time. The reason we don't use the more simple
                                // approach of requesting all the trades that have happened in the current candle to
                                // begin with is that this can take a prohibitively long time if the candle duration
                                // is too large. (Some exchanges have multiple trades every second.)
                                int currentTill = (int) Instant.now().getEpochSecond();
                                CompletableFuture<List<Trade>> tradesFuture = null;
                                try {
                                    tradesFuture = exchange.fetchRecentTradesUntil(
                                            tradePair, Instant.ofEpochSecond(inProgressCandleData.currentTill()));
                                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                                    throw new RuntimeException(e);
                                }

                                tradesFuture.whenComplete((trades, exception) -> {
                                    if (exception == null) {
                                        inProgressCandle.setOpenPrice(inProgressCandleData.openPrice());
                                        inProgressCandle.setCloseTime(currentTill);

                                        if (trades.isEmpty()) {
                                            // No trading activity happened in addition to the sub-candles from above.
                                            inProgressCandle.setHighPriceSoFar(
                                                    inProgressCandleData.highPriceSoFar());
                                            inProgressCandle.setLowPriceSoFar(inProgressCandleData.lowPriceSoFar());
                                            inProgressCandle.setVolumeSoFar(inProgressCandleData.volumeSoFar());
                                            inProgressCandle.setLastPrice(inProgressCandleData.lastPrice());
                                        } else {
                                            // We need to factor in the trades that have happened after the
                                            // "currentTill" time of the in-progress candle.
                                            inProgressCandle.setHighPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).max().getAsDouble(),
                                                    inProgressCandleData.highPriceSoFar()));
                                            inProgressCandle.setLowPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).min().getAsDouble(),
                                                    inProgressCandleData.lowPriceSoFar()));
                                            inProgressCandle.setVolumeSoFar(inProgressCandleData.volumeSoFar() +
                                                    trades.stream().mapToDouble(
                                                            Trade::getAmount).sum());
                                            inProgressCandle.setLastPrice(trades.getLast().getPrice());
                                        }
                                        Platform.runLater(() -> setInitialState(candleData));
                                    } else {
                                        logger.error("error fetching recent trades until: %s".formatted(inProgressCandleData), exception);
                                    }
                                });
                            } else {
                                // No trades have happened during the current candle so far.
                                inProgressCandle.setIsPlaceholder(true);
                                inProgressCandle.setVolumeSoFar(0);
                                inProgressCandle.setCloseTime((int) (secondsIntoCurrentCandle +
                                        (candleData.getLast().getOpenTime() + secondsPerCandle)));
                                Platform.runLater(() -> setInitialState(candleData));
                            }
                        } else {
                            logger.error("error fetching in-progress candle data: ", throwable);
                        }
                    });
                } else {
                    setInitialState(candleData);
                }
            } else {
                int slidingWindowSize = (int) currZoomLevel.getNumVisibleCandles();

                // To compute the y-axis extrema for the new data in the page, we have to include the
                // first numVisibleCandles from the previous page (otherwise the sliding window will not be able
                // to reach all the way).
                Map<Integer, CandleData> extremaData = new TreeMap<>(data.subMap(currZoomLevel.getMinXValue(),
                        currZoomLevel.getMinXValue() + (int) (currZoomLevel.getNumVisibleCandles() *
                                secondsPerCandle)));
                List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
                newDataPlusOffset.addAll(extremaData.values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset,
                        slidingWindowSize);
                data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime,
                        Function.identity())));
                currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            }
        }
    }

    private class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (mousePrevX == -1 && mousePrevY == -1) {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                return;
            }

            double dx = event.getScreenX() - mousePrevX;

            scrollDeltaXSum += dx;

            if (Math.abs(scrollDeltaXSum) >= 10) {
                int deltaX = (int) -Math.signum(scrollDeltaXSum);
                moveAlongX(deltaX, false);
                scrollDeltaXSum = 0;
            }
            mousePrevX = event.getScreenX();
            mousePrevY = event.getScreenY();
        }
    }

    private class KeyEventHandler implements EventHandler<KeyEvent> {
        @Override
        public void handle(KeyEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            boolean consume = false;
            if (event.isControlDown() && event.getCode() == KeyCode.PLUS) {
                changeZoom(ZoomDirection.IN);
                consume = true;
            } else if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
                changeZoom(ZoomDirection.OUT);
                consume = true;
            }

            int deltaX = 0;
            if (event.getCode() == KeyCode.LEFT) {
                deltaX = -1;
                consume = true;
            } else if (event.getCode() == KeyCode.RIGHT) {
                deltaX = 1;
                consume = true;
            }

            if (deltaX != 0) {
                moveAlongX(deltaX, false);
            }

            if (consume) {
                event.consume();
            }
        }
    }

    private class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override
        public void handle(ScrollEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            if (event.getDeltaY() != 0 && event.getTouchCount() == 0 && !event.isInertia()) {
                final double direction = -Math.signum(event.getDeltaY());

                if (direction == 1.0d) {
                    changeZoom(ZoomDirection.OUT);
                } else if (direction == -1.0d) {
                    changeZoom(ZoomDirection.IN);
                }
            }
            event.consume();
        }
    }
}