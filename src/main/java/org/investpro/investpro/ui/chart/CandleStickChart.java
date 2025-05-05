package org.investpro.investpro.ui.chart;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
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
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
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
import javafx.scene.text.*;
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
import org.investpro.investpro.ai.InvestProAIAutotrader;
import org.investpro.investpro.ai.InvestProAIBacktester;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.InProgressCandle;
import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.CandleStickChartToolbar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.investpro.CandleStickChartUtils.getXAxisFormatterForRange;
import static org.investpro.investpro.CandleStickChartUtils.putSlidingWindowExtrema;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.LEFT;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.RIGHT;

@Getter
@Setter
public class CandleStickChart extends Region {

    public static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
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
    public static StableTicksAxis xAxis = new StableTicksAxis();
    private static CandleStickChartOptions chartOptions = new CandleStickChartOptions();
    private static boolean showGrid;
    private static Map<Long, ZoomLevel> zoomLevelMap = Map.of();
    public static volatile ZoomLevel currZoomLevel;
    private static StableTicksAxis yAxis = new StableTicksAxis();
    private static StableTicksAxis extraAxis = new StableTicksAxis();
    @Getter
    private static Canvas canvas;
    private static GraphicsContext graphicsContext;
    private static int candleWidth = 10;
    private static NavigableMap<Long, CandleData> data = Collections.synchronizedNavigableMap(new TreeMap<>(Long::compare));
    private static int inProgressCandleLastDraw = -1;
    static Font canvasNumberFont = new Font(14);
    private static volatile boolean paging;
    private static double chartWidth = 1300;
    private static double chartHeight = 700;
    private final Exchange exchange;
    private final TradePair tradePair;
    private final CandlePageConsumer candlePageConsumer;
    private final SimpleDoubleProperty widthProperty;
    private final InvestProAIAutotrader ai;
    Double[] bid_ask;
    IndicatorManager indicatorManager;
    private ScheduledExecutorService updateInProgressCandleExecutor;
    private UpdateInProgressCandleTask updateInProgressCandleTask;
    private Line extraAxisExtension;
    private EventHandler<MouseEvent> mouseDraggedHandler;
    private EventHandler<ScrollEvent> scrollHandler;
    private EventHandler<KeyEvent> keyHandler;
    CandleDataSupplier candleDataSupplier;

    List<Trade> currentCandleTrades;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;

    private ChartOverlayManager overlayManager;
    private List<CandleData> candleData;
    private double startIndex;
    private String tokens;
    private int verticalGridLines = 10;
    private int horizontalGridLines = 5;
    private int scrollStep = 1; // finer than a navigation step
    private AnchorPane drawingLayer;
    private EventHandler<MouseEvent> mousePressedHandler;

    private EventHandler<MouseEvent> mouseReleasedHandler;

    private double dragStartX;
    private boolean isDragging = false;
    private Line bidLine;
    private Line askLine;
    private Label bidLabel;
    private Label askLabel;
    private Line verticalCrosshair;
    private Line horizontalCrosshair;
    private Label priceLabel;
    private Label timeLabel;
    public CandleStickChart(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier,
                            boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                            ObservableNumberValue containerHeight, String token) throws IOException {

        Objects.requireNonNull(exchange, "Exchange must not be null");
        Objects.requireNonNull(candleDataSupplier, "CandleDataSupplier must not be null");
        Objects.requireNonNull(containerWidth, "Container width must not be null");
        Objects.requireNonNull(containerHeight, "Container height must not be null");

        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("Must be constructed on JavaFX Application Thread, was: " + Thread.currentThread());
        }

        logger.debug("Initializing CandleStickChart for {}", tradePair);

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.tokens = token;
        this.candleDataSupplier = candleDataSupplier;
        this.mousePressedHandler = new MousePressedHandler();
        this.scrollHandler = new ScrollEventHandler();
        this.mouseDraggedHandler = new MouseDraggedHandler();
        this.keyHandler = new KeyEventHandler();

        CandleStickChart.secondsPerCandle = secondsPerCandle;
        CandleStickChart.liveSyncing = liveSyncing;

