package org.investpro.ui.charts;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
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
import javafx.util.Duration;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.investpro.data.CandleData;
import org.investpro.data.CandleDataPager;
import org.investpro.data.InProgressCandle;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.Exchange;

import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.ChartContainer;
import org.investpro.ui.tools.InstantAxisFormatter;
import org.investpro.ui.tools.MoneyAxisFormatter;
import org.investpro.ui.tools.PriceLine;
import org.investpro.ui.tools.StableTicksAxis;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.Extrema;
import org.investpro.utils.FXUtils;
import org.investpro.utils.LogOnExceptionThreadFactory;
import org.investpro.utils.ZoomDirection;
import org.investpro.utils.ZoomLevel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.ui.charts.CandleStickChartUtils.getXAxisFormatterForRange;
import static org.investpro.ui.charts.CandleStickChartUtils.putExtremaForRemainingElements;
import static org.investpro.ui.charts.CandleStickChartUtils.putSlidingWindowExtrema;
import static org.investpro.ui.charts.ChartColors.AXIS_TICK_LABEL_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_FILL_COLOR;

/**
 * Canvas-based candlestick chart.

 * Designed for:
 * - fast candle rendering
 * - mouse panning
 * - wheel zooming
 * - lazy historical paging
 * - optional live in-progress candle updates

 * This class should be created by {@link ChartContainer}.
 */
