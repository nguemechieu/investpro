package org.investpro.investpro.ui.chart;

import javafx.animation.PauseTransition;
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
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.investpro.investpro.*;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.ai.InvestProAIAutotrader;
import org.investpro.investpro.ai.InvestProAIBacktester;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.InProgressCandle;
import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.investpro.CandleStickChartUtils.getXAxisFormatterForRange;
import static org.investpro.investpro.CandleStickChartUtils.putSlidingWindowExtrema;

@Getter
@Setter
public class CandleStickChart extends Region {

    public static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    public static final Paint PLACE_HOLDER_FILL_COLOR = Color.rgb(189, 189, 189, 0.7);
    public static final Paint PLACE_HOLDER_BORDER_COLOR = Color.rgb(204, 204, 204, 0.7);
    public static final Paint AXIS_TICK_LABEL_COLOR = Color.rgb(234, 154, 17);
    private static final DecimalFormat MARKER_FORMAT = new DecimalFormat("#.000000000");
    private static final DateTimeFormatter CROSSHAIR_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d HH:mm");
    private static final Color CHART_BACKGROUND_TOP = Color.web("#07131f");
    private static final Color CHART_BACKGROUND_BOTTOM = Color.web("#10263b");
    private static final Color CHART_PANEL_FILL = Color.web("#08121d", 0.9);
    private static final Color CHART_PANEL_BORDER = Color.web("#334155", 0.9);
    private static final Color CHART_TEXT_PRIMARY = Color.web("#e2e8f0");
    private static final Color CHART_TEXT_MUTED = Color.web("#94a3b8");
    private static final Color CHART_ACCENT = Color.web("#38bdf8");
    private static final Color GRID_MAJOR = Color.web("#94a3b8", 0.16);
    private static final Color GRID_MINOR = Color.web("#64748b", 0.12);
    private static final Color BULL_BODY = Color.web("#22c55e");
    private static final Color BULL_EDGE = Color.web("#86efac");
    private static final Color BEAR_BODY = Color.web("#ef4444");
    private static final Color BEAR_EDGE = Color.web("#fca5a5");
    private static final Color WICK_COLOR = Color.web("#cbd5e1", 0.85);
    private static final Color VOLUME_BULL = Color.web("#22c55e", 0.35);
    private static final Color VOLUME_BEAR = Color.web("#ef4444", 0.35);
    private static final Color CROSSHAIR_COLOR = Color.web("#cbd5e1", 0.58);
    private static final Color SMA_COLOR = Color.web("#f59e0b");
    private static final Color EMA_COLOR = Color.web("#38bdf8");
    private static final Color BOLLINGER_UPPER_COLOR = Color.web("#a78bfa", 0.9);
    private static final Color BOLLINGER_MIDDLE_COLOR = Color.web("#f8fafc", 0.72);
    private static final Color BOLLINGER_LOWER_COLOR = Color.web("#a78bfa", 0.9);
    private static final String MONOSPACED_FONT_FAMILY = FXUtils.getMonospacedFont();
    private static final Font SUMMARY_META_FONT = Font.font(MONOSPACED_FONT_FAMILY, FontWeight.SEMI_BOLD, 12);
    private static final Font SUMMARY_PRICE_FONT = Font.font(MONOSPACED_FONT_FAMILY, FontWeight.BOLD, 24);
    private static final Font BADGE_FONT = Font.font(MONOSPACED_FONT_FAMILY, FontWeight.SEMI_BOLD, 11);
    private static final boolean LIVE_BACKTEST_ENABLED = Boolean.getBoolean("investpro.ai.liveBacktest");
    private final InProgressCandle inProgressCandle = new InProgressCandle();
    // index of first visible candle
    // how many candles to move per navigation
    private final int secondsPerCandle;
    private final CandleDataPager candleDataPager;
    private final boolean liveSyncing;
    private final ProgressIndicator progressIndicator;
    private final StableTicksAxis xAxis = new StableTicksAxis(0,100000000);
    private final CandleStickChartOptions chartOptions = new CandleStickChartOptions();
    private boolean showGrid = true;
    private Map<Long, ZoomLevel> zoomLevelMap;
    private volatile ZoomLevel currZoomLevel;
    private final StableTicksAxis yAxis = new StableTicksAxis(0,100000000);
    private final StableTicksAxis extraAxis = new StableTicksAxis(0,100000000);
    @Getter
    private Canvas canvas = new Canvas();
    private GraphicsContext graphicsContext;
    private int candleWidth = 10;
    private final NavigableMap<Long, CandleData> data = Collections.synchronizedNavigableMap(new TreeMap<>(Long::compare));
    private int inProgressCandleLastDraw = -1;
    private static final Font canvasNumberFont = new Font(14);
    private volatile boolean paging;
    private double chartWidth = 1300;
    private double chartHeight = 700;
    private final Exchange exchange;
    private final TradePair tradePair;
    private final CandlePageConsumer candlePageConsumer;
    private final SimpleDoubleProperty widthProperty;
    private InvestProAIAutotrader ai;
    private final TelegramClient telegramClient;
    Double[] bid_ask;
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

    private List<CandleData> candleData;
    private double startIndex;
    private String tokens;
    private int verticalGridLines = 10;
    private int horizontalGridLines = 5;
    private int scrollStep = 1; // finer than a navigation step
    private EventHandler<MouseEvent> mousePressedHandler;

    private EventHandler<MouseEvent> mouseReleasedHandler;

    private double dragStartX;
    private boolean isDragging = false;
    private Line bidLine;
    private Line askLine;
    private Label bidLabel;
    private Label askLabel;
    private Label chartStatusLabel;
    private Line verticalCrosshair;
    private Line horizontalCrosshair;
    private Label priceLabel;
    private Label timeLabel;
    private final AtomicBoolean redrawQueued = new AtomicBoolean(false);
    private final AtomicBoolean redrawRequested = new AtomicBoolean(false);
    private final AtomicBoolean queuedClearCanvas = new AtomicBoolean(false);
    private ObservableNumberValue containerWidthObservable;
    private ObservableNumberValue containerHeightObservable;
    private SizeChangeListener sizeListener;
    private BooleanProperty gotFirstSize;
    private ChangeListener<Boolean> gotFirstSizeChangeListener;
    private StackPane chartStackPane;
    private Label exchangeMessageLabel;
    private Parent inputEventNode;
    private EventHandler<MouseEvent> resetMouseHandler;
    private EventHandler<ScrollEvent> canvasZoomHandler;
    private boolean eventHandlersInitialized;
    private volatile boolean disposed;
    private volatile boolean aiTradingEnabled;
    private volatile boolean bidAskLinesVisible = true;
    private PauseTransition chartNotificationTransition;

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
        this.tokens = token == null ? "" : token.trim();
        this.telegramClient = new TelegramClient(this.tokens);
        getStyleClass().add("candle-chart");
        this.candleDataSupplier = candleDataSupplier;
        this.mousePressedHandler = new MousePressedHandler();
        this.scrollHandler = new ScrollEventHandler();
        this.mouseDraggedHandler = new MouseDraggedHandler();
        this.keyHandler = new KeyEventHandler();

        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;

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
        chartOptions.showSma20Property().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.showEma50Property().addListener((_, _, _) -> drawChartContents(true));
        chartOptions.showBollingerBandsProperty().addListener((_, _, _) -> drawChartContents(true));