        chartOptions = new CandleStickChartOptions();
        zoomLevelMap = new ConcurrentHashMap<>();
        widthProperty = new SimpleDoubleProperty(containerWidth.doubleValue());
        currZoomLevel = new ZoomLevel(this, 0, candleWidth, secondsPerCandle, widthProperty,
                InstantAxisFormatter.of(DateTimeFormatter.BASIC_ISO_DATE), 10);
        progressIndicator = new ProgressIndicator(-1);
        progressIndicator.setPrefSize(40, 40);
        progressIndicator.setMouseTransparent(true);

        candlePageConsumer = new CandlePageConsumer(this);
        candleDataPager = new CandleDataPager(this);

        chartOptions.horizontalGridLinesVisibleProperty().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents(true));

        setupAxes(tradePair);
        setupCanvasAndLayout(containerWidth, containerHeight);
        setupAsyncTasks();
        getChildren().addAll(
                xAxis, yAxis, extraAxis, extraAxisExtension);
        ai = new InvestProAIAutotrader(this);

        // updateBidAskLines(bid_ask[0], bid_ask[1]);
    }

    /**
     * Moves the x-axis bounds by exactly one full candle duration.
     * Positive deltaX shifts right, negative shifts left.
     *
     * @param deltaX must be 1 or -1; controls the direction of movement
     */
    private static void setAxisBoundsForMove(int deltaX) {
        if (deltaX != 1 && deltaX != -1) {
            throw new IllegalArgumentException(String.format("deltaX must be 1 or -1, but was: %d", deltaX));
        }

        double shift = deltaX * secondsPerCandle;
        xAxis.setLowerBound(xAxis.getLowerBound() + shift);
        xAxis.setUpperBound(xAxis.getUpperBound() + shift);
    }

    public static void putExtremaForRemainingElements(
            Map<Integer, Pair<Extrema, Extrema>> extrema,
            final List<CandleData> candleData
    ) {
        Objects.requireNonNull(extrema, "extrema map must not be null");
        Objects.requireNonNull(candleData, "candleData list must not be null");

        if (candleData.isEmpty()) {
            throw new IllegalArgumentException("candleData must not be empty");
        }

        double minVolume = Double.MAX_VALUE;
        double maxVolume = -Double.MAX_VALUE;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = -Double.MAX_VALUE;

        for (int i = candleData.size() - 1; i >= 0; i--) {
            CandleData candle = candleData.get(i);

            minVolume = Math.min(candle.getVolume(), minVolume);
            maxVolume = Math.max(candle.getVolume(), maxVolume);

            minPrice = Math.min(candle.getLowPrice(), minPrice);
            maxPrice = Math.max(candle.getHighPrice(), maxPrice);

            // Store extrema for this open time (volumeExtrema, priceExtrema)
            extrema.put(candle.getOpenTime(), new Pair<>(
                    new Extrema(minVolume, maxVolume),
                    new Extrema(minPrice, maxPrice)
            ));
        }
    }

    /**
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private static void setYAndExtraAxisBounds() {
        int xLowerBound = (int) xAxis.getLowerBound();
        logger.info("xAxis lower bound: {}", xLowerBound);

        final double idealBufferSpaceMultiplier = 0.35;

        if (!currZoomLevel.getExtremaForCandleRangeMap().containsKey(xLowerBound)) {
            // This could happen during zoom transitions or async data loading
            logger.warn("Extrema map does not contain entry for x-value: {}. Full extrema map: {}", xLowerBound, new TreeMap<>(currZoomLevel.getExtremaForCandleRangeMap()));
            return;
        }

        int extremaKey = xLowerBound - secondsPerCandle;
        Pair<Extrema, Extrema> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(extremaKey);

        if (extremaForRange == null) {
            logger.error("Extrema for key {} (offset by -{}) is null!", extremaKey, secondsPerCandle);
            return;
        }

        Pair<Extrema, Extrema> extrema = currZoomLevel.getExtremaForCandleRangeMap().get((int) xAxis.getLowerBound());

        Extrema yExtrema = extremaForRange.getValue();
        if (extrema == null) {
            logger.warn("Extrema map does not contain entry for x-value: {}", (long) xAxis.getLowerBound());
            // optionally use previous known extrema or default
            return;
        }

        double yAxisMin = yExtrema.getMin();
        double yAxisMax = yExtrema.getMax();
        double yAxisDelta = yAxisMax - yAxisMin;

        double paddedMin = yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier);
        double paddedMax = yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier);

        yAxis.setLowerBound(paddedMin);
        yAxis.setUpperBound(paddedMax);

        extraAxis.setLowerBound(paddedMin);
        extraAxis.setUpperBound(paddedMax);

        logger.info(String.format("Set y-axis bounds: [%.2f, %.2f]", paddedMin, paddedMax));
    }

    private static void drawOHLCVLabel(CandleData candle, double x, double y) {
        String text = String.format("Time: %s | O: %.2f H: %.2f L: %.2f C: %.2f V: %.2f",
                Instant.ofEpochSecond(candle.getOpenTime()),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getVolume());

        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        graphicsContext.fillText(text, x, y);
    }

    private static void drawPlaceholderCandle(@NotNull CandleData candleDatum, int candleIndex, double lastClose, double pixelsPerMonetaryUnit) {
        double candleOpenPrice = candleDatum.getOpenPrice();
        if (chartOptions.isAlignOpenClose() && lastClose != -1) {
            candleOpenPrice = lastClose;
        }

        double candleYOrigin = cartesianToScreenCoords((candleOpenPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double candleX = canvas.getWidth() - (candleIndex * candleWidth);

        graphicsContext.beginPath();
        graphicsContext.moveTo(candleX + 1, candleYOrigin);
        graphicsContext.rect(candleX, candleYOrigin, candleWidth - 1, 1);

        graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
        graphicsContext.fill();

        graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
        graphicsContext.setLineWidth(1);
        graphicsContext.stroke();

        // Draw OHLCV label
        drawOHLCVLabel(candleDatum, candleX + 5, candleYOrigin - 10); // shift label slightly right and above
    }

    private void setupCanvasAndLayout(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight) {
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);

        ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        // Initial placeholder canvas, will be resized after size is known
        canvas = new Canvas(containerWidth.doubleValue() - 100, containerHeight.doubleValue());
        graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.strokeRect(0, 0, containerWidth.doubleValue() - 100, containerHeight.doubleValue() - 100);
        setupCrosshairOverlay();
        // Setup loading indicator
        Text loadingText = new Text("Loading...");
        loadingText.setFont(Font.font(FXUtils.getMonospacedFont(), 14));
        loadingText.setFill(Color.WHITE);
        loadingText.setStroke(Color.GREEN);
        loadingText.setMouseTransparent(true);

        progressIndicator.setMouseTransparent(true);
        VBox loadingIndicatorContainer = new VBox(progressIndicator, loadingText);
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setPadding(new Insets(20));
        loadingIndicatorContainer.setMouseTransparent(true);

        StackPane chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
        chartStackPane.setTranslateX(64); // Optional offset, can be removed if not needed
        getChildren().addFirst(chartStackPane);

        // Wait until final size is known to layout chart properly
        ChangeListener<? super Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                gotFirstSize.removeListener(this);

                double containerW = containerWidth.getValue().doubleValue();
                double containerH = containerHeight.getValue().doubleValue();

                int visibleCandles = (int) Math.floor(containerW / candleWidth);
                chartWidth = visibleCandles * candleWidth - 60 + (candleWidth / 2.0);
                chartHeight = containerH;

                canvas.setWidth(chartWidth - 100);
                canvas.setHeight(chartHeight - 200);

                canvas.setLayoutX((containerW - chartWidth) / 2);
                canvas.setLayoutY((containerH - chartHeight) / 2);

                canvas.widthProperty().addListener(sizeListener);
                canvas.heightProperty().addListener(sizeListener);

                // Axis positioning
                extraAxisExtension.setTranslateX(-chartWidth + 107.8);  // 37.8 + 70
                extraAxis.setTranslateX(-chartWidth + 47.8);            // 37.8 + 10
                yAxis.setTranslateX(chartWidth - 37.8);

                // Exchange message label
                Label exchangeMessage = new Label(exchange.getExchangeMessage());
                exchangeMessage.setLayoutX(10);
                exchangeMessage.setLayoutY(10);
                exchangeMessage.setTranslateX(-chartWidth + 37.8);
                exchangeMessage.setTranslateY((chartHeight - 37.8) / 2);
                getChildren().add(exchangeMessage);

                layoutChart();
                initializeEventHandlers();

                CompletableFuture
                        .supplyAsync(candleDataSupplier)
                        .thenAccept(candleDataPager.getCandleDataPreProcessor())
                        .exceptionally(e -> {
                            logger.error("Failed to load initial candle data", e);
                            return null;
                        });
            }
        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);
    }

    private void setupAxes(@NotNull TradePair tradePair) {
        xAxis = new StableTicksAxis();
        canvas = new Canvas(1530, 780);

        xAxis.setTranslateY(getHeight() - 100);
        xAxis.setPrefSize(canvas.getWidth(), 20);
        yAxis = new StableTicksAxis();
        yAxis.setPrefSize(100, canvas.getHeight());

        yAxis.setTranslateY(-100);
        extraAxis = new StableTicksAxis();

        extraAxis.setTranslateY(-100);


        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 14);
        Paint axisColor = Color.rgb(95, 195, 195); // For extra axis extension lines

        // General properties
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        extraAxis.setAutoRanging(false);

        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(true);
        extraAxis.setForceZeroInRange(false);

        xAxis.setSide(Side.TOP);
        yAxis.setSide(Side.RIGHT);
        extraAxis.setSide(Side.LEFT);

        // Formatters
        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));

        // Fonts
        xAxis.setTickLabelFont(axisFont);
        yAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);

        // Extra axis visual extension (usually for volume lines)
        extraAxisExtension = new Line();
        extraAxisExtension.setStartX(0);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndX(0);
        extraAxisExtension.setEndY(chartHeight - 100); // This may be reset later based on container size

        extraAxisExtension.setFill(axisColor);
        extraAxisExtension.setStroke(axisColor);
        extraAxisExtension.setStrokeWidth(2);
        extraAxisExtension.setSmooth(false);
    }

    private static double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    private void setupAsyncTasks() {
        updateInProgressCandleTask = new UpdateInProgressCandleTask();
        updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));

        CompletableFuture.supplyAsync(candleDataSupplier)
                .thenApply(supplier -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                })
                .thenAccept(data -> {
                    this.candleData = data;
                    updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
                    logger.info("Initialized candle data and scheduled update task");
                })
                .exceptionally(e -> {
                    logger.error("Failed to load initial candle data", e);
                    return null;
                });


    }

    private Line createPriceLine(Color color) {
        Line line = new Line(0, 0, chartWidth, 0);
        line.setStroke(color);
        line.setStrokeWidth(1);
        line.setVisible(true);
        line.setMouseTransparent(true);
        return line;
    }

    private Label createPriceLabel(Color color) {
        Label label = new Label();
        label.setFont(Font.font("Monospaced", 12));
        label.setStyle("-fx-background-color: black; -fx-padding: 2;");
        label.setTextFill(color);
        label.setVisible(true);
        label.setMouseTransparent(true);
        return label;
    }

    private double mapPriceToY(double price) {
        double range = yAxis.getUpperBound() - yAxis.getLowerBound();
        return ((yAxis.getUpperBound() - price) / range) * chartHeight;
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

    private void updateBidAskLines(double bidPrice, double askPrice) {
        double bidY = mapPriceToY(bidPrice);
        double askY = mapPriceToY(askPrice);

        bidLine.setStartY(bidY);
        bidLine.setEndY(bidY);
        askLine.setStartY(askY);
        askLine.setEndY(askY);

        bidLabel.setText(String.format("Bid: %.2f", bidPrice));
        askLabel.setText(String.format("Ask: %.2f", askPrice));

        bidLabel.setLayoutX(chartWidth - 70);
        bidLabel.setLayoutY(bidY - 10);

        askLabel.setLayoutX(chartWidth - 70);
        askLabel.setLayoutY(askY - 10);
    }

    private void moveAlongX(int deltaX, boolean skipDraw) {
        if (deltaX != 1 && deltaX != -1) {
            throw new IllegalArgumentException(String.format("deltaX must be 1 or -1 but was: %d", deltaX));
        }

        CompletableFuture<Boolean> progressIndicatorVisibleFuture = new CompletableFuture<>();
        Platform.runLater(() -> progressIndicatorVisibleFuture.complete(progressIndicator.isVisible()));

        progressIndicatorVisibleFuture.thenAccept(progressIndicatorVisible -> {
            if (progressIndicatorVisible) return;

            int desiredXLowerBound = (int) xAxis.getLowerBound() + (deltaX * secondsPerCandle);
            int latestTime = data.lastEntry().getValue().getOpenTime();
            int minCandlesRemaining = 3;
            int maxBound = latestTime - (minCandlesRemaining - 1) * secondsPerCandle;

            boolean needsPaging = desiredXLowerBound <= currZoomLevel.getMinXValue();

            Runnable chartUpdater = () -> {
                setAxisBoundsForMove(deltaX);
                setYAndExtraAxisBounds();
                if (!skipDraw) {
                    drawChartContents(true);
                }
            };

            if (desiredXLowerBound <= maxBound) {
                if (needsPaging) {
                    paging = true;
                    Platform.runLater(() -> progressIndicator.setVisible(true));
                    CompletableFuture
                            .supplyAsync(candleDataSupplier)
                            .thenAccept(candleDataPager.getCandleDataPreProcessor())
                            .whenComplete((_, throwable) -> Platform.runLater(() -> {
                                if (throwable != null) {
                                    logger.error("Failed to fetch/paginate candles during moveAlongX", throwable);
                                    progressIndicator.setVisible(false);
                                    paging = false;
                                    return;
                                }

                                chartUpdater.run();
                                progressIndicator.setVisible(false);
                                paging = false;
                            }));
                } else {
                    Platform.runLater(chartUpdater);
                }
            }
        });
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

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis bounds.
     */
    public void drawChartContents(boolean clearCanvas) {
        int numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);

        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            if (xAxis.getUpperBound() >= inProgressCandleLastDraw &&
                    xAxis.getUpperBound() < inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)) {
                if (numCandlesToSkip == 0) {
                    moveAlongX(1, true);
                    numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);
                }
            }
            inProgressCandleLastDraw = inProgressCandle.getOpenTime();
        }

        if (clearCanvas) {
            graphicsContext.setFill(Color.rgb(15, 15, 15)); // dark TradingView background
            graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 150);
        }

        double monetaryUnitsPerPixel = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        double pixelsPerMonetaryUnit = 1d / monetaryUnitsPerPixel;

        NavigableMap<Long, CandleData> candlesToDraw = data.subMap(
                (long) ((xAxis.getUpperBound() - secondsPerCandle) - ((long) (currZoomLevel.getNumVisibleCandles()) * secondsPerCandle)), true,
                (long) ((xAxis.getUpperBound() - secondsPerCandle) - ((long) numCandlesToSkip * secondsPerCandle)), true
        );

        graphicsContext.setFill(Color.WHITE);
        graphicsContext.fillText(tradePair.toString('/'), chartWidth / 2, chartHeight / 2);
        drawOHLCVLabel(candlesToDraw.values().stream().toList().getLast(), 300, 24);



        logger.info("Drawing {} candles.", candlesToDraw.size());

        // Grid lines
        if (!chartOptions.isHorizontalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(80, 80, 80, 0.3));
                graphicsContext.setLineWidth(1);
                graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
            }
        }

        if (!chartOptions.isVerticalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(80, 80, 80, 0.2));
                graphicsContext.setLineWidth(1);
                graphicsContext.strokeLine(tickMark.getPosition(), 0, tickMark.getPosition(), canvas.getHeight());
            }
        }

        int candleIndex = numCandlesToSkip;
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        int highIndex = -1;
        int lowIndex = -1;
        int volumeMaxHeight = 150;
        double volumeScale = volumeMaxHeight / extraAxis.getUpperBound();
        double lastClose = -1;

        for (CandleData candle : candlesToDraw.descendingMap().values()) {
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
                if (candle.getHighPrice() > highest) {
                    highest = candle.getHighPrice();
                    highIndex = candleIndex;
                }
                if (candle.getLowPrice() < lowest) {
                    lowest = candle.getLowPrice();
                    lowIndex = candleIndex;
                }
            }

            if (candle.isPlaceHolder()) {
                drawPlaceholderCandle(candle, candleIndex, lastClose, pixelsPerMonetaryUnit);
            } else {
                drawTradingViewCandle(candle, candleIndex, pixelsPerMonetaryUnit);
                drawVolumeBar(candle, candleIndex, volumeScale);
            }

            lastClose = candle.getClosePrice();

            candleIndex++;

        }

        drawHighLowMarkers(highest, lowest, highIndex, lowIndex, pixelsPerMonetaryUnit);
    }

    private void drawVolumeBar(CandleData candle, int index, double volumeScale) {
        double volume = candle.getVolume();
        double barHeight = volume * volumeScale;
        double x = index * candleWidth;
        double y = canvas.getHeight() - barHeight;

        Color barColor = candle.getClosePrice() >= candle.getOpenPrice() ? Color.LIMEGREEN : Color.RED;

        graphicsContext.setFill(barColor.deriveColor(1, 1, 1, 0.5));
        graphicsContext.fillRect(x, y, candleWidth, barHeight);
    }

    private void drawTradingViewCandle(CandleData candle, int index, double pixelsPerUnit) {
        double x = index * candleWidth;
        double openY = yAxis.getDisplayPosition(candle.getOpenPrice());
        double closeY = yAxis.getDisplayPosition(candle.getClosePrice());
        double highY = yAxis.getDisplayPosition(candle.getHighPrice());
        double lowY = yAxis.getDisplayPosition(candle.getLowPrice());

        boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();

        Color bodyColor = isBullish ? Color.LIMEGREEN : Color.RED;
        Color wickColor = Color.LIGHTGRAY;

        double candleBodyTop = Math.min(openY, closeY);
        double candleBodyHeight = Math.abs(openY - closeY);

        // Wick
        graphicsContext.setStroke(wickColor);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeLine(x + (double) candleWidth / 2, highY, x + (double) candleWidth / 2, lowY);

        // Body
        graphicsContext.setFill(bodyColor);
        if (candleBodyHeight < 1) {
            graphicsContext.fillRect(x, candleBodyTop, candleWidth, 1); // flat candle
        } else {
            graphicsContext.fillRect(x, candleBodyTop, candleWidth, candleBodyHeight);
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

    public void changeZoom(ZoomDirection zoomDirection) {
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
            ZoomLevel newZoomLevel = new ZoomLevel(this, nextZoomLevelId, newCandleWidth, secondsPerCandle,
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
                    List<CandleData> candleData = new ArrayList<>(data.values().stream().sorted().toList());
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
            currZoomLevel = zoomLevelMap.get((long) nextZoomLevelId);
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

            mousePressedHandler = event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    dragStartX = event.getSceneX();
                    isDragging = true;
                    event.consume();
                }
            };

            mouseDraggedHandler = event -> {
                if (isDragging && event.getButton() == MouseButton.PRIMARY) {
                    double dragDistance = event.getSceneX() - dragStartX;
                    int candleSteps = (int) (dragDistance / candleWidth); // how many candles to pan

                    if (candleSteps != 0) {
                        moveAlongX(-candleSteps, true); // negative because drag left = move right
                        dragStartX = event.getSceneX(); // reset for next delta
                    }
                    event.consume();
                }
            };

            mouseReleasedHandler = event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    isDragging = false;
                    event.consume();
                }
            };

            // Bind handlers to canvas
            canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
            canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);

            canvas.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (event.isControlDown()) {
                    // Optional: future vertical zoom
                    event.consume();
                    return;
                }

                if (event.getDeltaY() < 0) {
                    zoomOut();
                } else {
                    zoomIn();
                }

                event.consume();
            });

        }
    }

    private void zoomIn() {
        currZoomLevel.zoomIn();
        drawChartContents(true);
    }

    private void zoomOut() {
        currZoomLevel.zoomOut();
        drawChartContents(true);
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

    private void setInitialState(List<CandleData> candleData) {
        if (liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(this, 0, candleWidth, secondsPerCandle, canvas.widthProperty(),
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
            logger.info("Chart snapshot saved at: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private double mapYToPrice(double y) {
        double priceRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        return yAxis.getUpperBound() - ((y / chartHeight) * priceRange);
    }

    private long mapXToEpoch(double x) {
        double timeRange = xAxis.getUpperBound() - xAxis.getLowerBound();
        return (long) (xAxis.getLowerBound() + (x / chartWidth) * timeRange);
    }

    private void hideCrosshair() {
        verticalCrosshair.setVisible(false);
        horizontalCrosshair.setVisible(false);
        priceLabel.setVisible(false);
        timeLabel.setVisible(false);
    }

    private void updateCrosshair(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        verticalCrosshair.setStartX(mouseX);
        verticalCrosshair.setEndX(mouseX);
        verticalCrosshair.setStartY(0);
        verticalCrosshair.setEndY(chartHeight);

        horizontalCrosshair.setStartX(0);
        horizontalCrosshair.setEndX(chartWidth);
        horizontalCrosshair.setStartY(mouseY);
        horizontalCrosshair.setEndY(mouseY);

        verticalCrosshair.setVisible(true);
        horizontalCrosshair.setVisible(true);

        double price = mapYToPrice(mouseY);  // You must implement this mapping
        long timestamp = mapXToEpoch(mouseX); // You must implement this mapping

        priceLabel.setText(String.format("%.2f", price));
        priceLabel.setLayoutX(chartWidth - 60);  // place on right side
        priceLabel.setLayoutY(mouseY - 10);

        timeLabel.setText(Instant.ofEpochSecond(timestamp).toString());
        timeLabel.setLayoutX(mouseX - 50);
        timeLabel.setLayoutY(chartHeight - 20);

        priceLabel.setVisible(true);
        timeLabel.setVisible(true);
    }

    private void setupCrosshairOverlay() {
        verticalCrosshair = new Line();
        horizontalCrosshair = new Line();
        priceLabel = new Label();
        timeLabel = new Label();

        verticalCrosshair.setStroke(Color.LIGHTGRAY);
        verticalCrosshair.setStrokeWidth(1);
        verticalCrosshair.setMouseTransparent(true);

        horizontalCrosshair.setStroke(Color.LIGHTGRAY);
        horizontalCrosshair.setStrokeWidth(1);
        horizontalCrosshair.setMouseTransparent(true);

        priceLabel.setFont(Font.font("Monospaced", 12));
        priceLabel.setTextFill(Color.WHITE);
        priceLabel.setStyle("-fx-background-color: black; -fx-padding: 2;");

        timeLabel.setFont(Font.font("Monospaced", 12));
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setStyle("-fx-background-color: black; -fx-padding: 2;");

        getChildren().addAll(verticalCrosshair, horizontalCrosshair, priceLabel, timeLabel);

        canvas.setOnMouseMoved(this::updateCrosshair);
        canvas.setOnMouseExited(_ -> hideCrosshair());

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
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                }
                inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());

                data.put((long) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());


            }

            // Trigger AI operations
            CandleData latest = candleData.getLast();

            ai.onNewCandle(latest);

            new InvestProAIBacktester().runBacktest(candleData);



            //Execute AI Training and Trade Analysis

            drawChartContents(false);




        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        private final CandleStickChart chart;
        ChartAlertsManager alertsManager;

        public CandlePageConsumer(CandleStickChart chart) {
            this.chart = chart;
            this.alertsManager = new ChartAlertsManager(chart, new AnchorPane(), candleData);
        }

        @Override
        public void accept(List<CandleData> candleData) {
            if (Platform.isFxApplicationThread()) {
                throw new IllegalStateException("Candle data paging must not happen on FX thread!");
            }

            if (candleData == null || candleData.isEmpty()) {
                logger.warn("CandleData was empty");
                Platform.runLater(() -> {
                    Label marketClosedLabel = new Label("Market is closed. No new data available.");
                    marketClosedLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 16px;");
                    getChildren().setAll(marketClosedLabel);
                });
                return;
            }

            if (candleData.size() < 2 || candleData.get(0).getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Candle data must be at least size 2 and sorted ascending by openTime");
            }


            long lastCandleOpenTime = candleData.getLast().getOpenTime();
            long secondsIntoCurrentCandle = Instant.now().getEpochSecond() - (lastCandleOpenTime + secondsPerCandle);
            inProgressCandle.setOpenTime((int) (lastCandleOpenTime + secondsPerCandle));

            CompletableFuture<List<Optional<?>>> future = exchange.fetchCandleDataForInProgressCandle(
                    tradePair,
                    Instant.ofEpochSecond(inProgressCandle.getOpenTime()),
                    secondsIntoCurrentCandle,
                    secondsPerCandle
            );

            if (future == null) {
                logger.error("fetchCandleDataForInProgressCandle returned null");
                Platform.runLater(() -> setInitialState(candleData));
                return;
            }

            future.whenComplete((optionalCandle, throwable) -> {
                if (throwable != null) {
                    logger.error("Error fetching in-progress candle data: ", throwable);
                    return;
                }

                if (optionalCandle.isEmpty()) {
                    logger.info("No in-progress candle data available");
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setVolumeSoFar(0);
                    Platform.runLater(() -> setInitialState(candleData));
                    return;
                }


                int currentTill = (int) Instant.now().getEpochSecond();
                CandleData inProgressCandleData = candleData.getFirst(); // first in list

                exchange.getOrderBook(tradePair, Instant.ofEpochSecond(inProgressCandleData.getOpenTime()))
                        .whenComplete((trades, exception) -> {
                            if (exception != null) {
                                logger.error("Error fetching order book", exception);
                                return;
                            }

                            inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
                            inProgressCandle.setCloseTime(currentTill);

                            if (trades.isEmpty() || trades.getLast().getBidEntries().isEmpty()) {
                                inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPrice());
                                inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPrice());
                                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolume());
                                inProgressCandle.setLastPrice(inProgressCandleData.getClosePrice());
                            } else {
                                var lastBid = trades.getLast().getBidEntries().getLast();
                                inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPrice());
                                inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPrice());
                                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolume() + lastBid.getSize());
                                inProgressCandle.setLastPrice(lastBid.getPrice());
                            }

                            Platform.runLater(() -> setInitialState(candleData));
                        });
            });

            // Calculate extrema within sliding window range
            long from = (long) currZoomLevel.getMinXValue();
            long to = from + ((long) currZoomLevel.getNumVisibleCandles() * secondsPerCandle);

            TreeMap<Long, CandleData> extremaData = new TreeMap<>(data.subMap(from, to));

            List<CandleData> combinedData = new ArrayList<>(candleData);
            combinedData.addAll(extremaData.values());

            int slidingWindowSize = currZoomLevel.getNumVisibleCandles();
            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), combinedData, slidingWindowSize);

            // Update final candle data in internal map
            data.putAll(candleData.stream().collect(Collectors.toMap(
                    candle -> (long) candle.getOpenTime(),
                    Function.identity()
            )));

            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
        }
    }

    /**
     * Pages new candle data in chronological order to a {@code CandleStickChart} on-demand.
     *
     * @author <a href="mailto: nguemechieu@live.com">nguem</a>
     */
    @Getter
    @Setter
    public class CandleDataPager {

        private static final Logger logger = LoggerFactory.getLogger(CandleDataPager.class);
        private CandleDataPreProcessor candleDataPreProcessor;
        private List<CandleData> candleData0;

        public CandleDataPager(CandleStickChart chart) {


            this.candleDataPreProcessor = new CandleDataPreProcessor(chart);
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
                    candleData0 = futureCandleData.get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("exception during accepting futureCandleData: ", ex);
                    return;
                }

                if (!candleData0.isEmpty()) {
                    if (hitFirstNonPlaceHolder) {
                        getCandlePageConsumer().accept(candleData0);
                    } else {
                        int count = 0;
                        while (candleData0.get(count).isPlaceHolder()) {
                            count++;
                            if (count == candleData0.size()) {
                                logger.info("No non-placeholder candles found in the data");

                                new Messages(Alert.AlertType.WARNING,
                                        "No non-placeholder candles found in the data"
                                );
                                break;
                            }
                        }
                        List<CandleData> nonPlaceHolders = candleData0.subList(count, candleData0.size());
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


    private class MousePressedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(@NotNull MouseEvent event) {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragStartX = event.getSceneX();
                isDragging = true;

                // Optional: could capture the initial minXValue for smooth repositioning
                logger.debug("Mouse pressed at X = {}", dragStartX);
            }
        }
    }

}