@Getter
public class CandleStickChart extends Region {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);

    private static final DecimalFormat MARKER_FORMAT = new DecimalFormat("#.00000");
    private static final long LOADING_TIMEOUT_MS = 30_000L;
    private static final int MIN_CHART_WIDTH = 320;
    private static final int MIN_CHART_HEIGHT = 240;
    private static final int AXIS_RESERVED_WIDTH = 100;

    private static final int MIN_PRICE_AXIS_WIDTH = 72;
    private static final int VOLUME_AXIS_WIDTH = 64;
    private static final int DEFAULT_TIME_AXIS_HEIGHT = 32;
    private static final double VOLUME_AREA_RATIO = 0.22;
    private static final int DEFAULT_CANDLE_WIDTH = ChartScalingStrategy.DEFAULT_CANDLE_BODY_WIDTH;
    private static final int HEADER_HEIGHT = 54;
    private static final int PRICE_BADGE_WIDTH = 92;
    private static final int PRICE_BADGE_HEIGHT = 22;

    private static final DateTimeFormatter CROSSHAIR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
    private static final DateTimeFormatter SCREENSHOT_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private boolean autoTradeEnabled = false;

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final int secondsPerCandle;

    private final CandleDataPager candleDataPager;
    private final CandleStickChartOptions chartOptions;
    private final NavigableMap<Integer, CandleData> data;
    private final Map<Integer, ZoomLevel> zoomLevelMap;

    private final StableTicksAxis xAxis;
    private final StableTicksAxis yAxis;
    private final StableTicksAxis extraAxis;

    private final ProgressIndicator progressIndicator;
    private final Text loadingStatusText;
    private final VBox loadingIndicatorContainer;
    private final Line extraAxisExtension;

    private final Consumer<List<CandleData>> candlePageConsumer;

    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private final EventHandler<ScrollEvent> scrollHandler;
    private final EventHandler<KeyEvent> keyHandler;

    private final Font canvasNumberFont;

    private final InProgressCandle inProgressCandle = new InProgressCandle();
    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    private final ScheduledExecutorService updateInProgressCandleExecutor;

    private final ScheduledExecutorService chartLoadingExecutor =
            Executors.newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-DATA-LOADER"));

    private final ScheduledExecutorService chartTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-LOADING-TIMEOUT"));

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean drawRequested = new AtomicBoolean(false);
    private final AtomicBoolean eventFiltersInstalled = new AtomicBoolean(false);
    private final PauseTransition redrawDebouncer = new PauseTransition(Duration.millis(16));
    private final PauseTransition noticeClearTimer = new PauseTransition(Duration.seconds(2.2));

    private volatile long loadingStartTime = -1L;
    private ZoomLevel currZoomLevel;
    private volatile boolean paging;
    private volatile boolean disposed;

    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private StackPane chartStackPane;

    private int candleWidth = DEFAULT_CANDLE_WIDTH;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;
    private double chartWidth = 1000;
    private double chartHeight = 700;
    private int inProgressCandleLastDraw = -1;

    private double crosshairMouseX = -1;
    private double crosshairMouseY = -1;
    private boolean showCrosshair = true;
    private double hoveredPrice = -1;
    private long hoveredTime = -1;

    private final List<PriceLine> priceLines = new ArrayList<>();
    private boolean showPriceLines = true;

    private List<Trade> currentCandleTrades = Collections.emptyList();

    private ChangeListener<Number> activeSizeListener;
    private ChangeListener<Boolean> activeFirstSizeListener;
    private int visibleCandles;

    public CandleStickChart(
            Exchange exchange,
            CandleDataSupplier candleDataSupplier,
            TradePair tradePair,
            boolean liveSyncing,
            int secondsPerCandle,
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight
    ) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(candleDataSupplier, "candleDataSupplier must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException(
                    "CandleStickChart must be constructed on the JavaFX Application Thread, but was called from %s"
                            .formatted(Thread.currentThread())
            );
        }

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = Math.max(1, secondsPerCandle);
        this.liveSyncing = liveSyncing;

        this.zoomLevelMap = new ConcurrentHashMap<>();
        this.data = Collections.synchronizedNavigableMap(new TreeMap<>());
        this.chartOptions = new CandleStickChartOptions();
        this.candleDataPager = new CandleDataPager(this, candleDataSupplier);
        this.canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 14);

        this.progressIndicator = new ProgressIndicator(-1);
        this.loadingStatusText = new Text("Loading...");
        this.loadingIndicatorContainer = new VBox(8, progressIndicator, loadingStatusText);
        this.extraAxisExtension = new Line();

        this.xAxis = new StableTicksAxis();
        this.yAxis = new StableTicksAxis();
        this.extraAxis = new StableTicksAxis();

        configureStyle();
        configureAxes();
        configureLoadingOverlay();
        configureAxisExtension();

        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);

        if (liveSyncing) {
            this.updateInProgressCandleTask = new UpdateInProgressCandleTask();
            this.updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                    new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE")
            );
        } else {
            this.updateInProgressCandleTask = null;
            this.updateInProgressCandleExecutor = null;
        }

        this.candlePageConsumer = new CandlePageConsumer();
        this.mouseDraggedHandler = new MouseDraggedHandler();
        this.scrollHandler = new ScrollEventHandler();
        this.keyHandler = new KeyEventHandler();

        initializeFirstLayout(containerWidth, containerHeight);
        initializeOptionListeners();
        noticeClearTimer.setOnFinished(event -> {
            if (!loading.get() && !paging) {
                loadingStatusText.setText("");
            }
        });

        if (liveSyncing) {
            initializeLiveSyncing();
        }
    }

    private void configureStyle() {
        getStyleClass().add("candle-chart");
        setFocusTraversable(true);
    }

    private void configureAxes() {
        String axisStyle = "-fx-text-fill: #cbd5e1; -fx-border-color: #273244; -fx-background-color: #0b1220;";
        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 12);

        xAxis.setManaged(false);
        yAxis.setManaged(false);
        extraAxis.setManaged(false);
        extraAxisExtension.setManaged(false);

        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        extraAxis.setAutoRanging(false);

        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        extraAxis.setForceZeroInRange(false);

        xAxis.setSide(Side.BOTTOM);
        yAxis.setSide(Side.RIGHT);
        extraAxis.setSide(Side.LEFT);

        xAxis.configureForPriceAxis();
        yAxis.configureForPriceAxis();
        extraAxis.configureForVolumeAxis();

        extraAxis.setOpacity(1.0);
        extraAxis.setMouseTransparent(true);
        extraAxis.setTickLabelsVisible(true);
        extraAxis.setTickMarkVisible(true);
        extraAxis.setMinorTickVisible(false);
        extraAxis.setTickLength(4);

        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));

        xAxis.setStyle(axisStyle);
        yAxis.setStyle(axisStyle);
        extraAxis.setStyle(axisStyle);

        xAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);
        yAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);
        extraAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);

        xAxis.setTickLabelFont(axisFont);
        yAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);
    }

    private void configureLoadingOverlay() {
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);

        progressIndicator.setPrefSize(42, 42);
        progressIndicator.setVisible(false);

        loadingStatusText.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        loadingStatusText.setFill(Color.web("#cbd5e1"));
        loadingStatusText.setMouseTransparent(true);
    }

    private void configureAxisExtension() {
        Paint lineColor = Color.web("#475569");
        extraAxisExtension.setFill(lineColor);
        extraAxisExtension.setStroke(lineColor);
        extraAxisExtension.setSmooth(false);
        extraAxisExtension.setStrokeWidth(1);
        extraAxisExtension.setStartX(0);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndX(0);
        extraAxisExtension.setEndY(chartHeight -100);
    }

    private void initializeFirstLayout(
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight
    ) {
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);

        ChangeListener<Number> sizeListener = new SizeChangeListener(
                gotFirstSize,
                containerWidth,
                containerHeight
        );

        activeSizeListener = sizeListener;
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        ChangeListener<Boolean> gotFirstSizeChangeListener = (observable, oldValue, newValue) -> {
            if (!Boolean.TRUE.equals(newValue)) {
                return;
            }

            double width = Math.max(MIN_CHART_WIDTH, containerWidth.getValue().doubleValue());
            double height = Math.max(MIN_CHART_HEIGHT, containerHeight.getValue().doubleValue());

            chartWidth = normalizedChartWidth(width);
            chartHeight = Math.max(MIN_CHART_HEIGHT, height);

            canvas = new Canvas(chartWidth, chartHeight);

            canvas.widthProperty().addListener(sizeListener);
            canvas.heightProperty().addListener(sizeListener);

            chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
            chartStackPane.setManaged(false);

            getChildren().addFirst(chartStackPane);

            canvas.setOnMouseEntered(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.HAND);
                }
            });
            canvas.setOnMouseExited(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.DEFAULT);
                }
            });

            graphicsContext = canvas.getGraphicsContext2D();

            try {
                layoutChart();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            initializeEventHandlers();
            startChartDataLoading();

            gotFirstSize.removeListener(activeFirstSizeListener);
            activeFirstSizeListener = null;
        };

        activeFirstSizeListener = gotFirstSizeChangeListener;
        gotFirstSize.addListener(gotFirstSizeChangeListener);
    }

    private void initializeOptionListeners() {
        redrawDebouncer.setOnFinished(event -> {
            drawRequested.set(false);
            drawChartContents(true);
        });

        chartOptions.horizontalGridLinesVisibleProperty().addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.verticalGridLinesVisibleProperty().addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.showVolumeProperty().addListener((observable, oldValue, newValue) -> {
            try {
                layoutChart();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            requestChartRedraw();
        });
        chartOptions.alignOpenCloseProperty().addListener((observable, oldValue, newValue) -> requestChartRedraw());
    }

    private void requestChartRedraw() {
        if (disposed) {
            return;
        }

        runOnFx(() -> {
            if (drawRequested.compareAndSet(false, true)) {
                redrawDebouncer.playFromStart();
            }
        });
    }

    private void initializeLiveSyncing() {
        if (!liveSyncing || updateInProgressCandleExecutor == null || updateInProgressCandleTask == null) {
            return;
        }

        updateInProgressCandleExecutor.execute(() -> {
            if (disposed) {
                return;
            }

            boolean websocketInitialized = false;

            try {
                if (exchange.getWebsocketClient() != null) {
                    websocketInitialized = exchange.getWebsocketClient()
                            .getInitializationLatch()
                            .await(10, SECONDS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for websocket initialization for {}", tradePair, exception);
            } catch (Exception exception) {
                logger.warn("WebSocket initialization check failed for {}", tradePair, exception);
            }

            boolean streamingStarted = false;

            try {
                if (!disposed
                        && websocketInitialized
                        && exchange.getWebsocketClient() != null
                        && exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {

                    exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                    streamingStarted = true;
                    logger.info("Live trade WebSocket connected for {}", tradePair);
                }
            } catch (Exception exception) {
                logger.warn("WebSocket streaming failed for {}; fallback polling will be used.", tradePair, exception);
            }

            if (!disposed && !streamingStarted) {
                logger.info("Using polling fallback for live trades on {}", tradePair);
                startPollingForLiveTrades();
            }

            if (!disposed) {
                updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
            }
        });
    }

    private void initializeEventHandlers() {
        if (canvas == null) {
            return;
        }

        canvas.setFocusTraversable(true);

        canvas.setOnMouseClicked(event -> {
            canvas.requestFocus();

            if (event.getButton() == MouseButton.SECONDARY) {
                toggleCrosshair();
                event.consume();
            }
        });

        if (canvas.getParent() != null) {
            installEventFilters(canvas.getParent());
        } else {
            canvas.parentProperty().addListener((observable, oldParent, newParent) -> {
                if (newParent != null) {
                    installEventFilters(newParent);
                }
            });
        }
    }

    private void installEventFilters(Node node) {
        if (node == null || !eventFiltersInstalled.compareAndSet(false, true)) {
            return;
        }

        node.setFocusTraversable(true);

        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            node.requestFocus();
            if (canvas != null) {
                canvas.requestFocus();
            }
        });

        node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            mousePrevX = -1;
            mousePrevY = -1;
            scrollDeltaXSum = 0;
        });

        node.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        node.addEventFilter(MouseEvent.MOUSE_MOVED, new MouseMovedHandler());

        node.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            crosshairMouseX = -1;
            crosshairMouseY = -1;
            hoveredPrice = -1;
            hoveredTime = -1;
            requestChartRedraw();
        });

        node.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
        node.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
    }

    private double normalizedChartWidth(double containerWidth) {
        return Math.max(MIN_CHART_WIDTH, containerWidth);
    }

    private void moveAlongX(int xDirection, boolean skipDraw) {
        if (xDirection != 1 && xDirection != -1) {
            throw new IllegalArgumentException("xDirection must be 1 or -1 but was: %d".formatted(xDirection));
        }

        runOnFx(() -> {
            if (progressIndicator.isVisible() || currZoomLevel == null || data.isEmpty()) {
                return;
            }

            CandleData lastCandle = lastValue(data);
            if (lastCandle == null) {
                return;
            }

            int desiredXLowerBound = (int) xAxis.getLowerBound()
                    + (xDirection == 1 ? secondsPerCandle : -secondsPerCandle);

            int minCandlesRemaining = 3;
            int latestAllowedLowerBound = lastCandle.getOpenTime()
                    - ((minCandlesRemaining - 1) * secondsPerCandle);

            if (desiredXLowerBound > latestAllowedLowerBound) {
                showTransientNotice("Reached latest candle.");
                return;
            }

            if (desiredXLowerBound <= currZoomLevel.getMinXValue()) {
                int previousMinXValue = currZoomLevel.getMinXValue();
                pageMoreDataThen(() -> {
                    if (desiredXLowerBound <= currZoomLevel.getMinXValue()
                            && currZoomLevel.getMinXValue() >= previousMinXValue) {
                        showTransientNotice("Reached earliest available candle.");
                        return;
                    }
                    setAxisBoundsForMove(xDirection);
                    setYAndExtraAxisBounds();
                    if (!skipDraw) {
                        drawChartContents(true);
                    }
                });
            } else {
                setAxisBoundsForMove(xDirection);
                setYAndExtraAxisBounds();
                if (!skipDraw) {
                    drawChartContents(true);
                }
            }
        });
    }

    private void pageMoreDataThen(Runnable afterPaging) {
        if (disposed || paging) {
            return;
        }

        paging = true;
        progressIndicator.setVisible(true);
        showLoadingStatus("Loading more candles...");

        CompletableFuture
                .supplyAsync(candleDataPager.getCandleDataSupplier(), chartLoadingExecutor)
                .thenAccept(candleDataPager.getCandleDataPreProcessor())
                .whenComplete((result, throwable) -> runOnFx(() -> {
                    try {
                        if (disposed) {
                            return;
                        }

                        if (throwable != null) {
                            logger.error("Exception during chart paging for {}", tradePair, throwable);
                            showErrorMessage("Failed to load more data: %s".formatted(rootMessage(throwable)));
                        } else if (afterPaging != null) {
                            afterPaging.run();
                        }
                    } finally {
                        hideLoadingIndicator();
                        paging = false;
                    }
                }));
    }

    private void setAxisBoundsForMove(int xDirection) {
        if (xDirection == 1) {
            xAxis.setUpperBound(xAxis.getUpperBound() + secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() + secondsPerCandle);
        } else if (xDirection == -1) {
            xAxis.setUpperBound(xAxis.getUpperBound() - secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() - secondsPerCandle);
        } else {
            throw new IllegalArgumentException("xDirection must be 1 or -1 but was: %d".formatted(xDirection));
        }
    }

    private void setYAndExtraAxisBounds() {
        if (currZoomLevel == null || data.isEmpty()) {
            return;
        }

        int extremaKey = ((int) xAxis.getLowerBound()) - secondsPerCandle;
        Pair<Extrema<Double>, Extrema<Double>> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(extremaKey);

        if (extremaForRange == null) {
            logger.debug("Missing extrema for key {}. Computing visible fallback.", extremaKey);
            extremaForRange = computeVisibleExtremaFallback();
        }

        if (extremaForRange == null) {
            return;
        }

        Extrema<Double> volumeExtrema = extremaForRange.getKey();
        Extrema<Double> priceExtrema = extremaForRange.getValue();

        double yAxisMax = priceExtrema.getMax();
        double yAxisMin = priceExtrema.getMin();
        double yAxisDelta = yAxisMax - yAxisMin;

        if (!Double.isFinite(yAxisDelta) || yAxisDelta <= 0) {
            yAxisDelta = Math.max(Math.abs(yAxisMax) * 0.01, 0.00000001);
        }

        double yAxisPadding = yAxisDelta * 0.10;

        yAxis.setUpperBound(yAxisMax + yAxisPadding);
        double lowerBound = yAxisMin - yAxisPadding;
        yAxis.setLowerBound(yAxisMin >= 0.0 ? Math.max(0.0, lowerBound) : lowerBound);

        double volumeMax = Math.max(1.0, volumeExtrema.getMax());
        extraAxis.setUpperBound(volumeMax);
        extraAxis.setLowerBound(0.0);
    }

    private @Nullable Pair<Extrema<Double>, Extrema<Double>> computeVisibleExtremaFallback() {
        if (data.isEmpty()) {
            return null;
        }

        int lower = (int) xAxis.getLowerBound();
        int upper = (int) xAxis.getUpperBound();

        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = Double.NEGATIVE_INFINITY;
        double maxVolume = 0.0;

        synchronized (data) {
            for (CandleData candle : data.subMap(lower, true, upper, true).values()) {
                if (candle == null || candle.isPlaceHolder()) {
                    continue;
                }

                minPrice = Math.min(minPrice, candle.getLowPrice());
                maxPrice = Math.max(maxPrice, candle.getHighPrice());
                maxVolume = Math.max(maxVolume, candle.getVolume());
            }

            if (!Double.isFinite(minPrice) || !Double.isFinite(maxPrice)) {
                for (CandleData candle : data.values()) {
                    if (candle == null || candle.isPlaceHolder()) {
                        continue;
                    }

                    minPrice = Math.min(minPrice, candle.getLowPrice());
                    maxPrice = Math.max(maxPrice, candle.getHighPrice());
                    maxVolume = Math.max(maxVolume, candle.getVolume());
                }
            }
        }

        if (!Double.isFinite(minPrice) || !Double.isFinite(maxPrice)) {
            minPrice = 0.0;
            maxPrice = 100.0;
        }

        return new Pair<>(
                new Extrema<>(0.0, Math.max(1.0, maxVolume)),
                new Extrema<>(minPrice, maxPrice)
        );
    }

    private void layoutChart() throws SQLException, ClassNotFoundException {
        if (canvas == null || graphicsContext == null || chartStackPane == null) {
            return;
        }

        double top = snapPositionY(snappedTopInset());
        double volumeAxisX = snapPositionX(snappedLeftInset());

        double xAxisHeight = Math.ceil(Math.max(DEFAULT_TIME_AXIS_HEIGHT, xAxis.prefHeight(chartWidth)));
        double yAxisWidth = Math.ceil(clamp(
                yAxis.prefWidth(Math.max(1, chartHeight - xAxisHeight)),
                MIN_PRICE_AXIS_WIDTH,
                AXIS_RESERVED_WIDTH
        ));
        double volumeAxisWidth = chartOptions.isShowVolume() ? VOLUME_AXIS_WIDTH : 0.0;
        double canvasLeft = volumeAxisX + volumeAxisWidth;
        double canvasWidth = Math.max(1, chartWidth - yAxisWidth - volumeAxisWidth);
        double canvasHeight = Math.max(1, chartHeight - xAxisHeight);
        double volumeAxisHeight = Math.max(48.0, canvasHeight * VOLUME_AREA_RATIO);
        double volumeAxisY = top + canvasHeight - volumeAxisHeight;

        canvas.setWidth(canvasWidth);
        canvas.setHeight(canvasHeight);

        chartStackPane.resizeRelocate(canvasLeft, top, canvasWidth, canvasHeight);
        chartStackPane.setMinSize(canvasWidth, canvasHeight);
        chartStackPane.setPrefSize(canvasWidth, canvasHeight);
        chartStackPane.setMaxSize(canvasWidth, canvasHeight);

        clearCanvas();

        double priceAxisX = canvasLeft + canvasWidth;

        extraAxisExtension.setStartX(volumeAxisX);
        extraAxisExtension.setEndX(volumeAxisX);
        extraAxisExtension.setStartY(volumeAxisY);
        extraAxisExtension.setEndY(top + canvasHeight);

        xAxis.resizeRelocate(canvasLeft, top + canvasHeight, canvasWidth, xAxisHeight);
        yAxis.resizeRelocate(priceAxisX, top, yAxisWidth, canvasHeight);
        extraAxis.resizeRelocate(volumeAxisX, volumeAxisY, volumeAxisWidth, volumeAxisHeight);
        extraAxis.setTranslateY(0);
        extraAxis.setVisible(chartOptions.isShowVolume());
        extraAxisExtension.setVisible(chartOptions.isShowVolume());


        layoutAxes();



    }

    private void layoutAxes() {
        xAxis.requestAxisLayout();
        yAxis.requestAxisLayout();
        extraAxis.requestAxisLayout();

        xAxis.layout();
        yAxis.layout();
        extraAxis.layout();
    }

    private void clearCanvas() {
        if (graphicsContext == null || canvas == null) {
            return;
        }
        graphicsContext.setFill(Color.web("#050b14"));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawChartContents(boolean clearCanvas) {
        if (!Platform.isFxApplicationThread()) {
            runOnFx(() -> drawChartContents(clearCanvas));
            return;
        }

        if (disposed || canvas == null || graphicsContext == null || currZoomLevel == null || data.isEmpty()) {
            return;
        }

        layoutAxes();

        CandleData lastCandle = lastValue(data);
        if (lastCandle == null) {
            return;
        }

        int numCandlesToSkip = Math.max(
                ((int) xAxis.getUpperBound() - (lastCandle.getOpenTime() + secondsPerCandle)) / secondsPerCandle,
                0
        );

        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            if (xAxis.getUpperBound() >= inProgressCandleLastDraw
                    && xAxis.getUpperBound() < inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)) {
                if (numCandlesToSkip == 0) {
                    moveAlongX(1, true);
                    numCandlesToSkip = Math.max(
                            ((int) xAxis.getUpperBound() - lastCandle.getOpenTime()) / secondsPerCandle,
                            0
                    );
                }
            }
            inProgressCandleLastDraw = inProgressCandle.getOpenTime();
        }

        if (clearCanvas) {
            clearCanvas();
        }

        double yRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        if (!Double.isFinite(yRange) || yRange <= 0.0) {
            yRange = 1.0;
        }
        double monetaryUnitsPerPixel = yRange / Math.max(1.0, canvas.getHeight());
        double pixelsPerMonetaryUnit = 1.0 / monetaryUnitsPerPixel;

        int visibleCandles = Math.max(1, (int) currZoomLevel.getNumVisibleCandles());

        int fromKey = ((int) xAxis.getUpperBound() - secondsPerCandle) - (visibleCandles * secondsPerCandle);
        int toKey = ((int) xAxis.getUpperBound() - secondsPerCandle) - (numCandlesToSkip * secondsPerCandle);

        NavigableMap<Integer, CandleData> candlesToDraw;
        synchronized (data) {
            candlesToDraw = new TreeMap<>(data.subMap(fromKey, true, toKey, true));
        }

        if (candlesToDraw.isEmpty()) {
            drawNoDataOverlay();
            return;
        }

        drawTradingBackground();
        drawGridLines();

        int candleIndex = numCandlesToSkip;
        double highestCandleValue = Double.NEGATIVE_INFINITY;
        double lowestCandleValue = Double.POSITIVE_INFINITY;
        int candleIndexOfHighest = -1;
        int candleIndexOfLowest = -1;

        double volumeAreaHeight = canvas.getHeight() * VOLUME_AREA_RATIO;
        double volumeScale = volumeAreaHeight / Math.max(1.0, extraAxis.getUpperBound());
        double lastClose = -1;

        for (CandleData candleDatum : candlesToDraw.descendingMap().values()) {
            if (candleDatum == null) {
                continue;
            }

            if (candleIndex < visibleCandles + 2) {
                if (candleDatum.getHighPrice() > highestCandleValue) {
                    highestCandleValue = candleDatum.getHighPrice();
                    candleIndexOfHighest = candleIndex;
                }

                if (candleDatum.getLowPrice() < lowestCandleValue) {
                    lowestCandleValue = candleDatum.getLowPrice();
                    candleIndexOfLowest = candleIndex;
                }
            }

            drawCandle(candleDatum, candleIndex, pixelsPerMonetaryUnit, volumeScale, lastClose);
            lastClose = candleDatum.getClosePrice();
            candleIndex++;
        }

        drawHighLowMarkers(highestCandleValue, lowestCandleValue, candleIndexOfHighest, candleIndexOfLowest, pixelsPerMonetaryUnit);
        drawChartHeader(lastCandle);

        if (showPriceLines) {
            drawPriceLines(pixelsPerMonetaryUnit);
        }

        drawCurrentPriceBadge(lastCandle, pixelsPerMonetaryUnit);

        if (showCrosshair && crosshairMouseX >= 0 && crosshairMouseY >= 0) {
            drawCrosshair(pixelsPerMonetaryUnit);
        }
    }

    private void drawTradingBackground() {
        graphicsContext.setFill(Color.rgb(10, 14, 23));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        graphicsContext.setFill(Color.rgb(15, 20, 31, 0.86));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), HEADER_HEIGHT);

        graphicsContext.setFill(Color.rgb(6, 10, 18, 0.54));
        graphicsContext.fillRect(0, canvas.getHeight() * (1.0 - VOLUME_AREA_RATIO), canvas.getWidth(), canvas.getHeight() * VOLUME_AREA_RATIO);

        graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.70));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeLine(0, HEADER_HEIGHT, canvas.getWidth(), HEADER_HEIGHT);
        graphicsContext.strokeLine(0, canvas.getHeight() * (1.0 - VOLUME_AREA_RATIO), canvas.getWidth(), canvas.getHeight() * (1.0 - VOLUME_AREA_RATIO));
    }

    private void drawGridLines() {
        if (chartOptions.isHorizontalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.42));
            graphicsContext.setLineWidth(1.0);

            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
            }
        }

        if (chartOptions.isVerticalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.30));
            graphicsContext.setLineWidth(1.0);

            for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
                graphicsContext.strokeLine(tickMark.getPosition(), 0, tickMark.getPosition(), canvas.getHeight());
            }
        }
    }

    private void drawCandle(
            CandleData candleDatum,
            int candleIndex,
            double pixelsPerMonetaryUnit,
            double volumeScale,
            double lastClose
    ) {
        double bodyWidth = Math.max(
                1.0,
                Math.min(candleWidth - (ChartScalingStrategy.CANDLE_SPACING * 2.0), candleWidth * 0.72)
        );
        double bodyX = snapPixel(candleCenterX(candleIndex) - (bodyWidth * 0.5));
        double wickX = snapPixel(bodyX + (bodyWidth * 0.5));

        if (bodyX > canvas.getWidth() || bodyX + bodyWidth < 0) {
            return;
        }

        double candleOpenPrice = candleDatum.getOpenPrice();

        if (chartOptions.isAlignOpenClose() && lastClose != -1) {
            candleOpenPrice = lastClose;
        }

        if (candleDatum.isPlaceHolder()) {
            double candleYOrigin = priceToScreenY(candleOpenPrice, pixelsPerMonetaryUnit);
            graphicsContext.beginPath();
            graphicsContext.rect(bodyX, snapPixel(candleYOrigin), bodyWidth, 1);
            graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
            graphicsContext.fill();
            graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
            graphicsContext.setLineWidth(1);
            graphicsContext.stroke();
            return;
        }

        boolean bearish = candleOpenPrice > candleDatum.getClosePrice();
        Paint candleBorderColor = bearish ? BEAR_CANDLE_BORDER_COLOR : BULL_CANDLE_BORDER_COLOR;
        Paint candleFillColor = bearish ? BEAR_CANDLE_FILL_COLOR : BULL_CANDLE_FILL_COLOR;

        double openY = priceToScreenY(candleOpenPrice, pixelsPerMonetaryUnit);
        double closeY = priceToScreenY(candleDatum.getClosePrice(), pixelsPerMonetaryUnit);
        double highY = priceToScreenY(Math.max(candleDatum.getHighPrice(), Math.max(candleOpenPrice, candleDatum.getClosePrice())), pixelsPerMonetaryUnit);
        double lowY = priceToScreenY(Math.min(candleDatum.getLowPrice(), Math.min(candleOpenPrice, candleDatum.getClosePrice())), pixelsPerMonetaryUnit);

        double bodyTop = snapPixel(Math.min(openY, closeY));
        double bodyBottom = snapPixel(Math.max(openY, closeY));
        double bodyHeight = Math.max(1.0, bodyBottom - bodyTop);

        drawWicks(wickX, highY, lowY, bodyTop, bodyTop + bodyHeight, candleBorderColor);

        graphicsContext.beginPath();
        graphicsContext.rect(bodyX, bodyTop, bodyWidth, bodyHeight);
        graphicsContext.setFill(candleFillColor);
        graphicsContext.fill();
        graphicsContext.setStroke(candleBorderColor);
        graphicsContext.setLineWidth(candleStrokeWidth());
        graphicsContext.stroke();

        if (chartOptions.isShowVolume()) {
            drawVolumeBar(candleDatum, bodyX, bodyWidth, candleFillColor, candleBorderColor, volumeScale);
        }
    }

    private void drawWicks(double wickX, double highY, double lowY, double bodyTop, double bodyBottom, Paint color) {
        graphicsContext.setStroke(color);
        graphicsContext.setLineWidth(candleStrokeWidth());
        graphicsContext.beginPath();

        double snappedWickX = snapPixel(wickX);
        if (highY < bodyTop) {
            graphicsContext.moveTo(snappedWickX, snapPixel(highY));
            graphicsContext.lineTo(snappedWickX, bodyTop);
        }
        if (lowY > bodyBottom) {
            graphicsContext.moveTo(snappedWickX, bodyBottom);
            graphicsContext.lineTo(snappedWickX, snapPixel(lowY));
        }

        graphicsContext.stroke();
    }

    private void drawVolumeBar(CandleData candleDatum, double x, double width, Paint fillColor, Paint borderColor, double volumeScale) {
        double baseline = canvas.getHeight();
        double maxVolumeHeight = canvas.getHeight() * VOLUME_AREA_RATIO;
        double volumeHeight = Math.min(maxVolumeHeight, Math.max(1.0, candleDatum.getVolume() * volumeScale));
        double y = baseline - volumeHeight;

        graphicsContext.beginPath();
        graphicsContext.rect(x, y, width, volumeHeight);
        graphicsContext.setFill(fillColor);
        graphicsContext.fill();
        graphicsContext.setStroke(borderColor);
        graphicsContext.setLineWidth(1);
        graphicsContext.stroke();
    }

    private void drawHighLowMarkers(
            double high,
            double low,
            int highIndex,
            int lowIndex,
            double pixelsPerMonetaryUnit
    ) {
        if (highIndex < 0 || lowIndex < 0 || !Double.isFinite(high) || !Double.isFinite(low)) {
            return;
        }

        graphicsContext.setFont(canvasNumberFont);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(AXIS_TICK_LABEL_COLOR);
        graphicsContext.setFontSmoothingType(FontSmoothingType.LCD);

        double highY = cartesianToScreenY((high - yAxis.getLowerBound()) * pixelsPerMonetaryUnit) - 1;
        double lowY = cartesianToScreenY((low - yAxis.getLowerBound()) * pixelsPerMonetaryUnit) + 1;

        boolean skipLow = lowY - highY < canvasNumberFont.getSize() && highIndex == lowIndex;
        double offset = 20;
        double highX = candleCenterX(highIndex);

        if (highIndex > currZoomLevel.getNumVisibleCandles() * 0.5) {
            graphicsContext.setTextAlign(TextAlignment.LEFT);
            graphicsContext.fillText("<- %s".formatted(MARKER_FORMAT.format(high)), Math.min(highX + offset, canvas.getWidth() - 80), highY);
        } else {
            graphicsContext.setTextAlign(TextAlignment.RIGHT);
            graphicsContext.fillText("%s ->".formatted(MARKER_FORMAT.format(high)), Math.max(highX - offset, 20), highY);
        }

        if (!skipLow) {
            double lowX = candleCenterX(lowIndex);
            if (lowIndex > currZoomLevel.getNumVisibleCandles() * 0.5) {
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                graphicsContext.fillText("<- %s".formatted(MARKER_FORMAT.format(low)), lowX + 2, lowY);
            } else {
                graphicsContext.setTextAlign(TextAlignment.RIGHT);
                graphicsContext.fillText("%s ->".formatted(MARKER_FORMAT.format(low)), lowX - 3, lowY);
            }
        }
    }

    private void drawChartHeader(CandleData lastCandle) {
        if (lastCandle == null) {
            return;
        }

        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), Math.max(28, Math.min(56, canvas.getWidth() / 12))));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(248, 250, 252, 0.08));
        graphicsContext.fillText(tradePair.toString('/'), canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);

        boolean bullish = lastCandle.getClosePrice() >= lastCandle.getOpenPrice();
        Color closeColor = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);

        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        graphicsContext.setFill(Color.rgb(226, 232, 240));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.fillText(tradePair.toString('/'), 14, 18);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(exchange.supportsTimeframe(secondsPerCandle), 112, 18);

        drawHeaderValue("O", lastCandle.getOpenPrice(), 14, Color.rgb(203, 213, 225));
        drawHeaderValue("H", lastCandle.getHighPrice(), 142, Color.rgb(38, 166, 154));
        drawHeaderValue("L", lastCandle.getLowPrice(), 270, Color.rgb(239, 83, 80));
        drawHeaderValue("C", lastCandle.getClosePrice(), 398, closeColor);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText("Vol %s".formatted(compactNumber(lastCandle.getVolume())), 526, 40);
    }

    private void drawHeaderValue(String label, double value, double x, Color color) {
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(label, x, 40);
        graphicsContext.setFill(color);
        graphicsContext.fillText(MARKER_FORMAT.format(value), x + 18, 40);
    }

    private void drawNoDataOverlay() {
        clearCanvas();
        graphicsContext.setFill(Color.web("#94a3b8"));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 16));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText("No visible candle data", canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
    }

    private void drawPriceLines(double pixelsPerMonetaryUnit) {
        if (priceLines.isEmpty()) {
            return;
        }

        for (PriceLine priceLine : List.copyOf(priceLines)) {
            if (!priceLine.isVisible()) {
                continue;
            }

            double screenY = cartesianToScreenY((priceLine.getPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
            if (screenY < 0 || screenY > canvas.getHeight()) {
                continue;
            }

            graphicsContext.setStroke(priceLine.getColor());
            graphicsContext.setLineWidth(priceLine.getLineWidth());

            if (priceLine.isDashed()) {
                graphicsContext.setLineDashes(5, 5);
            }

            graphicsContext.strokeLine(0, screenY, canvas.getWidth(), screenY);

            if (priceLine.isLabelVisible()) {
                graphicsContext.setFill(priceLine.getColor());
                graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                String label = priceLine.getLabel() == null || priceLine.getLabel().isBlank()
                        ? MARKER_FORMAT.format(priceLine.getPrice())
                        : "%s %s".formatted(priceLine.getLabel(), MARKER_FORMAT.format(priceLine.getPrice()));
                graphicsContext.fillText(label, 5, screenY - 3);
            }

            graphicsContext.setLineDashes();
        }
    }

    private void drawCurrentPriceBadge(CandleData lastCandle, double pixelsPerMonetaryUnit) {
        if (lastCandle == null) {
            return;
        }

        double lastPrice = lastCandle.getClosePrice();
        double screenY = cartesianToScreenY((lastPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);

        if (screenY < 0 || screenY > canvas.getHeight()) {
            return;
        }

        boolean bullish = lastCandle.getClosePrice() >= lastCandle.getOpenPrice();
        Color badgeColor = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);
        double badgeX = Math.max(0, canvas.getWidth() - PRICE_BADGE_WIDTH);
        double badgeY = clamp(screenY - (PRICE_BADGE_HEIGHT / 2.0), HEADER_HEIGHT + 2, canvas.getHeight() - PRICE_BADGE_HEIGHT - 2);

        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.42));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setLineDashes(5, 5);
        graphicsContext.strokeLine(0, screenY, badgeX - 4, screenY);
        graphicsContext.setLineDashes();

        graphicsContext.setFill(badgeColor);
        graphicsContext.fillRoundRect(badgeX, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setStroke(Color.rgb(255, 255, 255, 0.18));
        graphicsContext.strokeRoundRect(badgeX, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 5, 5);

        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(MARKER_FORMAT.format(lastPrice), badgeX + (PRICE_BADGE_WIDTH / 2.0), badgeY + (PRICE_BADGE_HEIGHT / 2.0));
    }

    private void drawCrosshair(double pixelsPerMonetaryUnit) {
        if (crosshairMouseX < 0 || crosshairMouseY < 0) {
            return;
        }

        graphicsContext.setStroke(Color.web("#94a3b8", 0.58));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setLineDashes(4, 4);
        graphicsContext.strokeLine(crosshairMouseX, 0, crosshairMouseX, canvas.getHeight());
        graphicsContext.strokeLine(0, crosshairMouseY, canvas.getWidth(), crosshairMouseY);
        graphicsContext.setLineDashes();

        double price = yAxis.getLowerBound() + ((canvas.getHeight() - crosshairMouseY) / pixelsPerMonetaryUnit);
        long timeSeconds = (long) (xAxis.getUpperBound() - ((canvas.getWidth() - crosshairMouseX) / Math.max(1, candleWidth)) * secondsPerCandle);

        String priceText = MARKER_FORMAT.format(price);
        double priceBadgeX = Math.max(0, canvas.getWidth() - PRICE_BADGE_WIDTH);
        double priceBadgeY = clamp(crosshairMouseY - (PRICE_BADGE_HEIGHT / 2.0), HEADER_HEIGHT + 2, canvas.getHeight() - PRICE_BADGE_HEIGHT - 2);
        drawLabelBadge(priceText, priceBadgeX, priceBadgeY, PRICE_BADGE_WIDTH, Color.rgb(30, 41, 59, 0.98));

        String timeText = CROSSHAIR_TIME_FORMAT.format(Instant.ofEpochSecond(Math.max(0, timeSeconds)));
        double timeWidth = 150;
        double timeX = clamp(crosshairMouseX - (timeWidth / 2.0), 2, canvas.getWidth() - timeWidth - 2);
        double timeY = canvas.getHeight() - PRICE_BADGE_HEIGHT - 2;
        drawLabelBadge(timeText, timeX, timeY, timeWidth, Color.rgb(30, 41, 59, 0.98));

        hoveredPrice = price;
        hoveredTime = timeSeconds;
    }

    private void drawLabelBadge(String text, double x, double y, double width, Color background) {
        graphicsContext.setFill(background);
        graphicsContext.fillRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.35));
        graphicsContext.strokeRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setFill(Color.rgb(241, 245, 249));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(text, x + width / 2.0, y + PRICE_BADGE_HEIGHT / 2.0);
    }

    public void addPriceLine(double price, Color color, String label) {
        if (!Double.isFinite(price) || price <= 0) {
            return;
        }

        Color safeColor = color == null ? Color.web("#f59e0b") : color;
        String safeLabel = label == null ? "" : label.trim();

        addPriceLine(new PriceLine(price, safeColor, safeLabel));
    }

    public void addPriceLine(@Nullable PriceLine priceLine) {
        if (priceLine == null || !priceLine.isValid()) {
            return;
        }

        priceLines.add(priceLine.copy());
        requestChartRedraw();
    }

    public void addSupportPriceLine(double price) {
        addPriceLine(PriceLine.support(price));
    }

    public void addResistancePriceLine(double price) {
        addPriceLine(PriceLine.resistance(price));
    }

    public void addStopLossPriceLine(double price) {
        addPriceLine(PriceLine.stopLoss(price));
    }

    public void addTakeProfitPriceLine(double price) {
        addPriceLine(PriceLine.takeProfit(price));
    }

    public void addEntryPriceLine(double price) {
        addPriceLine(PriceLine.entry(price));
    }

    public void setBidPriceLine(double price) {
        replacePriceLineByLabel(PriceLine.bid(price));
    }

    public void setAskPriceLine(double price) {
        replacePriceLineByLabel(PriceLine.ask(price));
    }

    public List<PriceLine> getPriceLines() {
        return priceLines.stream()
                .map(PriceLine::copy)
                .toList();
    }

    public void removePriceLine(double price) {
        priceLines.removeIf(line -> Double.compare(line.getPrice(), price) == 0);
        requestChartRedraw();
    }

    public void removePriceLine(@Nullable PriceLine priceLine) {
        if (priceLine != null) {
            removePriceLine(priceLine.getPrice());
        }
    }

    public void clearPriceLines() {
        priceLines.clear();
        requestChartRedraw();
    }

    public void setPriceLinesVisible(boolean visible) {
        showPriceLines = visible;
        requestChartRedraw();
    }

    public boolean isPriceLinesVisible() {
        return showPriceLines;
    }

    public double getLatestClosePrice() {
        CandleData last = lastValue(data);
        return last == null ? 0.0 : last.getClosePrice();
    }

    public void togglePriceLines() {
        setPriceLinesVisible(!showPriceLines);
    }

    private void replacePriceLineByLabel(@NotNull PriceLine replacement) {
        if (!replacement.isValid()) {
            return;
        }

        String label = replacement.getLabel();
        priceLines.removeIf(line -> Objects.equals(line.getLabel(), label));
        priceLines.add(replacement);
        requestChartRedraw();
    }

    public void setCrosshairVisible(boolean visible) {
        showCrosshair = visible;

        if (!visible) {
            crosshairMouseX = -1;
            crosshairMouseY = -1;
            hoveredPrice = -1;
            hoveredTime = -1;
        }

        requestChartRedraw();
    }

    public boolean isCrosshairVisible() {
        return showCrosshair;
    }

    public void toggleCrosshair() {
        setCrosshairVisible(!showCrosshair);
    }

    public void changeZoom(ZoomDirection zoomDirection) {
        if (disposed || currZoomLevel == null || canvas == null || data.isEmpty()) {
            logger.debug("Cannot change zoom because chart is not ready.");
            return;
        }

        CandleData lastCandle = lastValue(data);
        if (lastCandle == null) {
            return;
        }

        int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
        int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;

        if (newCandleWidth <= 1) {
            return;
        }

        int visibleCandles = Math.max(1, (int) (canvas.getWidth() / newCandleWidth));
        int newLowerBoundX = (int) (xAxis.getUpperBound() - (visibleCandles * secondsPerCandle));

        if (newLowerBoundX > lastCandle.getOpenTime() - (2 * secondsPerCandle)) {
            return;
        }

        int nextZoomLevelId = ZoomLevel.getNextZoomLevelId(currZoomLevel, zoomDirection);
        int currentMinX = currZoomLevel.getMinXValue();

        if (!zoomLevelMap.containsKey(nextZoomLevelId)) {
            ZoomLevel newZoomLevel = new ZoomLevel(
                    nextZoomLevelId,
                    newCandleWidth,
                    secondsPerCandle,
                    canvas.widthProperty(),
                    getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
                    currentMinX
            );

            int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) - lastCandle.getOpenTime()) / secondsPerCandle, 0);

            if (newLowerBoundX - (numCandlesToSkip * secondsPerCandle) < currentMinX) {
                paging = true;
                progressIndicator.setVisible(true);
                showLoadingStatus("Loading more candles...");

                CompletableFuture
                        .supplyAsync(candleDataPager.getCandleDataSupplier(), chartLoadingExecutor)
                        .thenAccept(candleDataPager.getCandleDataPreProcessor())
                        .whenComplete((result, throwable) -> runOnFx(() -> {
                            try {
                                if (throwable != null) {
                                    logger.error("Failed to page candles during zoom", throwable);
                                    showErrorMessage("Failed to load more data: %s".formatted(rootMessage(throwable)));
                                    return;
                                }

                                rebuildZoomLevelExtrema(newZoomLevel);
                                zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                                currZoomLevel = newZoomLevel;
                                applyZoomLevel(newLowerBoundX);
                            } finally {
                                hideLoadingIndicator();
                                paging = false;
                            }
                        }));
                return;
            }

            rebuildZoomLevelExtrema(newZoomLevel);
            zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
            currZoomLevel = newZoomLevel;
        } else {
            currZoomLevel = zoomLevelMap.get(nextZoomLevelId);
            rebuildZoomLevelExtrema(currZoomLevel);
        }

        applyZoomLevel(newLowerBoundX);
    }

    private void rebuildZoomLevelExtrema(ZoomLevel zoomLevel) {
        if (zoomLevel == null || data.isEmpty()) {
            return;
        }

        List<CandleData> candleData;
        synchronized (data) {
            candleData = new ArrayList<>(data.values());
        }

        if (candleData.isEmpty()) {
            return;
        }

        int visible = Math.max(1, (int) Math.round(zoomLevel.getNumVisibleCandles()));
        visible = Math.min(visible, candleData.size());

        zoomLevel.getExtremaForCandleRangeMap().clear();
        putSlidingWindowExtrema(zoomLevel.getExtremaForCandleRangeMap(), candleData, visible);

        int fromIndex = Math.max(0, candleData.size() - (int) Math.floor(zoomLevel.getNumVisibleCandles()));
        putExtremaForRemainingElements(zoomLevel.getExtremaForCandleRangeMap(), candleData.subList(fromIndex, candleData.size()));
    }

    private void applyZoomLevel(int newLowerBoundX) {
        if (currZoomLevel == null) {
            return;
        }

        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
        candleWidth = currZoomLevel.getCandleWidth();
        xAxis.setLowerBound(newLowerBoundX);
        setYAndExtraAxisBounds();
        drawChartContents(true);
    }
    private final Object zoomLock = new Object();
    private ZoomLevel currentZoomLevel() {
        synchronized (zoomLock) {
            return currZoomLevel;
        }
    }

    public void applyAdaptiveScaling() {
        if (disposed || canvas == null || currZoomLevel == null || data.isEmpty()) {
            return;
        }
        ZoomLevel zoomLevel = currentZoomLevel();

        if (zoomLevel == null || canvas == null) {
            return;
        }

        visibleCandles = Math.max(1, (int) zoomLevel.getNumVisibleCandles());
        CandleData last = lastValue(data);
        if (last == null) {
            return;
        }

        int totalCandles = data.size();
        double canvasWidth = Math.max(1, canvas.getWidth());
        double canvasHeight = Math.max(1, canvas.getHeight());
        double aspectRatio = ChartScalingStrategy.calculateAspectRatio(canvasWidth, canvasHeight);
        int chartPadding = ChartScalingStrategy.calculateChartPadding(canvasWidth);
        double usableWidth = Math.max(1.0, canvasWidth - (chartPadding * 2.0));

        int adaptiveVisibleCandles = ChartScalingStrategy.calculateVisibleCandles(usableWidth, candleWidth);
        if (!ChartScalingStrategy.needsAdaptiveScaling(totalCandles, adaptiveVisibleCandles)) {
            return;
        }

        int optimalWidth = ChartScalingStrategy.calculateOptimalCandleWidth(usableWidth, totalCandles);
        if (ChartScalingStrategy.shouldAutoZoom(adaptiveVisibleCandles) || ChartScalingStrategy.shouldZoomOut(adaptiveVisibleCandles)) {
            optimalWidth = ChartScalingStrategy.suggestNextZoomLevel(candleWidth, adaptiveVisibleCandles);
        }

        if (aspectRatio < 1.2 && ChartScalingStrategy.shouldZoomOut(adaptiveVisibleCandles)) {
            optimalWidth = Math.max(ChartScalingStrategy.MIN_CANDLE_BODY_WIDTH, optimalWidth - 1);
        }

        optimalWidth = Math.max(
                ChartScalingStrategy.MIN_CANDLE_BODY_WIDTH,
                Math.min(ChartScalingStrategy.MAX_CANDLE_BODY_WIDTH, optimalWidth)
        );

        if (Math.abs(optimalWidth - candleWidth) <= 1) {
            return;
        }

        candleWidth = optimalWidth;
        currZoomLevel = new ZoomLevel(
                getCurrentZoomLevelIndex(),
                candleWidth,
                secondsPerCandle,
                canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                currZoomLevel.getMinXValue()
        );
        zoomLevelMap.put(getCurrentZoomLevelIndex(), currZoomLevel);
        rebuildZoomLevelExtrema(currZoomLevel);

        visibleCandles = Math.max(1, (int) currZoomLevel.getNumVisibleCandles());
        int lower = last.getOpenTime() + secondsPerCandle - (visibleCandles * secondsPerCandle);
        xAxis.setUpperBound(last.getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound(lower);
        setYAndExtraAxisBounds();
        drawChartContents(true);
    }

    public int getCurrentZoomLevelIndex() {
        for (Map.Entry<Integer, ZoomLevel> entry : zoomLevelMap.entrySet()) {
            if (entry.getValue() == currZoomLevel) {
                return entry.getKey();
            }
        }
        return 0;
    }

    public void refreshChart() {
        if (disposed) {
            return;
        }

        synchronized (data) {
            data.clear();
        }

        zoomLevelMap.clear();
        currZoomLevel = null;
        inProgressCandleLastDraw = -1;
        showLoadingStatus("Refreshing chart...");
        startChartDataLoading();
    }

    public void jumpToLatestCandle() {
        runOnFx(() -> {
            if (disposed || data.isEmpty() || currZoomLevel == null || canvas == null) {
                return;
            }

            CandleData last = lastValue(data);
            if (last == null) {
                return;
            }

            int visibleCandles = Math.max(1, (int) currZoomLevel.getNumVisibleCandles());
            int upper = last.getOpenTime() + secondsPerCandle;
            int lower = upper - (visibleCandles * secondsPerCandle);

            xAxis.setUpperBound(upper);
            xAxis.setLowerBound(lower);

            setYAndExtraAxisBounds();
            drawChartContents(true);
        });
    }

    public void fitChart() {
        runOnFx(() -> {
            if (disposed || data.isEmpty() || canvas == null) {
                return;
            }

            CandleData first;
            CandleData last;

            synchronized (data) {
                first = data.firstEntry() == null ? null : data.firstEntry().getValue();
                last = data.lastEntry() == null ? null : data.lastEntry().getValue();
            }

            if (first == null || last == null) {
                return;
            }

            int candleCount = Math.max(1, data.size());
            int chartPadding = ChartScalingStrategy.calculateChartPadding(canvas.getWidth());
            double usableWidth = Math.max(1.0, canvas.getWidth() - (chartPadding * 2.0));
            candleWidth = ChartScalingStrategy.calculateOptimalCandleWidth(usableWidth, candleCount);

            currZoomLevel = new ZoomLevel(
                    getCurrentZoomLevelIndex(),
                    candleWidth,
                    secondsPerCandle,
                    canvas.widthProperty(),
                    getXAxisFormatterForRange(last.getOpenTime() - first.getOpenTime()),
                    first.getOpenTime()
            );

            zoomLevelMap.put(getCurrentZoomLevelIndex(), currZoomLevel);
            rebuildZoomLevelExtrema(currZoomLevel);

            xAxis.setUpperBound(last.getOpenTime() + secondsPerCandle);
            xAxis.setLowerBound(first.getOpenTime());

            setYAndExtraAxisBounds();
            drawChartContents(true);
        });
    }

    @SneakyThrows
    @Override
    protected void layoutChildren() {
        chartWidth = normalizedChartWidth(getWidth());
        chartHeight = Math.max(MIN_CHART_HEIGHT, getHeight());
        layoutChart();
        if (currZoomLevel != null && !data.isEmpty()) {
            drawChartContents(true);
        }
    }

    @Override
    protected double computeMinWidth(double height) {
        return MIN_CHART_WIDTH;
    }

    @Override
    protected double computeMinHeight(double width) {
        return MIN_CHART_HEIGHT;
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
        if (!Platform.isFxApplicationThread()) {
            runOnFx(() -> setInitialState(candleData));
            return;
        }

        if (candleData == null || candleData.isEmpty() || canvas == null) {
            showErrorMessage("No candle data available for %s".formatted(tradePair));
            return;
        }

        List<CandleData> workingData = new ArrayList<>(candleData);

        if (liveSyncing) {
            workingData.add(inProgressCandle.snapshot());
        }

        CandleData first = firstOf(workingData);
        CandleData last = lastOf(workingData);

        if (first == null || last == null) {
            showErrorMessage("Invalid candle data for %s".formatted(tradePair));
            return;
        }

        xAxis.setUpperBound(last.getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((last.getOpenTime() + secondsPerCandle) - (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(
                0,
                candleWidth,
                secondsPerCandle,
                canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                first.getOpenTime()
        );

        zoomLevelMap.put(0, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());

        synchronized (data) {
            data.clear();
            data.putAll(workingData.stream().collect(
                    Collectors.toMap(CandleData::getOpenTime, Function.identity(), (existing, replacement) -> replacement, TreeMap::new)
            ));
        }

        rebuildZoomLevelExtrema(currZoomLevel);
        setYAndExtraAxisBounds();
        applyAdaptiveScaling();
        drawChartContents(false);
        hideLoadingIndicator();

        if (updateInProgressCandleTask != null) {
            updateInProgressCandleTask.setReady(true);
        }
    }

    private void startChartDataLoading() {
        if (disposed) {
            return;
        }

        if (!loading.compareAndSet(false, true)) {
            logger.debug("Chart data loading already in progress for {}", tradePair);
            return;
        }

        loadingStartTime = System.currentTimeMillis();
        showLoadingStatus("Fetching chart data...");

        chartTimeoutExecutor.schedule(this::checkLoadingTimeout, LOADING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        CompletableFuture
                .supplyAsync(candleDataPager.getCandleDataSupplier(), chartLoadingExecutor)
                .thenAccept(candleDataPager.getCandleDataPreProcessor())
                .whenComplete((result, throwable) -> {
                    loadingStartTime = -1L;
                    loading.set(false);

                    if (disposed) {
                        return;
                    }

                    if (throwable != null) {
                        logger.error("Error loading chart data for {}", tradePair, throwable);
                        showErrorMessage("Failed to load chart data: %s".formatted(rootMessage(throwable)));
                    }
                });
    }

    private void checkLoadingTimeout() {
        if (loadingStartTime <= 0 || disposed || !loading.get()) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - loadingStartTime;
        if (elapsedMs >= LOADING_TIMEOUT_MS) {
            showErrorMessage("Loading is taking too long. Check your internet connection or exchange API.");
        }
    }

    private void showLoadingStatus(String message) {
        runOnFx(() -> {
            noticeClearTimer.stop();
            loadingStatusText.setText(message);
            loadingStatusText.setFill(Color.WHITE);
            progressIndicator.setVisible(true);
        });
    }

    private void showTransientNotice(String message) {
        runOnFx(() -> {
            if (message == null || message.isBlank()) {
                return;
            }
            progressIndicator.setVisible(false);
            loadingStatusText.setText(message);
            loadingStatusText.setFill(Color.web("#fbbf24"));
            loadingStatusText.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
            noticeClearTimer.playFromStart();
        });
    }

    private void showErrorMessage(String errorMessage) {
        runOnFx(() -> {
            noticeClearTimer.stop();
            loadingStatusText.setText("Error: %s".formatted(errorMessage));
            loadingStatusText.setFill(Color.web("#FF6B6B"));
            loadingStatusText.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
            progressIndicator.setVisible(false);
            logger.error("Chart Error: {}", errorMessage);
        });
    }

    private void hideLoadingIndicator() {
        runOnFx(() -> {
            progressIndicator.setVisible(false);
            if (noticeClearTimer.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                loadingStatusText.setText("");
            }
        });
    }

    private void startPollingForLiveTrades() {
        if (disposed || updateInProgressCandleExecutor == null || updateInProgressCandleTask == null) {
            logger.debug("Live polling not started for {} because chart is disposed or executor is missing.", tradePair);
            return;
        }

        updateInProgressCandleExecutor.scheduleAtFixedRate(() -> {
            if (disposed) {
                return;
            }

            try {
                Instant pollSince = Instant.now().minusSeconds(Math.max(5, secondsPerCandle));

                exchange.fetchRecentTradesUntil(tradePair, pollSince)
                        .thenAccept(trades -> {
                            if (disposed || trades == null || trades.isEmpty()) {
                                return;
                            }

                            for (Trade trade : trades) {
                                updateInProgressCandleTask.accept(trade);
                            }

                            logger.debug("Polled {} recent trades for {}", trades.size(), tradePair);
                        })
                        .exceptionally(exception -> {
                            if (!disposed) {
                                logger.warn("Error polling recent trades for {}", tradePair, exception);
                            }
                            return null;
                        });
            } catch (Exception exception) {
                if (!disposed) {
                    logger.warn("Error in polling thread for {}", tradePair, exception);
                }
            }
        }, 3, 3, SECONDS);

        logger.info("Started polling mode for live trades on {}", tradePair);
    }

    public void dispose() {
        if (disposed) {
            return;
        }

        disposed = true;
        paging = false;
        loading.set(false);

        try {
            redrawDebouncer.stop();
            noticeClearTimer.stop();
        } catch (Exception ignored) {
            // no-op
        }

        try {
            if (exchange.getWebsocketClient() != null) {
                exchange.getWebsocketClient().stopStreamLiveTrades(tradePair);
            }
        } catch (Exception exception) {
            logger.debug("Unable to stop live trade stream for {}", tradePair, exception);
        }

        shutdownExecutor(updateInProgressCandleExecutor, "UPDATE-CURRENT-CANDLE");
        shutdownExecutor(chartLoadingExecutor, "CHART-DATA-LOADER");
        shutdownExecutor(chartTimeoutExecutor, "CHART-LOADING-TIMEOUT");

        runOnFx(() -> {
            try {
                priceLines.clear();
                data.clear();
                zoomLevelMap.clear();

                if (canvas != null) {
                    canvas.setOnMouseEntered(null);
                    canvas.setOnMouseExited(null);
                    canvas.setOnMouseClicked(null);
                }

                getChildren().clear();
            } catch (Exception exception) {
                logger.debug("Failed to clear chart UI for {}", tradePair, exception);
            }
        });

        logger.info("Disposed CandleStickChart for {}", tradePair);
    }

    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor == null) {
            return;
        }

        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.debug("{} did not terminate cleanly for {}", name, tradePair);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.debug("{} shutdown interrupted for {}", name, tradePair);
        }
    }

    /**
     * Toggle auto-trading mode from the chart.
     *
     * Important:
     * This method should not place orders directly.
     * It only toggles chart-level auto-trade intent/state.
     * Your SmartBot / ExecutionAgent should be the one that actually executes.
     */
    public void autoTrade() {
        autoTradeEnabled = !autoTradeEnabled;

        String state = autoTradeEnabled ? "enabled" : "disabled";

        logger.info("Chart auto-trade {} for {}", state, tradePair);

        showLoadingStatus("Auto-trade %s for %s".formatted(
                state,
                tradePair == null ? "symbol" : tradePair.toString('/')
        ));

        requestChartRedraw();
    }



    /**
     * Save a screenshot of the current candlestick chart.
     */
    public void screenshot() {
        runOnFx(() -> {
            try {
                Node snapshotNode = this;

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Chart Screenshot");

                String symbol = tradePair == null
                        ? "chart"
                        : tradePair.toString('-').replace("/", "-");

                String defaultName = "%s-%s.png".formatted(
                        symbol,
                        SCREENSHOT_FORMAT.format(LocalDateTime.now())
                );

                fileChooser.setInitialFileName(defaultName);
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("PNG Image", "*.png")
                );

                File selectedFile = fileChooser.showSaveDialog(
                        getScene() == null ? null : getScene().getWindow()
                );

                if (selectedFile == null) {
                    return;
                }

                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);

                WritableImage image = snapshotNode.snapshot(parameters, null);

                ImageIO.write(
                        javafx.embed.swing.SwingFXUtils.fromFXImage(image, null),
                        "png",
                        selectedFile
                );

                logger.info("Chart screenshot saved: {}", selectedFile.getAbsolutePath());
                showLoadingStatus("Screenshot saved: %s".formatted(selectedFile.getName()));

            } catch (IOException exception) {
                logger.error("Failed to save chart screenshot for {}", tradePair, exception);
                showErrorMessage("Screenshot failed: %s".formatted(rootMessage(exception)));
            } catch (Exception exception) {
                logger.error("Unexpected screenshot error for {}", tradePair, exception);
                showErrorMessage("Screenshot failed: %s".formatted(rootMessage(exception)));
            }
        });
    }

    /**
     * Print the current candlestick chart.
     */
    public void print() {
        runOnFx(() -> {
            PrinterJob job = PrinterJob.createPrinterJob();

            if (job == null) {
                showErrorMessage("No printer available.");
                logger.warn("No printer available for chart print.");
                return;
            }

            boolean showDialog = job.showPrintDialog(
                    getScene() == null ? null : getScene().getWindow()
            );

            if (!showDialog) {
                return;
            }

            try {
                boolean printed = job.printPage(this);

                if (printed) {
                    job.endJob();
                    logger.info("Chart printed for {}", tradePair);
                    showLoadingStatus("Chart sent to printer.");
                } else {
                    logger.warn("Chart print failed for {}", tradePair);
                    showErrorMessage("Chart print failed.");
                }

            } catch (Exception exception) {
                logger.error("Failed to print chart for {}", tradePair, exception);
                showErrorMessage("Print failed: %s".formatted(rootMessage(exception)));
            }
        });
    }

    private final class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(
                BooleanProperty gotFirstSize,
                ObservableValue<Number> containerWidth,
                ObservableValue<Number> containerHeight
        ) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() throws SQLException, ClassNotFoundException {
            if (disposed || canvas == null || currZoomLevel == null || data.isEmpty()) {
                return;
            }

            chartWidth = normalizedChartWidth(containerWidth.getValue().doubleValue());
            chartHeight = Math.max(MIN_CHART_HEIGHT, containerHeight.getValue().doubleValue());

            layoutChart();

            int newLowerBoundX = (int) (
                    xAxis.getUpperBound() - ((int) currZoomLevel.getNumVisibleCandles() * secondsPerCandle)
            );

            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                pageMoreDataThen(() -> {
                    try {
                        resizeAfterDataLoad(newLowerBoundX);
                    } catch (SQLException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                resizeAfterDataLoad(newLowerBoundX);
            }
        }

        private void resizeAfterDataLoad(int newLowerBoundX) throws SQLException, ClassNotFoundException {
            rebuildZoomLevelExtrema(currZoomLevel);
            xAxis.setLowerBound(newLowerBoundX);
            setYAndExtraAxisBounds();
            layoutChart();
            drawChartContents(true);
        }
    }

    private final class UpdateInProgressCandleTask implements LiveTradesConsumer, Runnable {
        private final BlockingQueue<Trade> liveTradesQueue = new LinkedBlockingQueue<>();
        @Setter
        private volatile boolean ready;

        @Override
        public boolean containsKey(TradePair tradePair) {
            return tradePair != null && tradePair.equals(CandleStickChart.this.tradePair);
        }

        @Override
        public void remove(TradePair tradePair) {
            logger.debug("Removing trade pair from consumer: {}", tradePair);
        }

        @Override
        public void put(TradePair tradePair) {
            if (tradePair != null && tradePair.equals(CandleStickChart.this.tradePair)) {
                logger.debug("Registered trade pair for live updates: {}", tradePair);
            }
        }

        @Override
        public Trade get(TradePair tradePair) {
            return null;
        }

        @Override
        public void accept(Trade trade) {
            if (trade == null) {
                return;
            }

            try {
                if (!liveTradesQueue.offer(trade, 5, TimeUnit.SECONDS)) {
                    logger.debug("Dropped live trade for {} because the processing queue is full.", tradePair);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.debug("Interrupted while queueing live trade for {}", tradePair, exception);
            }
        }

        @Override
        public void acceptTrades(Trade trade) {
            accept(trade);
        }

        @Override
        public void run() {
            if (disposed || !ready) {
                return;
            }

            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);

            if (liveTrades.isEmpty()) {
                return;
            }

            List<Trade> newTrades = liveTrades.stream()
                    .filter(trade -> trade != null
                            && trade.getTimestamp() != null
                            && trade.getTimestamp().getEpochSecond() > inProgressCandle.getCurrentTill())
                    .toList();

            if (newTrades.isEmpty()) {
                return;
            }

            Map<Boolean, List<Trade>> partitioned = newTrades.stream().collect(
                    Collectors.partitioningBy(trade ->
                            trade.getTimestamp().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle
                    )
            );

            currentCandleTrades = partitioned.getOrDefault(false, Collections.emptyList());
            List<Trade> nextCandleTrades = partitioned.getOrDefault(true, Collections.emptyList());

            int currentTill = (int) Instant.now().getEpochSecond();

            if (!currentCandleTrades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(Math.max(
                        currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().orElse(inProgressCandle.getHighPriceSoFar()),
                        inProgressCandle.getHighPriceSoFar()
                ));
                inProgressCandle.setLowPriceSoFar(Math.min(
                        currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandle.getLowPriceSoFar()),
                        inProgressCandle.getLowPriceSoFar()
                ));
                inProgressCandle.setVolumeSoFar(
                        inProgressCandle.getVolumeSoFar() + currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum()
                );
                inProgressCandle.setCurrentTill(currentTill);
                inProgressCandle.setLastPrice(lastOf(currentCandleTrades).getPrice());

                synchronized (data) {
                    data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
                }
            }

            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                rollInProgressCandle(nextCandleTrades);
            }

            runOnFx(() -> drawChartContents(true));
        }

        private void rollInProgressCandle(List<Trade> nextCandleTrades) {
            inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
            inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());

            if (nextCandleTrades != null && !nextCandleTrades.isEmpty()) {
                Trade firstTrade = firstOf(nextCandleTrades);

                inProgressCandle.setIsPlaceholder(false);
                inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().orElse(inProgressCandle.getLastPrice()));
                inProgressCandle.setLowPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandle.getLastPrice()));
                inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setLastPrice(firstTrade.getPrice());
                inProgressCandle.setCurrentTill((int) firstTrade.getTimestamp().getEpochSecond());
            } else {
                inProgressCandle.setIsPlaceholder(true);
                inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                inProgressCandle.setVolumeSoFar(0);
                inProgressCandle.setCurrentTill((int) Instant.now().getEpochSecond());
            }

            synchronized (data) {
                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }
        }
    }

    private final class CandlePageConsumer implements Consumer<List<CandleData>> {
        @Override
        public void accept(List<CandleData> candleData) {
            if (Platform.isFxApplicationThread()) {
                logger.error("Candle data paging must not happen on FX thread.");
                throw new IllegalStateException("Candle data paging must not happen on FX thread.");
            }

            if (candleData == null || candleData.isEmpty()) {
                logger.warn("No candle data available for {}", tradePair);
                showErrorMessage("No data available for %s.".formatted(tradePair));
                return;
            }

            if (candleData.size() > 1 && candleData.get(0).getOpenTime() >= candleData.get(1).getOpenTime()) {
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value.");
            }

            if (data.isEmpty()) {
                handleInitialCandlePage(candleData);
            } else {
                handleAdditionalCandlePage(candleData);
            }
        }

        private void handleInitialCandlePage(List<CandleData> candleData) {
            if (!liveSyncing) {
                runOnFx(() -> setInitialState(candleData));
                return;
            }

            CandleData last = lastOf(candleData);
            if (last == null) {
                showErrorMessage("Invalid initial candle data.");
                return;
            }

            long secondsIntoCurrentCandle = Instant.now().getEpochSecond() - (last.getOpenTime() + secondsPerCandle);
            int currentOpenTime = last.getOpenTime() + secondsPerCandle;
            inProgressCandle.setOpenTime(currentOpenTime);

            CompletableFuture<Optional<InProgressCandleData>> inProgressFuture =
                    exchange.fetchCandleDataForInProgressCandle(
                            tradePair,
                            Instant.ofEpochSecond(currentOpenTime),
                            secondsIntoCurrentCandle,
                            secondsPerCandle
                    );

            inProgressFuture.whenComplete((optionalData, throwable) -> {
                if (throwable != null) {
                    logger.error("Error fetching in-progress candle data", throwable);
                    showErrorMessage("Failed to fetch in-progress candle data: %s".formatted(rootMessage(throwable)));
                    return;
                }

                if (optionalData != null && optionalData.isPresent()) {
                    hydrateInProgressCandleFromExchange(candleData, optionalData.get());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setVolumeSoFar(0);
                    inProgressCandle.setCurrentTill((int) (secondsIntoCurrentCandle + currentOpenTime));
                    runOnFx(() -> setInitialState(candleData));
                }
            });
        }

        private void hydrateInProgressCandleFromExchange(List<CandleData> candleData, InProgressCandleData inProgressCandleData) {
            int currentTill = (int) Instant.now().getEpochSecond();

            exchange.fetchRecentTradesUntil(tradePair, Instant.ofEpochSecond(inProgressCandleData.getCurrentTill()))
                    .whenComplete((trades, exception) -> {
                        if (exception != null) {
                            logger.error("Error fetching recent trades", exception);
                            showErrorMessage("Failed to fetch recent trade data: %s".formatted(rootMessage(exception)));
                            return;
                        }

                        List<Trade> safeTrades = trades == null ? Collections.emptyList() : trades;

                        inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
                        inProgressCandle.setCurrentTill(currentTill);

                        if (safeTrades.isEmpty()) {
                            inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPriceSoFar());
                            inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPriceSoFar());
                            inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar());
                            inProgressCandle.setLastPrice(inProgressCandleData.getLastPrice());
                        } else {
                            inProgressCandle.setHighPriceSoFar(Math.max(
                                    safeTrades.stream().mapToDouble(Trade::getPrice).max().orElse(inProgressCandleData.getHighPriceSoFar()),
                                    inProgressCandleData.getHighPriceSoFar()
                            ));
                            inProgressCandle.setLowPriceSoFar(Math.min(
                                    safeTrades.stream().mapToDouble(Trade::getPrice).min().orElse(inProgressCandleData.getLowPriceSoFar()),
                                    inProgressCandleData.getLowPriceSoFar()
                            ));
                            inProgressCandle.setVolumeSoFar(
                                    inProgressCandleData.getVolumeSoFar() + safeTrades.stream().mapToDouble(Trade::getAmount).sum()
                            );
                            inProgressCandle.setLastPrice(lastOf(safeTrades).getPrice());
                        }

                        runOnFx(() -> setInitialState(candleData));
                    });
        }

        private void handleAdditionalCandlePage(List<CandleData> candleData) {
            if (currZoomLevel == null || candleData == null || candleData.isEmpty()) {
                return;
            }

            int slidingWindowSize = Math.max(1, (int) currZoomLevel.getNumVisibleCandles());

            Map<Integer, CandleData> extremaData;
            synchronized (data) {
                int from = currZoomLevel.getMinXValue();
                int to = currZoomLevel.getMinXValue() + (int) (currZoomLevel.getNumVisibleCandles() * secondsPerCandle);
                extremaData = new TreeMap<>(data.subMap(from, true, to, true));
            }

            List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
            newDataPlusOffset.addAll(extremaData.values());

            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset, slidingWindowSize);

            synchronized (data) {
                data.putAll(candleData.stream().collect(
                        Collectors.toMap(CandleData::getOpenTime, Function.identity(), (existing, replacement) -> replacement, TreeMap::new)
                ));
            }

            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            rebuildZoomLevelExtrema(currZoomLevel);
        }
    }

    private final class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (disposed || paging) {
                event.consume();
                return;
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (mousePrevX == -1 && mousePrevY == -1) {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                event.consume();
                return;
            }

            double dx = event.getScreenX() - mousePrevX;
            scrollDeltaXSum += dx;

            double threshold = Math.max(6.0, candleWidth * 0.65);
            if (Math.abs(scrollDeltaXSum) >= threshold) {
                int xDirection = (int) -Math.signum(scrollDeltaXSum);
                moveAlongX(xDirection, false);
                scrollDeltaXSum = 0;
            }

            mousePrevX = event.getScreenX();
            mousePrevY = event.getScreenY();
            event.consume();
        }
    }

    private final class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override
        public void handle(ScrollEvent event) {
            if (disposed || paging) {
                event.consume();
                return;
            }

            if (event.isInertia()) {
                return;
            }

            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    changeZoom(ZoomDirection.IN);
                } else if (event.getDeltaY() < 0) {
                    changeZoom(ZoomDirection.OUT);
                }
                event.consume();
                return;
            }

            if (Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY())) {
                int xDirection = event.getDeltaX() > 0 ? -1 : 1;
                moveAlongX(xDirection, false);
                event.consume();
                return;
            }

            if (event.getDeltaY() != 0 && event.getTouchCount() == 0) {
                int xDirection = event.getDeltaY() > 0 ? -1 : 1;
                moveAlongX(xDirection, false);
                event.consume();
            }
        }
    }

    private final class KeyEventHandler implements EventHandler<KeyEvent> {
        @Override
        public void handle(KeyEvent event) {
            if (disposed || paging) {
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
            } else if (event.getCode() == KeyCode.HOME) {
                jumpToLatestCandle();
                consume = true;
            } else if (event.getCode() == KeyCode.F5) {
                refreshChart();
                consume = true;
            } else if (event.getCode() == KeyCode.F) {
                fitChart();
                consume = true;
            } else if (event.getCode() == KeyCode.C) {
                toggleCrosshair();
                consume = true;
            } else if (event.getCode() == KeyCode.L) {
                togglePriceLines();
                consume = true;
            } else if (event.getCode() == KeyCode.LEFT) {
                moveAlongX(-1, false);
                consume = true;
            } else if (event.getCode() == KeyCode.RIGHT) {
                moveAlongX(1, false);
                consume = true;
            }

            if (consume) {
                event.consume();
            }
        }
    }

    private final class MouseMovedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (disposed || canvas == null || currZoomLevel == null || data.isEmpty()) {
                return;
            }

            crosshairMouseX = clamp(event.getX(), 0, canvas.getWidth());
            crosshairMouseY = clamp(event.getY(), 0, canvas.getHeight());
            requestChartRedraw();
        }
    }

    private double cartesianToScreenY(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    private double priceToScreenY(double price, double pixelsPerMonetaryUnit) {
        return cartesianToScreenY((price - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
    }

    private double candleCenterX(int candleIndex) {
        return canvas.getWidth() - ((candleIndex + 0.5) * candleWidth);
    }

    private double snapPixel(double value) {
        return Math.round(value) + 0.5;
    }

    private double candleStrokeWidth() {
        return Math.max(1.0, Math.min(1.5, candleWidth / 7.0));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String compactNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) {
            return "%sB".formatted(MARKER_FORMAT.format(value / 1_000_000_000.0));
        }
        if (abs >= 1_000_000) {
            return "%sM".formatted(MARKER_FORMAT.format(value / 1_000_000.0));
        }
        if (abs >= 1_000) {
            return "%sK".formatted(MARKER_FORMAT.format(value / 1_000.0));
        }
        return MARKER_FORMAT.format(value);
    }


    private @NotNull String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void runOnFx(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private <T> T firstOf(List<T> items) {
        return items == null || items.isEmpty() ? null : items.getFirst();
    }

    private <T> T lastOf(List<T> items) {
        return items == null || items.isEmpty() ? null : items.getLast();
    }

    private @Nullable CandleData lastValue(@NotNull NavigableMap<Integer, CandleData> values) {
        synchronized (values.values()) {
            return values.isEmpty() || values.lastEntry() == null ? null : values.lastEntry().getValue();
        }
    }
}