        setupAxes(tradePair);
        setupCanvasAndLayout(containerWidth, containerHeight);
        setupAsyncTasks();
        getChildren().addAll(
                xAxis, yAxis, extraAxis, extraAxisExtension);



    }

    /**
     * Moves the x-axis bounds by one or more candle durations.
     * Positive deltaX shifts right, negative shifts left.
     */
    private void setAxisBoundsForMove(int deltaX) {
        if (deltaX == 0) {
            return;
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
    private void setYAndExtraAxisBounds() {
        NavigableMap<Long, CandleData> visibleCandles = getVisibleCandlesInRange();
        if (visibleCandles.isEmpty()) {
            return;
        }

        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = Double.NEGATIVE_INFINITY;
        double maxVolume = 0;

        for (CandleData candle : visibleCandles.values()) {
            minPrice = Math.min(minPrice, candle.getLowPrice());
            maxPrice = Math.max(maxPrice, candle.getHighPrice());
            maxVolume = Math.max(maxVolume, candle.getVolume());
        }

        if (!Double.isFinite(minPrice) || !Double.isFinite(maxPrice)) {
            return;
        }

        final double idealBufferSpaceMultiplier = 0.35;
        double yAxisDelta = maxPrice - minPrice;
        if (yAxisDelta == 0) {
            yAxisDelta = Math.max(Math.abs(maxPrice) * 0.02, 0.01);
        }

        double paddedMin = minPrice - (yAxisDelta * idealBufferSpaceMultiplier);
        double paddedMax = maxPrice + (yAxisDelta * idealBufferSpaceMultiplier);

        yAxis.setLowerBound(paddedMin);
        yAxis.setUpperBound(paddedMax);

        extraAxis.setLowerBound(0);
        extraAxis.setUpperBound(maxVolume <= 0 ? 1 : maxVolume * 1.15);
    }

    private NavigableMap<Long, CandleData> getVisibleCandlesInRange() {
        NavigableMap<Long, CandleData> visibleCandles = new TreeMap<>();
        if (currZoomLevel == null || data.isEmpty()) {
            return visibleCandles;
        }

        long leftmostVisibleOpenTime = (long) xAxis.getLowerBound();
        long rightmostVisibleOpenTime = (long) xAxis.getUpperBound() - secondsPerCandle;
        if (rightmostVisibleOpenTime < leftmostVisibleOpenTime) {
            return visibleCandles;
        }

        visibleCandles.putAll(data.subMap(leftmostVisibleOpenTime, true, rightmostVisibleOpenTime, true));
        return visibleCandles;
    }

    private int getVisibleCandleStartIndex(@NotNull NavigableMap<Long, CandleData> visibleCandles) {
        if (data.isEmpty() || visibleCandles.isEmpty()) {
            return 1;
        }

        int latestVisibleOpenTime = visibleCandles.lastEntry().getValue().getOpenTime();
        int latestAvailableOpenTime = data.lastEntry().getValue().getOpenTime();
        return 1 + Math.max((latestAvailableOpenTime - latestVisibleOpenTime) / secondsPerCandle, 0);
    }

    private void drawOHLCVLabel(@NotNull CandleData candle) {
        String text = String.format("O %.2f   H %.2f   L %.2f   C %.2f   V %.2f",
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getVolume());

        graphicsContext.setFill(CHART_TEXT_MUTED);
        graphicsContext.setFont(SUMMARY_META_FONT);
        graphicsContext.fillText(text, 32, 88);
    }

    private void paintChartBackdrop() {
        LinearGradient backdrop = new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, CHART_BACKGROUND_TOP),
                new Stop(1, CHART_BACKGROUND_BOTTOM)
        );

        graphicsContext.setFill(backdrop);
        graphicsContext.fillRoundRect(0, 0, chartWidth - 100, chartHeight - 150, 18, 18);
        graphicsContext.setStroke(Color.web("#1e293b", 0.92));
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRoundRect(0.5, 0.5, chartWidth - 101, chartHeight - 151, 18, 18);
    }

    private void drawMarketSummary(List<CandleData> visibleCandles) {
        CandleData latestCandle = visibleCandles.getLast();
        CandleData previousCandle = visibleCandles.size() > 1
                ? visibleCandles.get(visibleCandles.size() - 2)
                : latestCandle;

        double delta = latestCandle.getClosePrice() - previousCandle.getClosePrice();
        double deltaPercent = previousCandle.getClosePrice() == 0
                ? 0
                : (delta / previousCandle.getClosePrice()) * 100;
        boolean positiveMove = delta >= 0;
        Color moveColor = positiveMove ? BULL_BODY : BEAR_BODY;

        graphicsContext.setFill(CHART_PANEL_FILL);
        graphicsContext.fillRoundRect(18, 16, 340, 74, 18, 18);
        graphicsContext.setStroke(CHART_PANEL_BORDER);
        graphicsContext.strokeRoundRect(18, 16, 340, 74, 18, 18);

        graphicsContext.setFill(CHART_TEXT_MUTED);
        graphicsContext.setFont(SUMMARY_META_FONT);
        graphicsContext.fillText(tradePair.toString('/') + "   " + getTimeframe(secondsPerCandle).toUpperCase(), 32, 38);

        graphicsContext.setFill(CHART_TEXT_PRIMARY);
        graphicsContext.setFont(SUMMARY_PRICE_FONT);
        graphicsContext.fillText(String.format("%.2f", latestCandle.getClosePrice()), 32, 67);

        graphicsContext.setFill(moveColor);
        graphicsContext.setFont(SUMMARY_META_FONT);
        graphicsContext.fillText(String.format("%+.2f  (%+.2f%%)", delta, deltaPercent), 180, 67);

        drawBadge(inProgressCandle.isPlaceHolder() ? "Delayed" : "Live", 370,
                inProgressCandle.isPlaceHolder() ? Color.web("#f59e0b", 0.24) : Color.web("#14b8a6", 0.24),
                inProgressCandle.isPlaceHolder() ? Color.web("#fbbf24") : Color.web("#5eead4"));
        drawBadge(visibleCandles.size() + " candles", 458, Color.web("#0f172a", 0.78), CHART_TEXT_MUTED);
        drawBadge(aiTradingEnabled ? "AI Active" : "Manual", 562,
                aiTradingEnabled ? Color.web("#0f766e", 0.32) : Color.web("#334155", 0.78),
                aiTradingEnabled ? Color.web("#99f6e4") : CHART_TEXT_MUTED);

        drawOHLCVLabel(latestCandle);
    }

    private void drawBadge(@NotNull String label, double x, Color fill, Color textColor) {
        double badgeWidth = Math.max(74, (label.length() * 7.3) + 18);
        graphicsContext.setFill(fill);
        graphicsContext.fillRoundRect(x, 22, badgeWidth, 24, 12, 12);
        graphicsContext.setStroke(Color.web("#cbd5e1", 0.16));
        graphicsContext.strokeRoundRect(x, 22, badgeWidth, 24, 12, 12);
        graphicsContext.setFill(textColor);
        graphicsContext.setFont(BADGE_FONT);
        graphicsContext.fillText(label, x + 10, (double) 22 + 16);
    }

    private void drawPlaceholderCandle(@NotNull CandleData candleDatum, int candleIndex, double lastClose, double pixelsPerMonetaryUnit) {
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
    }

    private void setupCanvasAndLayout(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight) {
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        containerWidthObservable = containerWidth;
        containerHeightObservable = containerHeight;
        gotFirstSize = new SimpleBooleanProperty(false);

        sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        // Initial placeholder canvas, will be resized after size is known
        canvas = new Canvas(
                clampCanvasDimension(containerWidth.doubleValue() - 100),
                clampCanvasDimension(containerHeight.doubleValue() - 100)
        );
        graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
        setupCrosshairOverlay();
        // Setup loading indicator
        Text loadingText = new Text("Loading...");
        loadingText.setFont(Font.font(FXUtils.getMonospacedFont(), 14));
        loadingText.setFill(CHART_TEXT_PRIMARY);
        loadingText.setStroke(CHART_ACCENT);
        loadingText.setMouseTransparent(true);

        progressIndicator.setMouseTransparent(true);
        VBox loadingIndicatorContainer = new VBox(progressIndicator, loadingText);
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setPadding(new Insets(20));
        loadingIndicatorContainer.setMouseTransparent(true);

        chartStatusLabel = new Label();
        chartStatusLabel.setStyle(
                "-fx-text-fill: #f8fafc; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: rgba(15,23,42,0.9); " +
                        "-fx-border-color: rgba(148,163,184,0.22); " +
                        "-fx-padding: 14 18 14 18; " +
                        "-fx-background-radius: 14; " +
                        "-fx-border-radius: 14;"
        );
        chartStatusLabel.setManaged(false);
        chartStatusLabel.setVisible(false);
        chartStatusLabel.setMouseTransparent(true);

        chartStackPane = new StackPane(canvas, loadingIndicatorContainer, chartStatusLabel);
        chartStackPane.setTranslateX(0);
        getChildren().addFirst(chartStackPane);

        // Wait until final size is known to layout chart properly
        gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                gotFirstSize.removeListener(this);
                if (disposed) {
                    return;
                }

                double containerW = containerWidth.getValue().doubleValue();
                double containerH = containerHeight.getValue().doubleValue();

                int visibleCandles = (int) Math.floor(containerW / candleWidth);
                chartWidth = visibleCandles * candleWidth - 60 + (candleWidth / 2.0);
                chartHeight = containerH;

                canvas.setWidth(clampCanvasDimension(chartWidth - 100));
                canvas.setHeight(clampCanvasDimension(chartHeight - 200));

                canvas.setLayoutX(0);
                canvas.setLayoutY(0);

                extraAxisExtension.setTranslateX(0);
                extraAxis.setTranslateX(0);
                yAxis.setTranslateX(0);

                // Exchange message label
                if (exchangeMessageLabel == null) {
                    exchangeMessageLabel = new Label(exchange.getExchangeMessage());
                    exchangeMessageLabel.setStyle(
                            "-fx-text-fill: #94a3b8; " +
                                    "-fx-background-color: rgba(8,18,29,0.88); " +
                                    "-fx-border-color: rgba(148,163,184,0.15); " +
                                    "-fx-border-radius: 12; " +
                                    "-fx-background-radius: 12; " +
                                    "-fx-padding: 6 10 6 10; " +
                                    "-fx-font-size: 11px;"
                    );
                    getChildren().add(exchangeMessageLabel);
                }
                exchangeMessageLabel.setText(exchange.getExchangeMessage());
                exchangeMessageLabel.relocate(18, 18);

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
        xAxis.setTranslateY(0);
        xAxis.setPrefSize(canvas == null ? 1530 : canvas.getWidth(), 20);
        yAxis.setPrefSize(100, canvas == null ? 780 : canvas.getHeight());

        yAxis.setTranslateY(0);
        extraAxis.setTranslateY(0);


        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 14);
        Paint axisColor = CHART_ACCENT; // For extra axis extension lines

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

    private double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    private double clampCanvasDimension(double proposedDimension) {
        return Math.max(64, Math.min(proposedDimension, 4096));
    }

    private void setupAsyncTasks() {
        updateInProgressCandleTask = new UpdateInProgressCandleTask();
        updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));
        updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
    }

    private double mapPriceToY(double price) {
        double range = yAxis.getUpperBound() - yAxis.getLowerBound();
        return ((yAxis.getUpperBound() - price) / range) * canvas.getHeight();
    }


    private void drawHighLowMarkers(double highestCandleValue, double lowestCandleValue, int candleIndexOfHighest, int candleIndexOfLowest, double pixelsPerMonetaryUnit) {
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

    private void refreshBidAskSnapshot() {
        try {
            Double[] latestPrice = exchange.getLatestPrice(tradePair);
            if (latestPrice != null && latestPrice.length >= 2
                    && latestPrice[0] > 0 && latestPrice[1] > 0) {
                bid_ask = latestPrice;
            }
        } catch (Exception e) {
            logger.debug("Unable to refresh bid/ask for {}", tradePair, e);
        }
    }

    private void drawBidAskGuides() {
        if (!bidAskLinesVisible) {
            return;
        }
        if (bid_ask == null || bid_ask.length < 2) {
            return;
        }

        double bidPrice = bid_ask[0];
        double askPrice = bid_ask[1];
        if (bidPrice <= 0 || askPrice <= 0) {
            return;
        }

        drawBidAskGuide("Bid", bidPrice, Color.web("#22c55e"), Color.web("#86efac"));
        drawBidAskGuide("Ask", askPrice, Color.web("#ef4444"), Color.web("#fca5a5"));
    }

    private void drawBidAskGuide(String label, double price, Color lineColor, Color textColor) {
        double y = mapPriceToY(price);
        if (Double.isNaN(y) || y < 0 || y > canvas.getHeight()) {
            return;
        }

        String text = label + " " + formatPriceGuide(price);
        double textWidth = Math.max(84, text.length() * 7.1);
        double boxHeight = 22;
        double boxX = Math.max(8, canvas.getWidth() - textWidth - 14);
        double boxY = Math.max(8, Math.min(y - (boxHeight / 2.0), canvas.getHeight() - boxHeight - 8));

        graphicsContext.save();
        graphicsContext.setStroke(lineColor.deriveColor(0, 1, 1, 0.82));
        graphicsContext.setLineWidth(1.05);
        graphicsContext.setLineDashes(2, 6);
        graphicsContext.strokeLine(0, y, canvas.getWidth(), y);
        graphicsContext.setLineDashes(0);

        graphicsContext.setFill(lineColor);
        graphicsContext.fillOval(canvas.getWidth() - 18, y - 4, 8, 8);

        graphicsContext.setFill(Color.web("#08121d", 0.94));
        graphicsContext.fillRoundRect(boxX, boxY, textWidth, boxHeight, 12, 12);
        graphicsContext.setStroke(lineColor.deriveColor(0, 1, 1, 0.38));
        graphicsContext.strokeRoundRect(boxX, boxY, textWidth, boxHeight, 12, 12);
        graphicsContext.setFill(textColor);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), FontWeight.SEMI_BOLD, 11));
        graphicsContext.fillText(text, boxX + 10, boxY + 15);
        graphicsContext.restore();
    }

    private String formatPriceGuide(double price) {
        if (price >= 1000) {
            return String.format("%.2f", price);
        }
        if (price >= 1) {
            return String.format("%.4f", price);
        }
        return String.format("%.6f", price);
    }

    private void moveAlongX(int deltaX, boolean skipDraw) {
        if (deltaX == 0) {
            return;
        }

        if (disposed) {
            return;
        }

        if (!Platform.isFxApplicationThread()) {
            runLaterIfAlive(() -> moveAlongX(deltaX, skipDraw));
            return;
        }

        if (currZoomLevel == null || data.isEmpty() || progressIndicator.isVisible()) {
            return;
        }

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
                progressIndicator.setVisible(true);
                CompletableFuture
                        .supplyAsync(candleDataSupplier)
                        .thenAccept(candleDataPager.getCandleDataPreProcessor())
                        .whenComplete((_, throwable) -> runLaterIfAlive(() -> {
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
                chartUpdater.run();
            }
        }
    }

    private void layoutChart() {
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

        double canvasWidth = snapSizeX(canvas.getWidth());
        double canvasHeight = snapSizeY(canvas.getHeight());
        double axisGap = 8;
        double canvasX = left;
        double yAxisX = canvasX + canvasWidth + axisGap;
        double volumeAxisHeight = Math.max(80, (chartHeight - 100) * 0.25);

        xAxis.setLayoutX(canvasX);
        yAxis.setLayoutX(yAxisX);
        xAxis.setPrefSize(canvasWidth, xAxisHeight);
        yAxis.setPrefSize(yAxisWidth, canvasHeight);
        extraAxis.setPrefSize(yAxisWidth, volumeAxisHeight);
        xAxis.setLayoutY(top + canvasHeight);
        yAxis.setLayoutY(top);
        extraAxis.setLayoutX(yAxisX);
        extraAxis.setLayoutY(top + canvasHeight - volumeAxisHeight);
        xAxis.requestAxisLayout();
        yAxis.requestAxisLayout();
        extraAxis.requestAxisLayout();
        canvas.setLayoutX(canvasX);
        canvas.setLayoutY(top);

        extraAxisExtension.setStartX(yAxisX);
        extraAxisExtension.setEndX(yAxisX);
        extraAxisExtension.setStartY(top);
        extraAxisExtension.setEndY(top + canvasHeight);
    }

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis bounds.
     */
    public void drawChartContents(boolean clearCanvas) {
        if (disposed) {
            return;
        }

        redrawRequested.set(true);
        if (clearCanvas) {
            queuedClearCanvas.set(true);
        }

        if (redrawQueued.compareAndSet(false, true)) {
            runLaterIfAlive(this::flushQueuedRedraws);
        }
    }

    private void flushQueuedRedraws() {
        try {
            while (!disposed && redrawRequested.getAndSet(false)) {
                boolean effectiveClear = queuedClearCanvas.getAndSet(false);
                drawChartContentsNow(effectiveClear);
            }
        } finally {
            redrawQueued.set(false);
            if (!disposed && redrawRequested.get() && redrawQueued.compareAndSet(false, true)) {
                runLaterIfAlive(this::flushQueuedRedraws);
            }
        }
    }

    private void drawChartContentsNow(boolean clearCanvas) {
        if (isCanvasReadyForDrawing() || currZoomLevel == null || data.isEmpty()) {
            return;
        }

        if (clearCanvas) {
            graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            paintChartBackdrop();
        }

        double monetaryUnitsPerPixel = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        double pixelsPerMonetaryUnit = 1d / monetaryUnitsPerPixel;

        NavigableMap<Long, CandleData> candlesToDraw = getVisibleCandlesInRange();

        List<CandleData> visibleCandles = new ArrayList<>(candlesToDraw.values());
        if (visibleCandles.isEmpty()) {
            return;
        }

        drawMarketSummary(visibleCandles);

        // Grid lines
        if (showGrid && chartOptions.isHorizontalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.setStroke(GRID_MAJOR);
                graphicsContext.setLineWidth(1);
                graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
            }
        }

        if (showGrid && chartOptions.isVerticalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
                graphicsContext.setStroke(GRID_MINOR);
                graphicsContext.setLineWidth(1);
                graphicsContext.strokeLine(tickMark.getPosition(), 0, tickMark.getPosition(), canvas.getHeight());
            }
        }

        int candleIndex = getVisibleCandleStartIndex(candlesToDraw);
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        int highIndex = -1;
        int lowIndex = -1;
        int volumeMaxHeight = 150;
        double volumeScale = extraAxis.getUpperBound() <= 0 ? 0 : volumeMaxHeight / extraAxis.getUpperBound();
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
                drawTradingViewCandle(candle, candleIndex);
                if (chartOptions.isShowVolume()) {
                    drawVolumeBar(candle, candleIndex, volumeScale);
                }
            }

            lastClose = candle.getClosePrice();

            candleIndex++;

        }

        drawIndicatorOverlays(visibleCandles, getVisibleCandleStartIndex(candlesToDraw));
        drawHighLowMarkers(highest, lowest, highIndex, lowIndex, pixelsPerMonetaryUnit);
        drawBidAskGuides();
    }

    private void drawIndicatorOverlays(List<CandleData> visibleCandles, int startingCandleIndex) {
        if (!chartOptions.isShowSma20() && !chartOptions.isShowEma50() && !chartOptions.isShowBollingerBands()) {
            return;
        }

        List<CandleData> orderedCandles = new ArrayList<>(data.values());
        if (orderedCandles.isEmpty()) {
            return;
        }

        Map<Integer, Integer> candleIndexByOpenTime = new HashMap<>();
        for (int i = 0; i < orderedCandles.size(); i++) {
            candleIndexByOpenTime.put(orderedCandles.get(i).getOpenTime(), i);
        }

        if (chartOptions.isShowSma20()) {
            drawIndicatorLine(
                    visibleCandles,
                    candleIndexByOpenTime,
                    IndicatorCalculator.calculateSMASeries(orderedCandles, 20),
                    startingCandleIndex,
                    SMA_COLOR,
                    1.7
            );
        }

        if (chartOptions.isShowEma50()) {
            drawIndicatorLine(
                    visibleCandles,
                    candleIndexByOpenTime,
                    IndicatorCalculator.calculateEMASeries(orderedCandles, 50),
                    startingCandleIndex,
                    EMA_COLOR,
                    1.6
            );
        }

        if (chartOptions.isShowBollingerBands()) {
            IndicatorCalculator.BollingerBands bands = IndicatorCalculator.calculateBollingerBandsSeries(orderedCandles, 20);
            drawIndicatorLine(visibleCandles, candleIndexByOpenTime, bands.upperBand(), startingCandleIndex, BOLLINGER_UPPER_COLOR, 1.15);
            drawIndicatorLine(visibleCandles, candleIndexByOpenTime, bands.middleBand(), startingCandleIndex, BOLLINGER_MIDDLE_COLOR, 1.0);
            drawIndicatorLine(visibleCandles, candleIndexByOpenTime, bands.lowerBand(), startingCandleIndex, BOLLINGER_LOWER_COLOR, 1.15);
        }
    }

    private void drawIndicatorLine(List<CandleData> visibleCandles,
                                   Map<Integer, Integer> candleIndexByOpenTime,
                                   List<Double> series,
                                   int startingCandleIndex,
                                   Color color,
                                   double lineWidth) {
        graphicsContext.save();
        graphicsContext.setStroke(color);
        graphicsContext.setLineWidth(lineWidth);

        boolean pathStarted = false;
        for (int i = 0; i < visibleCandles.size(); i++) {
            CandleData candle = visibleCandles.get(i);
            Integer globalIndex = candleIndexByOpenTime.get(candle.getOpenTime());
            if (globalIndex == null || globalIndex >= series.size()) {
                if (pathStarted) {
                    graphicsContext.stroke();
                    pathStarted = false;
                }
                continue;
            }

            Double value = series.get(globalIndex);
            if (value == null || !Double.isFinite(value)) {
                if (pathStarted) {
                    graphicsContext.stroke();
                }
                pathStarted = false;
                continue;
            }

            int relativeFromLatest = visibleCandles.size() - 1 - i;
            int candleSlot = startingCandleIndex + relativeFromLatest;
            double x = candleCenterX(candleSlot);
            double y = yAxis.getDisplayPosition(value);
            if (!Double.isFinite(y)) {
                if (pathStarted) {
                    graphicsContext.stroke();
                }
                pathStarted = false;
                continue;
            }

            if (!pathStarted) {
                graphicsContext.beginPath();
                graphicsContext.moveTo(x, y);
                pathStarted = true;
            } else {
                graphicsContext.lineTo(x, y);
            }
        }

        if (pathStarted) {
            graphicsContext.stroke();
        }
        graphicsContext.restore();
    }

    private double candleCenterX(int candleIndex) {
        return (canvas.getWidth() - (candleIndex * candleWidth)) + (candleWidth * 0.5);
    }

    private boolean isCanvasReadyForDrawing() {
        return disposed
                || canvas == null
                || graphicsContext == null
                || !Double.isFinite(canvas.getWidth())
                || !Double.isFinite(canvas.getHeight())
                || !(canvas.getWidth() > 1)
                || !(canvas.getHeight() > 1)
                || getScene() == null
                || getScene().getWindow() == null
                || !getScene().getWindow().isShowing();
    }

    private void advanceViewportForLiveSync() {
        if (!liveSyncing || isCanvasReadyForDrawing() || currZoomLevel == null || data.isEmpty()) {
            return;
        }

        int currentOpenTime = inProgressCandle.getOpenTime();
        if (inProgressCandleLastDraw == currentOpenTime) {
            return;
        }

        if (inProgressCandleLastDraw < 0) {
            inProgressCandleLastDraw = currentOpenTime;
            return;
        }

        int numCandlesToSkip = Math.max(((int) xAxis.getUpperBound() - data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);
        if (xAxis.getUpperBound() >= inProgressCandleLastDraw
                && xAxis.getUpperBound() < inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)
                && numCandlesToSkip == 0) {
            setAxisBoundsForMove(1);
            setYAndExtraAxisBounds();
        }

        inProgressCandleLastDraw = currentOpenTime;
    }

    private void drawVolumeBar(CandleData candle, int index, double volumeScale) {
        double volume = candle.getVolume();
        double barHeight = volume * volumeScale;
        double x = canvas.getWidth() - (index * candleWidth);
        double y = canvas.getHeight() - barHeight;

        Color barColor = candle.getClosePrice() >= candle.getOpenPrice() ? VOLUME_BULL : VOLUME_BEAR;

        graphicsContext.setFill(barColor);
        graphicsContext.fillRoundRect(x + 1, y, Math.max(candleWidth - 2, 1), barHeight, 6, 6);
    }

    private void drawTradingViewCandle(CandleData candle, int index) {
        double x = canvas.getWidth() - (index * candleWidth);
        double openY = yAxis.getDisplayPosition(candle.getOpenPrice());
        double closeY = yAxis.getDisplayPosition(candle.getClosePrice());
        double highY = yAxis.getDisplayPosition(candle.getHighPrice());
        double lowY = yAxis.getDisplayPosition(candle.getLowPrice());

        boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();

        Color bodyColor = isBullish ? BULL_BODY : BEAR_BODY;
        Color borderColor = isBullish ? BULL_EDGE : BEAR_EDGE;

        double candleBodyTop = Math.min(openY, closeY);
        double candleBodyHeight = Math.max(Math.abs(openY - closeY), 2);
        double bodyWidth = Math.max(candleWidth - 2, 2);
        double bodyX = x + ((candleWidth - bodyWidth) / 2.0);

        // Wick
        graphicsContext.setStroke(WICK_COLOR);
        graphicsContext.setLineWidth(1.2);
        graphicsContext.strokeLine(x + (double) candleWidth / 2, highY, x + (double) candleWidth / 2, lowY);

        // Body
        graphicsContext.setFill(bodyColor);
        graphicsContext.fillRoundRect(bodyX, candleBodyTop, bodyWidth, candleBodyHeight, 4, 4);
        graphicsContext.setStroke(borderColor);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRoundRect(bodyX, candleBodyTop, bodyWidth, candleBodyHeight, 4, 4);
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

    public CandleStickChartOptions getChartOptions() {
        return chartOptions;
    }

    public boolean isAiTradingEnabled() {
        return aiTradingEnabled;
    }

    public boolean isBidAskLinesVisible() {
        return bidAskLinesVisible;
    }

    public void setBidAskLinesVisible(boolean visible) {
        bidAskLinesVisible = visible;
        drawChartContents(true);
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
        if (disposed || paging || progressIndicator.isVisible() || data.isEmpty()) {
            return;
        }

        final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
        if (currZoomLevel == null) {
            logger.error("currZoomLevel was null!");
            return;
        }
        int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
        if (newCandleWidth <= 1) {
            showChartNotification("Maximum zoom reached.");
            return;
        }

        int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) (canvas.getWidth() /
                newCandleWidth) * secondsPerCandle));
        if (newLowerBoundX > data.lastEntry().getValue().getOpenTime() - (2 * secondsPerCandle)) {
            showChartNotification("No more candle data available for that zoom level.");
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
                    rebuildExtremaMap(newZoomLevel, candleData);
                    zoomLevelMap.put((long) nextZoomLevelId, newZoomLevel);
                    currZoomLevel = newZoomLevel;
                    runLaterIfAlive(() -> {
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
                rebuildExtremaMap(newZoomLevel, candleData);
                zoomLevelMap.put((long) nextZoomLevelId, newZoomLevel);
                currZoomLevel = newZoomLevel;
            }
        } else {
            //  In this case, we only need to compute the extrema for any new live syncing data that has
            //  happened since the last time we were at this zoom level.
            currZoomLevel = zoomLevelMap.get((long) nextZoomLevelId);
            List<CandleData> candleData = new ArrayList<>(data.values());
            rebuildExtremaMap(currZoomLevel, candleData);
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



    public void setShowGrid(boolean b) {
        showGrid = b;
        drawChartContents(true);
    }

    public void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Messages(Alert.AlertType.ERROR, "No printer is available for this chart.");
            return;
        }

        if (getScene() == null || getScene().getWindow() == null) {
            new Messages(Alert.AlertType.WARNING, "Open the chart in a window before printing.");
            return;
        }

        boolean proceed = job.showPrintDialog(getScene().getWindow());
        if (proceed) {
            boolean success = job.printPage(this); // 'this' refers to the CandleStickChart StackPane
            if (success) {
                job.endJob();
            } else {
                new Messages(Alert.AlertType.ERROR, "Printing failed for this chart.");
            }
        }
    }

    public TelegramClient getTelegram() {
        return telegramClient;
    }



    public void toggleFullScreen() {
        if (getScene() == null || !(getScene().getWindow() instanceof Stage stage)) {
            new Messages(Alert.AlertType.WARNING, "Open the chart in its own window before toggling full screen.");
            return;
        }
        stage.setFullScreen(!stage.isFullScreen());
    }

    public void scroll(NavigationDirection navigationDirection) {
        if (data.isEmpty()) {
            return;
        }
        if (navigationDirection == NavigationDirection.LEFT) {
            moveAlongX(-1, false);
            return;
        }
        if (navigationDirection == NavigationDirection.RIGHT) {
            moveAlongX(1, false);
            return;
        }
        if (navigationDirection == NavigationDirection.UP) {
            panVertically(1);
            return;
        }
        if (navigationDirection == NavigationDirection.DOWN) {
            panVertically(-1);
        }
    }

    private void panVertically(int direction) {
        double currentRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        if (currentRange <= 0) {
            return;
        }

        double shift = currentRange * 0.08 * direction;
        yAxis.setLowerBound(yAxis.getLowerBound() + shift);
        yAxis.setUpperBound(yAxis.getUpperBound() + shift);
        extraAxis.setLowerBound(extraAxis.getLowerBound() + shift);
        extraAxis.setUpperBound(extraAxis.getUpperBound() + shift);
        drawChartContents(true);
    }

    public void jumpToLatestCandle() {
        if (data.isEmpty()) {
            return;
        }

        int latestOpenTime = data.lastEntry().getValue().getOpenTime();
        xAxis.setUpperBound(latestOpenTime + secondsPerCandle);
        xAxis.setLowerBound((latestOpenTime + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));
        setYAndExtraAxisBounds();
        refreshBidAskSnapshot();
        drawChartContents(true);
    }

    public void fitChart() {
        if (data.isEmpty()) {
            return;
        }

        int latestOpenTime = data.lastEntry().getValue().getOpenTime();
        int visibleCandles = Math.max(24, (int) Math.floor(canvas.getWidth() / Math.max(candleWidth, 1)));
        xAxis.setUpperBound(latestOpenTime + secondsPerCandle);
        xAxis.setLowerBound((latestOpenTime + secondsPerCandle) - (visibleCandles * secondsPerCandle));
        setYAndExtraAxisBounds();
        refreshBidAskSnapshot();
        layoutChart();
        drawChartContents(true);
    }

    public void refreshChart() {
        CompletableFuture.runAsync(this::refreshBidAskSnapshot)
                .whenComplete((_, throwable) -> runLaterIfAlive(() -> {
                    if (throwable != null) {
                        logger.warn("Unable to refresh chart market snapshot for {}", tradePair, throwable);
                        new Messages(Alert.AlertType.WARNING,
                                "Unable to refresh chart data right now. Please try again in a moment.");
                    }
                    if (!data.isEmpty()) {
                        fitChart();
                    } else {
                        drawChartContents(true);
                    }
                }));
    }

    public double getLatestClosePrice() {
        if (data.isEmpty()) {
            return Double.NaN;
        }
        return data.lastEntry().getValue().getClosePrice();
    }

    public String getSignalBias() {
        if (data.size() < 2) {
            return "Neutral";
        }

        List<CandleData> candles = new ArrayList<>(data.values());
        CandleData latest = candles.getLast();
        CandleData previous = candles.get(candles.size() - 2);
        double delta = latest.getClosePrice() - previous.getClosePrice();
        if (Math.abs(delta) < 1e-9) {
            return "Neutral";
        }
        return delta > 0 ? "Bullish" : "Bearish";
    }

    public int getLoadedCandleCount() {
        return data.size();
    }

    public void toggleAiTrading() {
        aiTradingEnabled = !aiTradingEnabled;
        if (aiTradingEnabled) {
            ensureAutotraderInitialized();
        }
        drawChartContents(true);
        showChartNotification(aiTradingEnabled ? "AI autotrading enabled." : "AI autotrading paused.");
    }

    public void showTradeTicket() {
        if (getScene() == null || getScene().getWindow() == null) {
            new Messages(Alert.AlertType.WARNING, "Open the chart in a window before sending trades.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Trade Ticket");
        dialog.setHeaderText(tradePair + " manual trade");

        ButtonType buyButtonType = new ButtonType("Buy", ButtonBar.ButtonData.LEFT);
        ButtonType sellButtonType = new ButtonType("Sell", ButtonBar.ButtonData.RIGHT);
        dialog.getDialogPane().getButtonTypes().addAll(buyButtonType, sellButtonType, ButtonType.CANCEL);

        ComboBox<ENUM_ORDER_TYPE> orderTypeBox = new ComboBox<>();
        orderTypeBox.getItems().setAll(ENUM_ORDER_TYPE.MARKET, ENUM_ORDER_TYPE.LIMIT, ENUM_ORDER_TYPE.STOP);
        orderTypeBox.getSelectionModel().select(ENUM_ORDER_TYPE.MARKET);

        TextField sizeField = new TextField("0.01");
        TextField priceField = new TextField(resolveDisplayedTradePrice(org.investpro.investpro.Side.BUY));
        TextField stopLossField = new TextField();
        TextField takeProfitField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("Order Type"), 0, 0);
        grid.add(orderTypeBox, 1, 0);
        grid.add(new Label("Size"), 0, 1);
        grid.add(sizeField, 1, 1);
        grid.add(new Label("Price"), 0, 2);
        grid.add(priceField, 1, 2);
        grid.add(new Label("Stop Loss"), 0, 3);
        grid.add(stopLossField, 1, 3);
        grid.add(new Label("Take Profit"), 0, 4);
        grid.add(takeProfitField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.initOwner(getScene().getWindow());

        orderTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            boolean marketOrder = newValue == ENUM_ORDER_TYPE.MARKET;
            priceField.setDisable(marketOrder);
            if (marketOrder) {
                priceField.setText(resolveDisplayedTradePrice(org.investpro.investpro.Side.BUY));
            }
        });
        priceField.setDisable(true);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return;
        }

        org.investpro.investpro.Side side = result.get() == buyButtonType
                ? org.investpro.investpro.Side.BUY
                : org.investpro.investpro.Side.SELL;
        ENUM_ORDER_TYPE orderType = orderTypeBox.getValue();
        double size;
        double price;
        double stopLoss;
        double takeProfit;

        try {
            size = parsePositiveDouble(sizeField.getText(), "size");
            price = orderType == ENUM_ORDER_TYPE.MARKET
                    ? resolveManualTradePrice(side)
                    : parsePositiveDouble(priceField.getText(), "price");
            stopLoss = parseOptionalDouble(stopLossField.getText());
            takeProfit = parseOptionalDouble(takeProfitField.getText());
        } catch (IllegalArgumentException ex) {
            new Messages(Alert.AlertType.WARNING, ex.getMessage());
            return;
        }

        placeManualTrade(side, orderType, size, price, stopLoss, takeProfit);
    }

    private void ensureAutotraderInitialized() {
        if (ai == null) {
            ai = new InvestProAIAutotrader(this);
        }
    }

    private void placeManualTrade(org.investpro.investpro.Side side,
                                  ENUM_ORDER_TYPE orderType,
                                  double size,
                                  double price,
                                  double stopLoss,
                                  double takeProfit) {
        CompletableFuture.runAsync(() -> {
            try {
                exchange.createOrder(tradePair, side, orderType, price, size, new Date(), stopLoss, takeProfit);
                refreshBidAskSnapshot();
                runLaterIfAlive(() -> {
                    drawChartContents(true);
                    showChartNotification(String.format("%s %s order sent: %.4f @ %s",
                            side.name(),
                            orderType.name(),
                            size,
                            formatPriceGuide(price)));
                });
            } catch (Exception ex) {
                logger.error("Manual trade failed for {}", tradePair, ex);
                runLaterIfAlive(() -> new Messages(Alert.AlertType.ERROR,
                        "Unable to submit the order: " + ex.getMessage()));
            }
        });
    }

    private @NotNull String resolveDisplayedTradePrice(org.investpro.investpro.Side side) {
        try {
            return formatPriceGuide(resolveManualTradePrice(side));
        } catch (Exception ignored) {
            return "";
        }
    }

    private double resolveManualTradePrice(org.investpro.investpro.Side side) {
        if (bid_ask != null && bid_ask.length >= 2) {
            double bid = bid_ask[0];
            double ask = bid_ask[1];
            if (bid > 0 && ask > 0) {
                return side == org.investpro.investpro.Side.BUY ? ask : bid;
            }
        }
        return exchange.fetchLivesBidAsk(tradePair);
    }

    private double parsePositiveDouble(String value, String fieldName) {
        double parsed = parseOptionalDouble(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("Enter a positive " + fieldName + ".");
        }
        return parsed;
    }

    private double parseOptionalDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a valid numeric value.");
        }
    }

    public void shutdown() {
        disposed = true;
        redrawQueued.set(false);
        redrawRequested.set(false);
        queuedClearCanvas.set(false);
        if (updateInProgressCandleTask != null) {
            updateInProgressCandleTask.setReady(false);
        }
        if (updateInProgressCandleExecutor != null) {
            updateInProgressCandleExecutor.shutdownNow();
        }

        Runnable releaseResources = () -> {
            detachInputHandlers();

            if (containerWidthObservable != null && sizeListener != null) {
                containerWidthObservable.removeListener(sizeListener);
            }
            if (containerHeightObservable != null && sizeListener != null) {
                containerHeightObservable.removeListener(sizeListener);
            }
            if (sizeListener != null) {
                sizeListener.dispose();
                sizeListener = null;
            }
            if (gotFirstSize != null && gotFirstSizeChangeListener != null) {
                gotFirstSize.removeListener(gotFirstSizeChangeListener);
                gotFirstSizeChangeListener = null;
            }

            if (chartNotificationTransition != null) {
                chartNotificationTransition.stop();
            }

            if (exchangeMessageLabel != null) {
                exchangeMessageLabel.setVisible(false);
            }
            if (chartStatusLabel != null) {
                chartStatusLabel.setVisible(false);
            }
            if (verticalCrosshair != null) {
                verticalCrosshair.setVisible(false);
            }
            if (horizontalCrosshair != null) {
                horizontalCrosshair.setVisible(false);
            }
            if (priceLabel != null) {
                priceLabel.setVisible(false);
            }
            if (timeLabel != null) {
                timeLabel.setVisible(false);
            }
            if (progressIndicator != null) {
                progressIndicator.setVisible(false);
            }
            if (chartStackPane != null) {
                chartStackPane.setVisible(false);
                chartStackPane.setManaged(false);
            }
            if (canvas != null) {
                canvas.setVisible(false);
                canvas.setManaged(false);
                canvas.setWidth(1);
                canvas.setHeight(1);
            }

            graphicsContext = null;
            getChildren().clear();
        };

        if (Platform.isFxApplicationThread()) {
            releaseResources.run();
        } else {
            FxLifecycle.runLaterIf(() -> true, releaseResources);
        }

    }

    public void exportAsPDF() {
        if (getScene() == null || getScene().getWindow() == null) {
            new Messages(Alert.AlertType.WARNING, "Open the chart in a window before exporting it.");
            return;
        }
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
                new Messages(Alert.AlertType.INFORMATION, "Chart PDF saved to:\n" + file.getAbsolutePath());
            }
            document.close();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            new Messages(Alert.AlertType.ERROR, "Unable to export the chart as PDF.");
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
            new Messages(Alert.AlertType.INFORMATION, "Chart screenshot saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save screenshot", e);
            new Messages(Alert.AlertType.ERROR, "Unable to save the chart screenshot.");
        }
    }

    private void initializeEventHandlers() {
        if (eventHandlersInitialized) {
            return;
        }

        resetMouseHandler = event -> {
            mousePrevX = -1;
            mousePrevY = -1;
        };

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
                int candleSteps = (int) (dragDistance / candleWidth);

                if (candleSteps != 0) {
                    moveAlongX(-candleSteps, true);
                    dragStartX = event.getSceneX();
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

        canvasZoomHandler = event -> {
            if (disposed || paging || progressIndicator.isVisible() || isCanvasReadyForDrawing()) {
                return;
            }

            if (!event.isControlDown() || event.getTouchCount() != 0 || event.isInertia() || event.getDeltaY() == 0) {
                return;
            }

            if (event.getDeltaY() < 0) {
                zoomOut();
            } else {
                zoomIn();
            }

            event.consume();
        };

        inputEventNode = canvas.getParent();
        if (inputEventNode != null) {
            inputEventNode.addEventFilter(MouseEvent.MOUSE_RELEASED, resetMouseHandler);
            inputEventNode.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            inputEventNode.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }

        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        canvas.addEventFilter(ScrollEvent.SCROLL, canvasZoomHandler);
        eventHandlersInitialized = true;
    }

    private void detachInputHandlers() {
        if (canvas != null) {
            if (mousePressedHandler != null) {
                canvas.removeEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
            }
            if (mouseDraggedHandler != null) {
                canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            }
            if (mouseReleasedHandler != null) {
                canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
            }
            if (canvasZoomHandler != null) {
                canvas.removeEventFilter(ScrollEvent.SCROLL, canvasZoomHandler);
            }
            canvas.setOnMouseMoved(null);
            canvas.setOnMouseExited(null);
        }

        if (inputEventNode != null) {
            if (resetMouseHandler != null) {
                inputEventNode.removeEventFilter(MouseEvent.MOUSE_RELEASED, resetMouseHandler);
            }
            if (mouseDraggedHandler != null) {
                inputEventNode.removeEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            }
            if (keyHandler != null) {
                inputEventNode.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
            inputEventNode = null;
        }

        eventHandlersInitialized = false;
    }

    private void zoomIn() {
        changeZoom(ZoomDirection.IN);
    }

    private void zoomOut() {
        changeZoom(ZoomDirection.OUT);
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            if (disposed || isCanvasReadyForDrawing() || currZoomLevel == null || data.isEmpty()) {
                return;
            }
            chartWidth = Math.max(300, Math.floor(containerWidth.getValue().doubleValue() / candleWidth) *
                    candleWidth - 60 + ((double) candleWidth / 2));
            chartHeight = Math.max(300, containerHeight.getValue().doubleValue());
            canvas.setWidth(clampCanvasDimension(chartWidth - 100));
            canvas.setHeight(clampCanvasDimension(chartHeight - 100));

            // Because the chart has been resized, the number of visible candles has changed, and thus we must
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
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    rebuildExtremaMap(currZoomLevel, candleData);
                    runLaterIfAlive(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();
                        layoutChart();
                        drawChartContents(true);
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
            } else {
                List<CandleData> candleData = new ArrayList<>(data.values());
                rebuildExtremaMap(currZoomLevel, candleData);
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChartContents(true);
            }
        }
    }

    private void initializeInProgressCandle(List<CandleData> candleData, CandleData currentCandle) {
        CandleData lastClosedCandle = candleData.getLast();
        boolean placeholder = currentCandle == null;

        inProgressCandle.setOpenTime(lastClosedCandle.getOpenTime() + secondsPerCandle);
        inProgressCandle.setOpenPrice(placeholder ? lastClosedCandle.getClosePrice() : currentCandle.getOpenPrice());
        inProgressCandle.setHighPriceSoFar(placeholder ? lastClosedCandle.getClosePrice() : currentCandle.getHighPrice());
        inProgressCandle.setLowPriceSoFar(placeholder ? lastClosedCandle.getClosePrice() : currentCandle.getLowPrice());
        inProgressCandle.setVolumeSoFar(placeholder ? 0 : currentCandle.getVolume());
        inProgressCandle.setLastPrice(placeholder ? lastClosedCandle.getClosePrice() : currentCandle.getClosePrice());
        inProgressCandle.setCloseTime((int) Instant.now().getEpochSecond());
        inProgressCandle.setIsPlaceholder(placeholder);
    }

    private List<CandleData> trailingCandles(List<CandleData> candleData, int desiredSize) {
        int safeSize = Math.max(desiredSize, 1);
        int fromIndex = Math.max(candleData.size() - safeSize, 0);
        return candleData.subList(fromIndex, candleData.size());
    }

    private int effectiveWindowSize(List<CandleData> candles, int requestedWindowSize) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.min(requestedWindowSize, candles.size()));
    }

    private List<CandleData> tailCandlesForExtrema(List<CandleData> candles, int requestedWindowSize) {
        int windowSize = effectiveWindowSize(candles, requestedWindowSize);
        if (windowSize == 0) {
            return List.of();
        }
        return trailingCandles(candles, windowSize);
    }

    private void rebuildExtremaMap(ZoomLevel zoomLevel, List<CandleData> candles) {
        if (zoomLevel == null || candles == null || candles.isEmpty()) {
            return;
        }

        int windowSize = effectiveWindowSize(candles, zoomLevel.getNumVisibleCandles());
        Map<Integer, Pair<Extrema, Extrema>> extremaMap = zoomLevel.getExtremaForCandleRangeMap();
        extremaMap.clear();
        putSlidingWindowExtrema(extremaMap, candles, windowSize);
        putExtremaForRemainingElements(extremaMap, tailCandlesForExtrema(candles, windowSize));
    }

    private void setInitialState(List<CandleData> candleData) {
        if (disposed) {
            return;
        }
        hideChartStatus();
        if (liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        this.candleData = new ArrayList<>(candleData);

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(this, 0, candleWidth, secondsPerCandle, canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                candleData.getFirst().getOpenTime());
        zoomLevelMap.put(0L, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());

        data.putAll(candleData.stream().collect(Collectors.toMap(m -> (long) m.getOpenTime(), Function.identity())));
        rebuildExtremaMap(currZoomLevel, candleData);
        setYAndExtraAxisBounds();
        refreshBidAskSnapshot();
        inProgressCandleLastDraw = liveSyncing ? inProgressCandle.getOpenTime() : -1;
        drawChartContents(true);

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

            if (Desktop.isDesktopSupported()) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(outputFile.getAbsolutePath()), null);
                } catch (IllegalStateException e) {
                    logger.debug("Clipboard unavailable while sharing chart snapshot", e);
                }
            }

            logger.info("Chart snapshot saved at: {}", outputFile.getAbsolutePath());
            new Messages(Alert.AlertType.INFORMATION,
                    "Chart snapshot saved to:\n" + outputFile.getAbsolutePath() + "\n\nThe file path was copied to your clipboard when possible.");

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            new Messages(Alert.AlertType.ERROR, "Unable to prepare the chart snapshot for sharing.");
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

        double price = mapYToPrice(mouseY);
        long timestamp = mapXToEpoch(mouseX);

        priceLabel.setText(String.format("%.2f", price));
        priceLabel.setLayoutX(Math.max(12, canvas.getWidth() - 92));
        priceLabel.setLayoutY(Math.max(8, Math.min(mouseY - 12, canvas.getHeight() - 34)));

        timeLabel.setText(CROSSHAIR_TIME_FORMATTER.format(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
        ));
        timeLabel.setLayoutX(Math.max(12, Math.min(mouseX - 42, canvas.getWidth() - 120)));
        timeLabel.setLayoutY(Math.max(8, canvas.getHeight() - 30));

        priceLabel.setVisible(true);
        timeLabel.setVisible(true);
    }

    private void setupCrosshairOverlay() {
        verticalCrosshair = new Line();
        horizontalCrosshair = new Line();
        priceLabel = new Label();
        timeLabel = new Label();

        verticalCrosshair.setStroke(CROSSHAIR_COLOR);
        verticalCrosshair.setStrokeWidth(1);
        verticalCrosshair.setMouseTransparent(true);
        verticalCrosshair.getStrokeDashArray().setAll(5d, 6d);

        horizontalCrosshair.setStroke(CROSSHAIR_COLOR);
        horizontalCrosshair.setStrokeWidth(1);
        horizontalCrosshair.setMouseTransparent(true);
        horizontalCrosshair.getStrokeDashArray().setAll(5d, 6d);

        priceLabel.setFont(Font.font(FXUtils.getMonospacedFont(), FontWeight.SEMI_BOLD, 12));
        priceLabel.setTextFill(CHART_TEXT_PRIMARY);
        priceLabel.setStyle(
                "-fx-background-color: rgba(8,18,29,0.92); " +
                        "-fx-border-color: rgba(148,163,184,0.25); " +
                        "-fx-border-radius: 12; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 4 10 4 10;"
        );

        timeLabel.setFont(Font.font(FXUtils.getMonospacedFont(), FontWeight.SEMI_BOLD, 12));
        timeLabel.setTextFill(CHART_TEXT_PRIMARY);
        timeLabel.setStyle(
                "-fx-background-color: rgba(8,18,29,0.92); " +
                        "-fx-border-color: rgba(148,163,184,0.25); " +
                        "-fx-border-radius: 12; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 4 10 4 10;"
        );

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
            if (!ready || disposed) {
                return;
            }


            int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);
            boolean candleRolled = false;


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
                inProgressCandle.setLowPriceSoFar(Math.min(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandle.getLowPriceSoFar()),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setCloseTime(currentTill);
                inProgressCandle.setLastPrice(currentCandleTrades.getLast()
                        .getPrice());
                data.put((long) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().orElse(inProgressCandle.getLastPrice()));
                    inProgressCandle.setLowPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandle.getLastPrice()));
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                }
                inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setCloseTime(currentTill);
                }

                data.put((long) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
                candleRolled = true;
            }

            if (candleRolled) {
                NavigableMap<Long, CandleData> closedCandles = data.headMap((long) inProgressCandle.getOpenTime(), false);
                if (!closedCandles.isEmpty()) {
                    CandleData latest = closedCandles.lastEntry().getValue();
                    List<CandleData> closedCandleList = new ArrayList<>(closedCandles.values());
                    candleData = closedCandleList;
                    if (aiTradingEnabled) {
                        ensureAutotraderInitialized();
                        ai.onNewCandle(latest);
                    }
                    if (LIVE_BACKTEST_ENABLED) {
                        new InvestProAIBacktester().runBacktest(closedCandleList);
                    }
                }
            }

            refreshBidAskSnapshot();


            //Execute AI Training and Trade Analysis

            runLaterIfAlive(() -> {
                if (disposed) {
                    return;
                }
                setYAndExtraAxisBounds();
                advanceViewportForLiveSync();
                drawChartContents(true);
            });




        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        CandleStickChart chart;

        public CandlePageConsumer(CandleStickChart chart) {
            this.chart = chart;
        }

        @Override
        public void accept(List<CandleData> candleData) {
            if (disposed) {
                return;
            }
            if (Platform.isFxApplicationThread()) {
                throw new IllegalStateException("Candle data paging must not happen on FX thread!");
            }

            List<CandleData> normalizedCandleData = normalizeCandleData(candleData);
            if (normalizedCandleData.isEmpty()) {
                logger.warn("No usable candle data returned for {}", tradePair);
                showChartStatus("No candle data available. Check credentials, permissions, or market availability.");
                return;
            }

            List<CandleData> renderableCandleData = ensureRenderableCandleData(normalizedCandleData);
            if (renderableCandleData.size() < 2) {
                logger.warn("Only {} candle(s) were returned for {}", normalizedCandleData.size(), tradePair);
                showChartStatus("Not enough chart data returned by the exchange to draw candles yet.");
                return;
            }

            if (normalizedCandleData.size() < 2) {
                logger.warn("Only {} candle(s) were returned for {}. Using a synthetic warm-up candle so the chart can render.", normalizedCandleData.size(), tradePair);
            }

            long lastCandleOpenTime = renderableCandleData.getLast().getOpenTime();
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
                initializeInProgressCandle(renderableCandleData, null);
                runLaterIfAlive(() -> setInitialState(new ArrayList<>(renderableCandleData)));
                return;
            }

            future.whenComplete((optionalCandle, throwable) -> {
                if (disposed) {
                    return;
                }
                if (throwable != null) {
                    logger.error("Error fetching in-progress candle data: ", throwable);
                    initializeInProgressCandle(renderableCandleData, null);
                    runLaterIfAlive(() -> setInitialState(new ArrayList<>(renderableCandleData)));
                    return;
                }

                if (optionalCandle.isEmpty()) {
                    logger.info("No in-progress candle data available; using the last closed candle as a placeholder.");
                    initializeInProgressCandle(renderableCandleData, null);
                    runLaterIfAlive(() -> setInitialState(new ArrayList<>(renderableCandleData)));
                    return;
                }

                Optional<?> currentCandleResult = optionalCandle.stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(Optional.empty());

                CandleData inProgressCandleData = currentCandleResult
                        .filter(CandleData.class::isInstance)
                        .map(CandleData.class::cast)
                        .orElse(null);

                initializeInProgressCandle(renderableCandleData, inProgressCandleData);
                runLaterIfAlive(() -> setInitialState(new ArrayList<>(renderableCandleData)));
            });

            // Calculate extrema within sliding window range
            long from = (long) currZoomLevel.getMinXValue();
            long to = from + ((long) currZoomLevel.getNumVisibleCandles() * secondsPerCandle);

            TreeMap<Long, CandleData> extremaData = new TreeMap<>(data.subMap(from, to));

            List<CandleData> combinedData = new ArrayList<>(renderableCandleData);
            combinedData.addAll(extremaData.values());

            rebuildExtremaMap(currZoomLevel, combinedData);

            // Update final candle data in internal map
            data.putAll(renderableCandleData.stream().collect(Collectors.toMap(
                    candle -> (long) candle.getOpenTime(),
                    Function.identity()
            )));

            currZoomLevel.setMinXValue(renderableCandleData.getFirst().getOpenTime());
        }
    }

    private List<CandleData> normalizeCandleData(List<CandleData> candleData) {
        if (candleData == null) {
            return List.of();
        }

        return candleData.stream()
                .filter(Objects::nonNull)
                .filter(candle -> candle.getOpenTime() > 0)
                .collect(Collectors.toMap(
                        CandleData::getOpenTime,
                        Function.identity(),
                        (_, replacement) -> replacement,
                        TreeMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private List<CandleData> ensureRenderableCandleData(List<CandleData> candleData) {
        if (candleData.size() != 1) {
            return candleData;
        }

        CandleData lastCandle = candleData.getFirst();
        int syntheticOpenTime = Math.max(1, lastCandle.getOpenTime() - secondsPerCandle);
        if (syntheticOpenTime == lastCandle.getOpenTime()) {
            return candleData;
        }

        CandleData syntheticCandle = new CandleData(
                lastCandle.getOpenPrice(),
                lastCandle.getClosePrice(),
                lastCandle.getHighPrice(),
                lastCandle.getLowPrice(),
                syntheticOpenTime,
                0,
                lastCandle.getAveragePrice(),
                0,
                true
        );

        List<CandleData> paddedCandles = new ArrayList<>(2);
        paddedCandles.add(syntheticCandle);
        paddedCandles.add(lastCandle);
        return paddedCandles;
    }

    private void showChartStatus(String message) {
        runLaterIfAlive(() -> {
            if (disposed) {
                return;
            }
            chartStatusLabel.setText(message);
            chartStatusLabel.setManaged(true);
            chartStatusLabel.setVisible(true);
            progressIndicator.setVisible(false);
            if (updateInProgressCandleTask != null) {
                updateInProgressCandleTask.setReady(false);
            }
        });
    }

    private void showChartNotification(String message) {
        runLaterIfAlive(() -> {
            if (disposed || chartStatusLabel == null) {
                return;
            }

            chartStatusLabel.setText(message);
            chartStatusLabel.setManaged(true);
            chartStatusLabel.setVisible(true);

            if (chartNotificationTransition != null) {
                chartNotificationTransition.stop();
            }

            chartNotificationTransition = new PauseTransition(Duration.seconds(2.5));
            chartNotificationTransition.setOnFinished(_ -> hideChartStatus());
            chartNotificationTransition.playFromStart();
        });
    }

    private void runLaterIfAlive(Runnable task) {
        FxLifecycle.runLaterIf(() -> !disposed, task);
    }

    private void hideChartStatus() {
        if (chartStatusLabel == null) {
            return;
        }
        chartStatusLabel.setManaged(false);
        chartStatusLabel.setVisible(false);
        chartStatusLabel.setText("");
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

            private boolean hitFirstNonPlaceHolder;

            CandleDataPreProcessor(CandleStickChart candleStickChart) {
                this.candleStickChart = candleStickChart;
                candleData = new ArrayList<>();
            }
            List<CandleData> candleData;
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
