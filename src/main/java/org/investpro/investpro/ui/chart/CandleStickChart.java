package org.investpro.investpro.ui.chart;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
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

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.investpro.investpro.*;
import org.investpro.investpro.ai.*;
import org.investpro.investpro.model.*;
import org.investpro.investpro.ui.CandleStickChartToolbar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.investpro.CandleStickChartUtils.getXAxisFormatterForRange;
import static org.investpro.investpro.CandleStickChartUtils.putSlidingWindowExtrema;
import static org.investpro.investpro.ChartColors.*;
import static org.investpro.investpro.ENUM_ORDER_TYPE.STOP;
import static org.investpro.investpro.Side.BUY;
import static org.investpro.investpro.Side.SELL;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.LEFT;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.RIGHT;

@Getter
@Setter
public class CandleStickChart extends Region {

    public static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    public static final Paint BEAR_CANDLE_BORDER_COLOR = Color.rgb(204, 20, 20, 0.7);
    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.rgb(189, 189, 189, 0.7);
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.rgb(204, 204, 204, 0.7);
    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(234, 154, 17);
    private static final DecimalFormat MARKER_FORMAT = new DecimalFormat("#.000000000");
    private static final InProgressCandle inProgressCandle = new InProgressCandle();
    // index of first visible candle
    private static final int SCROLL_STEP = 5; // how many candles to move per navigation
    public static int secondsPerCandle = 0;
    static CandleDataPager candleDataPager;
    static boolean liveSyncing;
    static ProgressIndicator progressIndicator;
    static Font canvasNumberFont;
    private static CandleStickChartOptions chartOptions = new CandleStickChartOptions();
    private static boolean showGrid;
    private static Map<Long, ZoomLevel> zoomLevelMap = Map.of();
    private static StableTicksAxis xAxis = new StableTicksAxis();
    private static StableTicksAxis yAxis = new StableTicksAxis();
    private static StableTicksAxis extraAxis = new StableTicksAxis();
    @Getter
    private static Canvas canvas;
    private static GraphicsContext graphicsContext;
    private static int candleWidth = 10;
    private static NavigableMap<Long, CandleData> data = Collections.synchronizedNavigableMap(new TreeMap<>(Long::compare));
    private static int inProgressCandleLastDraw = -1;
    private static volatile ZoomLevel currZoomLevel;
    private static volatile boolean paging;
    private static double chartWidth = 1000;
    private static double chartHeight = 600;
    private final Exchange exchange;
    private final TradePair tradePair;
    private final CandlePageConsumer candlePageConsumer;
    private final ScheduledExecutorService updateInProgressCandleExecutor;
    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    private final Line extraAxisExtension;
    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private final EventHandler<ScrollEvent> scrollHandler;
    private final EventHandler<KeyEvent> keyHandler;
    private final IndicatorManager indicatorManager;
    CandleDataSupplier candleDataSupplier;
    TradingAI tradingAI;
    TradeHistory tradeHistory;
    List<Trade> currentCandleTrades;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;
    @Getter
    private ChartOverlayManager overlayManager;
    private List<CandleData> candleData;
    private double startIndex;
    private String tokens;
    private int verticalGridLines = 10;
    private int horizontalGridLines = 5;
    private int scrollStep = 1; // finer than navigation step

    public CandleStickChart(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier,
                            boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                            ObservableNumberValue containerHeight, String token) throws IOException {
        logger.debug(String.valueOf(this));
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(candleDataSupplier);
        this.tradePair = tradePair;
        this.tokens = token;

        indicatorManager = new IndicatorManager(this);
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("CandleStickChart must be constructed on the JavaFX Application Thread but was called from %s".formatted(Thread.currentThread()));
        }

        DoubleProperty xax = new SimpleDoubleProperty(1000);
        InstantAxisFormatter yu = new InstantAxisFormatter(DateTimeFormatter.BASIC_ISO_DATE);
        currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, xax, yu, 10);

        this.exchange = exchange;
        this.overlayManager = new ChartOverlayManager(this);
        CandleStickChart.secondsPerCandle = secondsPerCandle;
        CandleStickChart.liveSyncing = liveSyncing;


        zoomLevelMap = new ConcurrentHashMap<>();
        chartOptions = new CandleStickChartOptions();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 15);
        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis = new StableTicksAxis(
                0,
                secondsPerCandle
        );
        yAxis = new StableTicksAxis();

        yAxis.setSide(Side.RIGHT);
        extraAxis = new StableTicksAxis();
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
        if (progressIndicator.isIndeterminate()) {
            loadingText.setText("Loading...");
            loadingText.setStroke(Color.GREEN);
        }
        canvas = new Canvas(containerWidth.doubleValue() - 100, containerHeight.doubleValue() - 100);
        graphicsContext = canvas.getGraphicsContext2D();

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

        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth,
                containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);


        updateInProgressCandleTask = new UpdateInProgressCandleTask();
        updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));

        CompletableFuture.runAsync(() -> {
            try {
                candleData = candleDataSupplier.get().get();

                updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);

                CompletableFuture<Trade> trades = exchange.fetchRecentTrades(tradePair, Instant.now());
                Thread.sleep(1000); // Wait for a bit to ensure the websocket is ready to receive live trades.

                logger.info("Recent trades{}", trades.get());


            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }


        });
        this.candleDataSupplier = candleDataSupplier;
        this.candlePageConsumer = new CandlePageConsumer(this);
        candleDataPager = new CandleDataPager(this);

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


                CompletableFuture.supplyAsync(candleDataSupplier).thenAccept(candleDataPager.getCandleDataPreProcessor())
                        .exceptionally(
                                e -> {
                                    throw new RuntimeException(e);
                                });

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
        canvas.setOnScroll(event -> {
            if (event.getDeltaY() < 0) {
                scroll(NavigationDirection.LEFT);
            } else {
                scroll(NavigationDirection.RIGHT);
            }
        });


        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);

    }

    /**
     * Sets the bounds of the x-axis either one full candle to the right or left, depending on the sign
     * of deltaX. Currently, the size of deltaX does not matter (each call this method only moves
     * the duration of one full candle).
     *
     * @param deltaX set the bounds either one candle over to the right or left from the current position
     */
    private static void setAxisBoundsForMove(int deltaX) {
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

    public static void putExtremaForRemainingElements(Map<Integer, Pair<Extrema, Extrema>> extrema,
                                                      final List<CandleData> candleData) {
        Objects.requireNonNull(extrema, "extrema map must not be null");
        Objects.requireNonNull(candleData, "candleData list must not be null");

        if (candleData.isEmpty()) {
            throw new IllegalArgumentException("candleData must not be empty");
        }

        double minVolume = Integer.MAX_VALUE;
        double maxVolume = Integer.MIN_VALUE;
        double minPrice = Integer.MAX_VALUE;
        double maxPrice = Integer.MIN_VALUE;

        // Iterate from the last element to the first
        for (int i = candleData.size() - 1; i >= 0; i--) {
            CandleData candle = candleData.get(i);

            // Update extrema
            minVolume = Math.min(candle.getVolume(), minVolume);
            maxVolume = Math.max(Math.ceil(candle.getVolume()), maxVolume);
            minPrice = Math.min(candle.getLowPrice(), minPrice);
            maxPrice = Math.max(Math.ceil(candle.getHighPrice()), maxPrice);

            // Store extrema for this open time
            extrema.put(candle.getOpenTime(), new Pair<>(
                    new Extrema(minVolume, maxVolume),
                    new Extrema(minPrice, maxPrice)
            ));
        }
    }

    private static void moveAlongX(int deltaX, boolean skipDraw) {
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
                        CompletableFuture.supplyAsync(candleDataPager.candleDataPreProcessor.candleStickChart.getCandleDataSupplier()).thenAccept(
                                candleDataPager.getCandleDataPreProcessor()).whenComplete((_, throwable) -> {
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
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private static void setYAndExtraAxisBounds() {
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
        Pair<Extrema, Extrema> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle);
        if (extremaForRange == null) {
            logger.error("extremaForRange was null!%d".formatted(secondsPerCandle));

            return;

        }
        double yAxisMax = extremaForRange.getValue().getMax();
        double yAxisMin = extremaForRange.getValue().getMin();
        double yAxisDelta = yAxisMax - yAxisMin;
        //yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        // yAxis.setLowerBound(0);

        // extraAxis.setUpperBound(currZoomLevel.getExtremaForCandleRangeMap().get(
        //       (int)xAxis.getLowerBound() - secondsPerCandle).getValue().getMax());
    }

    private static double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis bounds.
     */
    private static void drawChartContents(boolean clearCanvas) {
        int numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);

        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            if (xAxis.getUpperBound() >= inProgressCandleLastDraw && xAxis.getUpperBound() < inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)) {
                if (numCandlesToSkip == 0) {
                    moveAlongX(1, true);
                    numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);
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
        NavigableMap<Long, CandleData> candlesToDraw = data.subMap((long) ((xAxis.getUpperBound() - secondsPerCandle) - ((long) (currZoomLevel.getNumVisibleCandles()) * secondsPerCandle)), true,
                (long) ((xAxis.getUpperBound() - secondsPerCandle) - ((long) numCandlesToSkip * secondsPerCandle)), true);

        logger.info("Drawing {} candles.", candlesToDraw.size());

        if (chartOptions.isHorizontalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
                graphicsContext.setLineWidth(1.5);
                graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
            }
        }

        if (chartOptions.isVerticalGridLinesVisible()) {
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
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
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
                drawPlaceholderCandle(candleDatum, candleIndex, lastClose, pixelsPerMonetaryUnit);
            } else {
                drawActualCandle(candleDatum, candleIndex, lastClose, pixelsPerMonetaryUnit, volumeScale);
            }

            lastClose = candleDatum.getClosePrice();
            candleIndex++;
        }

        drawHighLowMarkers(highestCandleValue, lowestCandleValue, candleIndexOfHighest, candleIndexOfLowest, pixelsPerMonetaryUnit);
    }

    private static void drawHighLowMarkers(double highestCandleValue, double lowestCandleValue, int candleIndexOfHighest, int candleIndexOfLowest, double pixelsPerMonetaryUnit) {
        graphicsContext.setFont(canvasNumberFont);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(AXIS_TICK_LABEL_COLOR);
        graphicsContext.setFontSmoothingType(FontSmoothingType.LCD);

        double highMarkYPos = cartesianToScreenCoords((highestCandleValue - yAxis.getLowerBound()) * pixelsPerMonetaryUnit) - 1;
        double lowMarkYPos = cartesianToScreenCoords((lowestCandleValue - yAxis.getLowerBound()) * pixelsPerMonetaryUnit) + 1;
        boolean skipLowMark = lowMarkYPos - highMarkYPos < canvasNumberFont.getSize() && candleIndexOfHighest == candleIndexOfLowest;
        double halfCandleWidth = candleWidth * 0.5;

        if (candleIndexOfHighest > currZoomLevel.getNumVisibleCandles() * 0.5) {
            double xPos = ((canvas.getWidth() - (candleIndexOfHighest * candleWidth)) + halfCandleWidth) + 2;
            graphicsContext.setTextAlign(TextAlignment.LEFT);
            graphicsContext.fillText("← " + MARKER_FORMAT.format(highestCandleValue), xPos, highMarkYPos);
        } else {
            double xPos = ((canvas.getWidth() - (candleIndexOfHighest * candleWidth)) + halfCandleWidth) - 3;
            graphicsContext.setTextAlign(TextAlignment.RIGHT);
            graphicsContext.fillText(MARKER_FORMAT.format(highestCandleValue) + " -→", xPos, highMarkYPos);
        }

        if (!skipLowMark) {
            if (candleIndexOfLowest > currZoomLevel.getNumVisibleCandles() * 0.5) {
                double xPos = ((canvas.getWidth() - (candleIndexOfLowest * candleWidth)) + halfCandleWidth) + 2;
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                graphicsContext.fillText("← " + MARKER_FORMAT.format(lowestCandleValue), xPos, lowMarkYPos);
            } else {
                double xPos = ((canvas.getWidth() - (candleIndexOfLowest * candleWidth)) + halfCandleWidth) - 3;
                graphicsContext.setTextAlign(TextAlignment.RIGHT);
                graphicsContext.fillText(MARKER_FORMAT.format(lowestCandleValue) + " →", xPos, lowMarkYPos);
            }
        }
    }

    private static void drawPlaceholderCandle(CandleData candleDatum, int candleIndex, double lastClose, double pixelsPerMonetaryUnit) {
        graphicsContext.beginPath();
        double candleOpenPrice = candleDatum.getOpenPrice();
        if (chartOptions.isAlignOpenClose() && lastClose != -1) {
            candleOpenPrice = lastClose;
        }

        double candleYOrigin = cartesianToScreenCoords((candleOpenPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);

        graphicsContext.beginPath();
        graphicsContext.moveTo((canvas.getWidth() - (candleIndex * candleWidth)) + 1, candleYOrigin);
        graphicsContext.rect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin, candleWidth - 1, 1);
        graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
        graphicsContext.fill();
        graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
        graphicsContext.setLineWidth(1);
        graphicsContext.stroke();
    }

    private static void drawActualCandle(CandleData candleDatum, int candleIndex, double lastClose, double pixelsPerMonetaryUnit, double volumeScale) {
        double candleOpenPrice = candleDatum.getOpenPrice();
        if (chartOptions.isAlignOpenClose() && lastClose != -1) {
            candleOpenPrice = lastClose;
        }

        boolean isBullish = candleDatum.getClosePrice() >= candleOpenPrice;
        double candleYOrigin = cartesianToScreenCoords((isBullish ? candleDatum.getClosePrice() : candleOpenPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double candleHeight = Math.abs(candleDatum.getClosePrice() - candleOpenPrice) * pixelsPerMonetaryUnit;

        double x = canvas.getWidth() - (candleIndex * candleWidth);

        graphicsContext.setFill(isBullish ? BULL_CANDLE_FILL_COLOR : BEAR_CANDLE_FILL_COLOR);
        graphicsContext.setStroke(isBullish ? BULL_CANDLE_BORDER_COLOR : BEAR_CANDLE_BORDER_COLOR);
        graphicsContext.setLineWidth(1.5);

        graphicsContext.beginPath();
        graphicsContext.rect(x, candleYOrigin, candleWidth - 1, candleHeight == 0 ? 1 : candleHeight);
        graphicsContext.fill();
        graphicsContext.stroke();

        // High and Low Wicks
        double highY = cartesianToScreenCoords((candleDatum.getHighPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double lowY = cartesianToScreenCoords((candleDatum.getLowPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double centerX = x + candleWidth * 0.5;

        graphicsContext.beginPath();
        graphicsContext.moveTo(centerX, highY);
        graphicsContext.lineTo(centerX, lowY);
        graphicsContext.stroke();

        if (chartOptions.isShowVolume()) {
            double volumeHeight = candleDatum.getVolume() * volumeScale;
            double yVolume = cartesianToScreenCoords(volumeHeight);
            graphicsContext.setFill(Color.web("#888888", 0.4));
            graphicsContext.fillRect(x, yVolume, candleWidth - 1, chartHeight - yVolume);
        }
    }

    public static void changeZoom(ZoomDirection zoomDirection) {
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
        double currMinXValue = currZoomLevel.getMinXValue();

        if (!zoomLevelMap.containsKey((long) nextZoomLevelId)) {
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
                CompletableFuture.supplyAsync(candleDataPager.candleDataPreProcessor.candleStickChart.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, newZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) (double) newZoomLevel.getNumVisibleCandles(), candleData.size()));
                    zoomLevelMap.put((long) nextZoomLevelId, newZoomLevel);
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
                        candleData, newZoomLevel.getNumVisibleCandles());
                putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                        candleData.size() - newZoomLevel.getNumVisibleCandles(),
                        candleData.size()));
                zoomLevelMap.put((long) nextZoomLevelId, newZoomLevel);
                currZoomLevel = newZoomLevel;
            }
        } else {
            //  In this case, we only need to compute the extrema for any new live syncing data that has
            //  happened since the last time we were at this zoom level.
            currZoomLevel = zoomLevelMap.get(nextZoomLevelId);
            List<CandleData> candleData = new ArrayList<>(data.values());
            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                    currZoomLevel.getNumVisibleCandles());
            putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                    candleData.size() - currZoomLevel.getNumVisibleCandles(), candleData.size()));
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

    private static int getVisibleCandleCount() {
        return (int) (canvas.getWidth() / candleWidth);
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

        // try and work out the width and height of axes
        double xAxisWidth;
        double xAxisHeight = 25; // guess x-axis height to start with
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

    private void initializeEventHandlers() {
        if (canvas.getParent() != null) {
            canvas.getParent().addEventFilter(MouseEvent.MOUSE_RELEASED, _ -> {
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

    CandleStickChartOptions getChartOptions() {
        return chartOptions;
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
        return widthProperty().get();
    }

    @Override
    protected double computePrefHeight(double width) {
        return heightProperty().get();
    }

    private void setInitialState(List<CandleData> candleData) {
        if (liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                candleData.getFirst().getOpenTime());
        zoomLevelMap.put(0L, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());

        data.putAll(candleData.stream().collect(Collectors.toMap(m -> (long) m.getOpenTime(), Function.identity())));
        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                (currZoomLevel.getNumVisibleCandles()));
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - (currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
        setYAndExtraAxisBounds();
        drawChartContents(false);

        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                (currZoomLevel.getNumVisibleCandles()));
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - (currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
        setYAndExtraAxisBounds();
        progressIndicator.setVisible(false);
        updateInProgressCandleTask.setReady(true);
    }

    public List<CandleData> getCandlesData() {
        return data.values().stream().toList();
    }

    public void setGridLines(int vertical, int horizontal) {
        this.verticalGridLines = vertical;
        this.horizontalGridLines = horizontal;
        //draw(graphicsContext);
    }

    public double getCandleWidth() {

        return candleWidth;
    }

    public StableTicksAxis getYAxis() {
        return yAxis;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean b) {
        showGrid = b;
    }

    public void changeNavigation(CandleStickChartToolbar.Tool navigationDirection) {
        if (candleData == null || candleData.isEmpty()) return;

        int maxStart = Math.max(0, candleData.size() - getVisibleCandleCount());

        if (navigationDirection == LEFT) {
            startIndex = Math.min(startIndex + SCROLL_STEP, maxStart);
        } else if (navigationDirection == RIGHT) {
            startIndex = Math.max(startIndex - SCROLL_STEP, 0);
        }

        drawChartContents(true);
    }

    public void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new RuntimeException("Could not create printer job.");
        }

        boolean proceed = job.showPrintDialog(getScene().getWindow());
        if (proceed) {
            boolean success = job.printPage(this); // 'this' refers to the CandleStickChart StackPane
            if (success) {
                job.endJob();
            } else {
                throw new RuntimeException("Printing failed.");
            }
        }
    }

    public TelegramClient getTelegram() {
        return new TelegramClient(tokens);
    }

    public void setFullScreen(boolean fullScreen) {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            stage.setFullScreen(fullScreen);
        }
    }

    public void scroll(NavigationDirection navigationDirection) {
        if (candleData == null || candleData.isEmpty()) return;

        int maxStart = Math.max(0, candleData.size() - getVisibleCandleCount());

        if (navigationDirection == NavigationDirection.LEFT) {
            startIndex = Math.min(startIndex + scrollStep, maxStart);
        } else if (navigationDirection == NavigationDirection.RIGHT) {
            startIndex = Math.max(startIndex - scrollStep, 0);
        }

        drawChartContents(true);

    }

    public void exportAsPDF() {
        try {
            // Snapshot canvas to image
            WritableImage fxImage = canvas.snapshot(null, null);
            BufferedImage bImage = SwingFXUtils.fromFXImage(fxImage, null);

            // Create PDF document
            PDDocument document = new PDDocument();
            PDPage page = new PDPage();
            document.addPage(page);

            // Add chart image to PDF
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, bImage);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.drawImage(pdImage, 50, 250, (float) pdImage.getWidth() / 2, (float) pdImage.getHeight() / 2);
            contentStream.close();

            // Save file
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Chart as PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File file = fileChooser.showSaveDialog(canvas.getScene().getWindow());
            if (file != null) {
                document.save(file);
            }
            document.close();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void captureScreenshot() {
        SnapshotParameters params = new SnapshotParameters();
        WritableImage image = this.snapshot(params, null); // Snapshot of the entire StackPane

        // Define filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File file = new File(System.getProperty("user.home") + File.separator + "chart_screenshot_" + timestamp + ".png");

        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Screenshot saved to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }

    public void shareLink() {
        // Take snapshot of the chart
        WritableImage image = this.snapshot(new SnapshotParameters(), null);

        // Save to file (in user temp directory or predefined location)
        File outputFile = new File(System.getProperty("java.io.tmpdir"), "chart_snapshot.png");
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outputFile);

            // Open file location (optional)
            Desktop.getDesktop().open(outputFile.getParentFile());

            // Simulate "sharing" by printing path (could also copy to clipboard)
            System.out.println("Chart snapshot saved at: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class KeyEventHandler implements EventHandler<KeyEvent> {
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

    private static class ScrollEventHandler implements EventHandler<ScrollEvent> {
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
            int newLowerBoundX = (int) (xAxis.getUpperBound() - (currZoomLevel.getNumVisibleCandles() *
                    secondsPerCandle));
            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly resize the chart.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, (currZoomLevel.getNumVisibleCandles()));
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (
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
                        candleData, (currZoomLevel.getNumVisibleCandles()));
                putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData.subList(candleData.size() - (
                                currZoomLevel.getNumVisibleCandles()), candleData.size()));
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChartContents(true);
            }
        }
    }

    @Setter
    @Getter
    private class UpdateInProgressCandleTask implements Runnable {
        private final BlockingQueue<Trade> liveTradesQueue;
        Label infos = new Label();
        private boolean ready;


        UpdateInProgressCandleTask() {
            liveTradesQueue = new LinkedBlockingQueue<>();
        }

        @Override
        public void run() {
            if (!ready) {
                logger.info("No live trades received not updating in-progress candle!");
                return;
            }


            int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);


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
                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum(),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setCloseTime(currentTill);
                inProgressCandle.setLastPrice(currentCandleTrades.getLast()
                        .getPrice());
                data.put((long) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            graphicsContext.setFill(Color.WHITE);
            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().stream().sum());
                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                }

                data.put((long) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            drawChartContents(true);
            graphicsContext.setFill(Color.WHITE);
            graphicsContext.setTextAlign(TextAlignment.CENTER);
            graphicsContext.fillText("Time :%s".formatted(Date.from(Instant.ofEpochSecond(data.lastEntry().getValue().getOpenTime()))), canvas.getWidth() / 6, canvas.getHeight() / 7);
            graphicsContext.fillText("TimeFrame  :%s".formatted(getTimeframe(secondsPerCandle)), 120, 20);
            graphicsContext.fillText("O :%s".formatted(data.lastEntry().getValue().getOpenPrice()), 320, 20);
            graphicsContext.fillText("H :%s".formatted(data.lastEntry().getValue().getHighPrice()), 420, 20);
            graphicsContext.fillText("L :%s".formatted(data.lastEntry().getValue().getLowPrice()), 520, 20);
            graphicsContext.fillText("C :%s".formatted(data.lastEntry().getValue().getClosePrice()), 820, 20);
            graphicsContext.fillText("V :%s".formatted(data.lastEntry().getValue().getVolume()), 1020, 20);

            graphicsContext.setFill(Color.WHITE);

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
            tradeHistory = new TradeHistory();

            for (int i = 0; i < 100; i++) {
                DenseInstance instance = new DenseInstance(attributes.size());
                instance.setValue(attributes.get(0), data.lastEntry().getValue().getOpenPrice()); // open
                instance.setValue(attributes.get(1), data.lastEntry().getValue().getHighPrice()); // high
                instance.setValue(attributes.get(2), data.lastEntry().getValue().getLowPrice());  // low
                instance.setValue(attributes.get(3), data.lastEntry().getValue().getClosePrice()); // close
                instance.setValue(attributes.get(4), data.lastEntry().getValue().getVolume());      // volume


                // class: BUY, SELL, HOLD

                trainingData.add(instance);

                logger.info(
                        "trainingData {}", trainingData
                );

                SIGNAL signal = tradingAI.getSignal(
                        data.lastEntry().getValue().getOpenPrice(),
                        data.lastEntry().getValue().getHighPrice(),
                        data.lastEntry().getValue().getLowPrice(),
                        data.lastEntry().getValue().getClosePrice(),
                        data.lastEntry().getValue().getVolume()); // signal: BUY, SELL, HOLD


                if (signal != null) {
                    double price = 0;
                    double size = 0.01;
                    switch (signal) {
                        case BUY:
                            try {

                                infos.setText(
                                        "SIGNAL:BUY"
                                );
                                exchange.createOrder(tradePair, BUY,
                                        STOP, price, size, new Date(), 100, 100);
                            } catch (IOException | NoSuchAlgorithmException | InterruptedException |
                                     InvalidKeyException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        case SELL:
                            infos.setText(
                                    "SIGNAL:SELL"
                            );
                            try {
                                exchange.createOrder(tradePair, SELL,
                                        STOP, price, size, new Date(), 100, 100);
                            } catch (IOException | NoSuchAlgorithmException | InterruptedException |
                                     InvalidKeyException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        case HOLD:


                            infos.setText("Signal:HOLD");


                    }

                    List<Trade> tr = tradeHistory.getTradesByPair(tradePair);
                    logger.info(
                            "TradesHistory {}", tr
                    );
                    getChildren().add(infos);
                }


            }
        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        CandleStickChart chart;
        ChartAlertsManager alertsManager;

        public CandlePageConsumer(CandleStickChart chart) {
            this.chart = chart;
        }

        @Override
        public void accept(List<CandleData> candleData) {


            if (Platform.isFxApplicationThread()) {

                throw new IllegalStateException("candle data paging must not happen on FX thread!");
            }

            if (candleData.isEmpty()) {
                logger.warn("candleData was empty");
                throw new IllegalStateException("candleData was empty");
            }

            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }

            if (!data.isEmpty()) {
                List<CandleData> loadedCandles = data.values().stream().toList();
                // indicatorManager.addSimpleMovingAverage(loadedCandles, 20);
                // indicatorManager.addRSI(loadedCandles, 14);
                @NotNull AnchorPane drawingLayer = new AnchorPane();
                this.alertsManager = new ChartAlertsManager(chart, drawingLayer, candleData);
                alertsManager.startChecking();
                alertsManager.addAlertLine(30000.0);
                CandleChartAIManager aiManager = new CandleChartAIManager(loadedCandles, chart);
                aiManager.analyzeCandles();

                List<Double> features = InvestProFeatureExtractor.extractFeatures(loadedCandles);

                AIPredictorClient aiPredictorClient = new AIPredictorClient(
                        "localhost", 50051
                );

                LineChart<Number, Number> equity = new LineChart<>(xAxis, yAxis);
                InvestProAIPaperTradingBot bot = new InvestProAIPaperTradingBot(getTelegram(), equity);
                bot.onNewCandle(candleData);

                InvestProAIAutotrader investProAIAutotrader = new InvestProAIAutotrader(chart);
                investProAIAutotrader.onNewCandle(candleData.getLast());

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
                CompletableFuture<Optional<CandleData>> inProgressCandleDataOptionalFuture = exchange
                        .fetchCandleDataForInProgressCandle(tradePair, Instant.ofEpochSecond(
                                        candleData.getLast().getOpenTime() + secondsPerCandle),
                                secondsIntoCurrentCandle, secondsPerCandle);
                inProgressCandleDataOptionalFuture.whenComplete((inProgressCandleDataOptional, throwable) -> {
                    if (throwable == null) {
                        if (inProgressCandleDataOptional.isPresent()) {
                            CandleData inProgressCandleData = inProgressCandleDataOptional.get();

                            // Our second attempt to get caught up requests all trades that have happened since
                            // the time of the last sub-candle (the 9 seconds long candles from above). This will
                            // get us caught up to the current time. The reason we don't use the more simple
                            // approach of requesting all the trades that have happened in the current candle to
                            // begin with is that this can take a prohibitively long time if the candle duration
                            // is too large. (Some exchanges have multiple trades every second.)
                            int currentTill = (int) Instant.now().getEpochSecond();
                            CompletableFuture<Trade> tradesFuture;
                            tradesFuture = exchange.fetchRecentTrades(
                                    tradePair, Instant.ofEpochSecond(inProgressCandleData.getOpenTime()));

                            tradesFuture.whenComplete((trades, exception) -> {
                                if (exception == null) {
                                    inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
                                    inProgressCandle.setCloseTime(currentTill);

                                    if (trades.getPrice() == 0) {
                                        // No trading activity happened in addition to the sub-candles from above.
                                        inProgressCandle.setHighPriceSoFar(
                                                inProgressCandleData.getHighPrice());
                                        inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPrice());
                                        inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolume());
                                        inProgressCandle.setLastPrice(inProgressCandleData.getClosePrice());
                                    } else {
                                        // We need to factor in the trades that have happened after the
                                        // "currentTill" time of the in-progress candle.
                                        inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPrice());
                                        inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPrice());
                                        inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolume() +
                                                trades.getAmount());
                                        inProgressCandle.setLastPrice(trades.getPrice());
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

                            Platform.runLater(() -> setInitialState(candleData));
                        }
                    } else {
                        logger.error("error fetching in-progress candle data: ", throwable);
                    }
                });
            } else {
                setInitialState(candleData);
            }

            int slidingWindowSize = currZoomLevel.getNumVisibleCandles();

            // To compute the y-axis extrema for the new data in the page, we have to include the
            // first numVisibleCandles from the previous page (otherwise the sliding window will not be able
            // to reach all the way).
            TreeMap<Long, CandleData> extremaData = new TreeMap<>(data.subMap((long) currZoomLevel.getMinXValue(),
                    (long) (currZoomLevel.getMinXValue() + ((long) currZoomLevel.getNumVisibleCandles() *
                            secondsPerCandle))));
            List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
            newDataPlusOffset.addAll(extremaData.values());
            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset,
                    slidingWindowSize);
            data.putAll(candleData.stream().collect(Collectors.toMap((m -> (long) m.getOpenTime()),
                    Function.identity())));
            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
        }

    }

    /**
     * Pages new candle data in chronological order to a {@code CandleStickChart} on-demand.
     *
     * @author <a href="mailto: nguemechieu@live.com">nguem</a>
     */
    public class CandleDataPager {

        private static final Logger logger = LoggerFactory.getLogger(CandleDataPager.class);
        CandleDataPreProcessor candleDataPreProcessor;

        public CandleDataPager(CandleStickChart chart) {


            this.candleDataPreProcessor = new CandleDataPreProcessor(chart);
        }

        public Consumer<Future<List<CandleData>>> getCandleDataPreProcessor() {
            return candleDataPreProcessor;
        }

        private final class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
            final CandleStickChart candleStickChart;
            List<CandleData> candleData = new ArrayList<>();
            private boolean hitFirstNonPlaceHolder;

            CandleDataPreProcessor(CandleStickChart candleStickChart) {
                this.candleStickChart = candleStickChart;
            }

            @Override
            public void accept(@NotNull Future<List<CandleData>> futureCandleData) {

                try {
                    candleData = futureCandleData.get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("exception during accepting futureCandleData: ", ex);
                    return;
                }

                if (!candleData.isEmpty()) {
                    if (hitFirstNonPlaceHolder) {
                        getCandlePageConsumer().accept(candleData);
                    } else {
                        int count = 0;
                        while (candleData.get(count).isPlaceHolder()) {
                            count++;
                            if (count == candleData.size()) {
                                logger.info("No non-placeholder candles found in the data");

                                new Messages(Alert.AlertType.WARNING,
                                        "No non-placeholder candles found in the data"
                                );
                                break;
                            }
                        }
                        List<CandleData> nonPlaceHolders = candleData.subList(count, candleData.size());
                        if (!nonPlaceHolders.isEmpty()) {
                            hitFirstNonPlaceHolder = true;
                            getCandlePageConsumer().accept(nonPlaceHolders);
                        }

                    }
                }
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
}