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
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import org.investpro.data.CandleDataPager;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.ChartContainer;
import org.investpro.ui.tools.InstantAxisFormatter;
import org.investpro.ui.tools.MoneyAxisFormatter;
import org.investpro.ui.tools.PriceLine;
import org.investpro.ui.tools.StableTicksAxis;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.FXUtils;
import org.investpro.utils.LogOnExceptionThreadFactory;
import org.investpro.utils.ZoomDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.ui.charts.ChartColors.AXIS_TICK_LABEL_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_FILL_COLOR;

/**
 * Professional canvas-based candlestick chart for InvestPro.
 *
 * Fixes the visual problems from the previous version:
 * - candle body and wick use the exact same center X
 * - candle slot width is separated from candle body width
 * - fit mode chooses a readable TradingView-like visible range
 * - y-axis range is recalculated from the visible candles only
 * - resize keeps the latest candle visible without compressing everything badly
 *
 * This chart should be created by {@link ChartContainer}.
 */
@Getter
@Setter
public class CandleStickChart extends Region {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);

    private static final DateTimeFormatter SCREENSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CROSSHAIR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    private static final int MIN_CHART_WIDTH = 360;
    private static final int MIN_CHART_HEIGHT = 260;
    private static final int RIGHT_AXIS_WIDTH = 92;
    private static final int LEFT_AXIS_WIDTH = 64;
    private static final int TIME_AXIS_HEIGHT = 34;
    private static final int HEADER_HEIGHT = 54;
    private static final int PRICE_BADGE_WIDTH = 96;
    private static final int PRICE_BADGE_HEIGHT = 22;

    private static final int MIN_VISIBLE_CANDLES = 40;
    private static final int TARGET_VISIBLE_CANDLES = 72;
    private static final int MAX_VISIBLE_CANDLES = 120;

    private static final int MIN_BODY_WIDTH = 2;
    private static final int MAX_BODY_WIDTH = 28;
    private static final int DEFAULT_BODY_WIDTH = 8;
    private static final int CANDLE_GAP = 2;

    private static final double VOLUME_AREA_RATIO = 0.20;
    private static final double PRICE_PADDING_RATIO = 0.12;
    private static final long LOADING_TIMEOUT_MS = 30_000L;


    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final int secondsPerCandle;
    private final String telegramToken;
    private final TradingService tradingService;

    private final CandleStickChartOptions chartOptions;
    private final CandleDataPager candleDataPager;
    private final Consumer<List<CandleData>> candlePageConsumer;

    private final NavigableMap<Integer, CandleData> data = new ConcurrentSkipListMap<>();

    private final StableTicksAxis xAxis = new StableTicksAxis();
    private final StableTicksAxis yAxis = new StableTicksAxis();
    private final StableTicksAxis extraAxis = new StableTicksAxis();
    private final Line extraAxisExtension = new Line();

    private final ProgressIndicator progressIndicator = new ProgressIndicator(-1);
    private final Text loadingStatusText = new Text("Loading...");
    private final VBox loadingIndicatorContainer = new VBox(8, progressIndicator, loadingStatusText);

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean drawRequested = new AtomicBoolean(false);
    private final AtomicBoolean eventFiltersInstalled = new AtomicBoolean(false);

    private final ScheduledExecutorService chartLoadingExecutor =
            Executors.newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-DATA-LOADER"));
    private final ScheduledExecutorService chartTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-LOADING-TIMEOUT"));

    private final PauseTransition redrawDebouncer = new PauseTransition(Duration.millis(16));
    private final PauseTransition noticeClearTimer = new PauseTransition(Duration.seconds(2.0));

    private final EventHandler<MouseEvent> mouseDraggedHandler = new MouseDraggedHandler();
    private final EventHandler<ScrollEvent> scrollHandler = new ScrollEventHandler();
    private final EventHandler<KeyEvent> keyHandler = new KeyEventHandler();

    private final Font canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 13);

    private final List<PriceLine> priceLines = new ArrayList<>();

    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private StackPane chartStackPane;

    private volatile boolean disposed;
    private volatile boolean paging;
    private volatile long loadingStartTime = -1L;

    private int candleBodyWidth = DEFAULT_BODY_WIDTH;
    private int firstVisibleIndex = 0;
    private int visibleCandles = TARGET_VISIBLE_CANDLES;

    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;

    private double chartWidth = 1000;
    private double chartHeight = 700;

    private double visibleMinPrice = 0;
    private double visibleMaxPrice = 1;
    private double visibleMaxVolume = 1;

    private double crosshairMouseX = -1;
    private double crosshairMouseY = -1;
    private boolean showCrosshair = true;
    private boolean showPriceLines = true;
    private double hoveredPrice = -1;
    private long hoveredTime = -1;

    private boolean autoTradeEnabled = false;

    private ChangeListener<Number> activeSizeListener;
    private ChangeListener<Boolean> activeFirstSizeListener;

    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    private final ScheduledExecutorService updateInProgressCandleExecutor;
    private double canvasX;

    public CandleStickChart(
            Exchange exchange,
            CandleDataSupplier candleDataSupplier,
            TradePair tradePair,
            boolean liveSyncing,
            int secondsPerCandle,
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight
    ) {
        this(exchange, candleDataSupplier, tradePair, liveSyncing, secondsPerCandle, "", null, containerWidth, containerHeight);
    }

    public CandleStickChart(
            Exchange exchange,
            CandleDataSupplier candleDataSupplier,
            TradePair tradePair,
            boolean liveSyncing,
            int secondsPerCandle,
            String telegramToken,
            TradingService tradingService,
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
        this.liveSyncing = liveSyncing;
        this.secondsPerCandle = Math.max(1, secondsPerCandle);
        this.telegramToken = telegramToken == null ? "" : telegramToken.trim();
        this.tradingService = tradingService;

        this.chartOptions = new CandleStickChartOptions();
        this.candleDataPager = new CandleDataPager(this, candleDataSupplier);
        this.candlePageConsumer = new CandlePageConsumer();

        if (liveSyncing) {
            this.updateInProgressCandleTask = new UpdateInProgressCandleTask();
            this.updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                    new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE")
            );
        } else {
            this.updateInProgressCandleTask = null;
            this.updateInProgressCandleExecutor = null;
        }

        configureStyle();
        configureAxes();
        configureLoadingOverlay();
        configureAxisExtension();

        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);

        initializeFirstLayout(containerWidth, containerHeight);
        initializeOptionListeners();

        if (liveSyncing) {
            initializeLiveSyncing();
        }
    }

    private void configureStyle() {
        getStyleClass().add("candle-chart");
        setFocusTraversable(true);
        setMinSize(MIN_CHART_WIDTH, MIN_CHART_HEIGHT);
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

        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
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

        extraAxis.setTickLabelsVisible(true);
        extraAxis.setTickMarkVisible(true);
        extraAxis.setMinorTickVisible(false);
        extraAxis.setMouseTransparent(true);
    }

    private void configureLoadingOverlay() {
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);

        progressIndicator.setPrefSize(42, 42);
        progressIndicator.setVisible(false);

        loadingStatusText.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        loadingStatusText.setFill(Color.web("#cbd5e1"));
        loadingStatusText.setMouseTransparent(true);

        noticeClearTimer.setOnFinished(event -> {
            if (!loading.get() && !paging) {
                loadingStatusText.setText("");
            }
        });
    }

    private void configureAxisExtension() {
        extraAxisExtension.setStroke(Color.web("#475569"));
        extraAxisExtension.setStrokeWidth(1);
        extraAxisExtension.setMouseTransparent(true);
    }

    private void initializeFirstLayout(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight) {
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);

        activeSizeListener = sizeListener;
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        ChangeListener<Boolean> firstSizeListener = (_, _, newValue) -> {
            if (!Boolean.TRUE.equals(newValue)) {
                return;
            }

            chartWidth = Math.max(MIN_CHART_WIDTH, containerWidth.getValue().doubleValue());
            chartHeight = Math.max(MIN_CHART_HEIGHT, containerHeight.getValue().doubleValue());

            canvas = new Canvas(Math.max(1, chartWidth - LEFT_AXIS_WIDTH - RIGHT_AXIS_WIDTH), Math.max(1, chartHeight - TIME_AXIS_HEIGHT));
            graphicsContext = canvas.getGraphicsContext2D();

            chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
            chartStackPane.setManaged(false);
            getChildren().addFirst(chartStackPane);

            canvas.setFocusTraversable(true);
            canvas.setOnMouseEntered(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.CROSSHAIR);
                }
            });
            canvas.setOnMouseExited(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.DEFAULT);
                }
            });

            layoutChart();
            initializeEventHandlers();
            startChartDataLoading();

            gotFirstSize.removeListener(activeFirstSizeListener);
            activeFirstSizeListener = null;
        };

        activeFirstSizeListener = firstSizeListener;
        gotFirstSize.addListener(firstSizeListener);
    }

    private void initializeOptionListeners() {
        redrawDebouncer.setOnFinished(event -> {
            drawRequested.set(false);
            drawChartContents(true);
        });

        chartOptions.horizontalGridLinesVisibleProperty().addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.verticalGridLinesVisibleProperty().addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.showVolumeProperty().addListener((observable, oldValue, newValue) -> {
            layoutChart();
            requestChartRedraw();
        });
        chartOptions.alignOpenCloseProperty().addListener((_,
                                                           _, _) -> requestChartRedraw());
    }

    private void initializeEventHandlers() {
        if (canvas == null) {
            return;
        }

        canvas.setOnMouseClicked(event -> {
            canvas.requestFocus();
            if (event.getButton() == MouseButton.SECONDARY) {
                toggleCrosshair();
                event.consume();
            }
        });

        if (eventFiltersInstalled.compareAndSet(false, true)) {
            chartStackPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                chartStackPane.requestFocus();
                canvas.requestFocus();
            });
            chartStackPane.addEventFilter(MouseEvent.MOUSE_RELEASED,
                    _ -> {
                mousePrevX = -1;
                mousePrevY = -1;
                scrollDeltaXSum = 0;
            });
            chartStackPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            chartStackPane.addEventFilter(MouseEvent.MOUSE_MOVED, new MouseMovedHandler());
            chartStackPane.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
                crosshairMouseX = -1;
                crosshairMouseY = -1;
                hoveredPrice = -1;
                hoveredTime = -1;
                requestChartRedraw();
            });
            chartStackPane.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            chartStackPane.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
    }

    private void initializeLiveSyncing() {
        if (updateInProgressCandleExecutor == null || updateInProgressCandleTask == null) {
            return;
        }

        updateInProgressCandleExecutor.execute(() -> {
            if (disposed) return;
            boolean streamingStarted = false;
            try {
                if (exchange.getWebsocketClient() != null
                        && exchange.getWebsocketClient().getInitializationLatch().await(10, SECONDS)
                        && exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                    exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                    streamingStarted = true;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                logger.warn("WebSocket live stream failed for {}; polling fallback will be used.", tradePair, exception);
            }

            if (!streamingStarted && !disposed) {
                startPollingForLiveTrades();
            }

            if (!disposed) {
                updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
            }
        });
    }

    private void startChartDataLoading() {
        if (disposed || !loading.compareAndSet(false, true)) return;

        loadingStartTime = System.currentTimeMillis();
        showLoadingStatus("Fetching chart data...");

        chartTimeoutExecutor.schedule(this::checkLoadingTimeout, LOADING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier(), chartLoadingExecutor)
                .thenAccept(candleDataPager.getCandleDataPreProcessor())
                .whenComplete((result, throwable) -> {
                    loadingStartTime = -1L;
                    loading.set(false);
                    if (disposed) return;
                    if (throwable != null) {
                        logger.error("Error loading chart data for {}", tradePair, throwable);
                        showErrorMessage("Failed to load chart data: %s".formatted(rootMessage(throwable)));
                    }
                });
    }

    private void checkLoadingTimeout() {
        if (!disposed && loading.get() && loadingStartTime > 0 && System.currentTimeMillis() - loadingStartTime >= LOADING_TIMEOUT_MS) {
            showErrorMessage("Loading is taking too long. Check your exchange connection.");
        }
    }

    private void startPollingForLiveTrades() {
        if (disposed || updateInProgressCandleExecutor == null || updateInProgressCandleTask == null) return;
        updateInProgressCandleExecutor.scheduleAtFixedRate(() -> {
            if (disposed) return;
            try {
                Instant since = Instant.now().minusSeconds(Math.max(5, secondsPerCandle));
                exchange.fetchRecentTradesUntil(tradePair, since).thenAccept(trades -> {
                    if (trades != null) {
                        for (Trade trade : trades) updateInProgressCandleTask.accept(trade);
                    }
                });
            } catch (Exception exception) {
                logger.debug("Polling task failed for {}", tradePair, exception);
            }
        }, 3, 3, SECONDS);
    }

    private void setInitialState(List<CandleData> candles) {
        if (!Platform.isFxApplicationThread()) {
            runOnFx(() -> setInitialState(candles));
            return;
        }
        if (candles == null || candles.isEmpty()) {
            showErrorMessage("No candle data available for %s".formatted(tradePair.toString('-')));
            return;
        }
        data.clear();
        for (CandleData candle : sanitizeAndSort(candles)) {
            data.put(candle.getOpenTime(), candle);
        }
        fitLatestReadable();
        hideLoadingIndicator();
        if (updateInProgressCandleTask != null) {
            updateInProgressCandleTask.setReady(true);
        }
    }

    private List<CandleData> sanitizeAndSort(List<CandleData> candles) {
        return candles.stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(CandleData::getOpenTime)).toList();
    }

    private void mergeCandles(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) return;
        for (CandleData candle : sanitizeAndSort(candles)) {
            data.put(candle.getOpenTime(), candle);
        }
        if (!data.isEmpty()) {
            clampVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        }
    }

    private void fitLatestReadable() {
        if (data.isEmpty() || canvas == null) return;
        int total = data.size();
        double width = Math.max(1.0, canvas.getWidth());
        int visible = Math.min(total, TARGET_VISIBLE_CANDLES);
        if (total <= MIN_VISIBLE_CANDLES) visible = total;
        else if (width > 1350) visible = Math.min(total, 90);
        else if (width < 650) visible = Math.min(total, 55);
        setVisibleCandleCount(visible);
        firstVisibleIndex = Math.max(0, total - visibleCandles);
        updateXAxisBoundsFromVisibleWindow();
        recomputeVisiblePriceRange();
        drawChartContents(true);
    }

    public void fitChart() { runOnFx(this::fitLatestReadable); }

    public void jumpToLatestCandle() {
        runOnFx(() -> {
            if (data.isEmpty()) return;
            firstVisibleIndex = Math.max(0, data.size() - visibleCandles);
            updateXAxisBoundsFromVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        });
    }

    public void refreshChart() {
        if (disposed) return;
        data.clear();
        firstVisibleIndex = 0;
        visibleCandles = TARGET_VISIBLE_CANDLES;
        showLoadingStatus("Refreshing chart...");
        startChartDataLoading();
    }

    public void applyAdaptiveScaling() {
        runOnFx(() -> {
            if (data.isEmpty() || canvas == null) return;
            if (visibleCandles < MIN_VISIBLE_CANDLES || visibleCandles > MAX_VISIBLE_CANDLES) fitLatestReadable();
            else {
                updateCandleWidthFromVisibleCount();
                drawChartContents(true);
            }
        });
    }

    private void setVisibleCandleCount(int requestedVisible) {
        visibleCandles = Math.max(1, Math.min(data.size(), requestedVisible));
        updateCandleWidthFromVisibleCount();
    }

    private void updateCandleWidthFromVisibleCount() {
        if (canvas == null || visibleCandles <= 0) {
            candleBodyWidth = DEFAULT_BODY_WIDTH;
            return;
        }
        double slot = Math.max(1.0, canvas.getWidth() / visibleCandles);
        candleBodyWidth = clampInt((int) Math.floor(slot - CANDLE_GAP), MIN_BODY_WIDTH, MAX_BODY_WIDTH);
    }

    private double candleCenterX(int candleIndex) {
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        return snapPixel((candleIndex + 0.5) * slot);
    }

    private void updateXAxisBoundsFromVisibleWindow() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) return;
        CandleData first = visible.getFirst();
        CandleData last = visible.getLast();
        xAxis.setLowerBound(first.getOpenTime());
        xAxis.setUpperBound(last.getOpenTime() + secondsPerCandle);
        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(selectTimePattern()));
    }

    private DateTimeFormatter selectTimePattern() {
        long range = Math.max(secondsPerCandle, (long) (xAxis.getUpperBound() - xAxis.getLowerBound()));
        if (range > 60L * 60L * 24L * 120L) return DateTimeFormatter.ofPattern("MMM yyyy");
        if (range > 60L * 60L * 24L * 2L) return DateTimeFormatter.ofPattern("MM-dd");
        return DateTimeFormatter.ofPattern("HH:mm");
    }

    private void clampVisibleWindow() {
        int total = data.size();
        if (total == 0) { firstVisibleIndex = 0; return; }
        visibleCandles = Math.max(1, Math.min(visibleCandles, total));
        firstVisibleIndex = clampInt(firstVisibleIndex, 0, Math.max(0, total - visibleCandles));
        updateCandleWidthFromVisibleCount();
        updateXAxisBoundsFromVisibleWindow();
    }

    private List<CandleData> visibleCandlesSnapshot() {
        if (data.isEmpty()) return List.of();
        List<CandleData> all = new ArrayList<>(data.values());
        int total = all.size();
        int from = clampInt(firstVisibleIndex, 0, Math.max(0, total - 1));
        int to = clampInt(from + visibleCandles, from + 1, total);
        return new ArrayList<>(all.subList(from, to));
    }

    private void recomputeVisiblePriceRange() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) {
            visibleMinPrice = 0.0; visibleMaxPrice = 1.0; visibleMaxVolume = 1.0; return;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double maxVolume = 0.0;
        for (CandleData candle : visible) {
            if (candle == null || candle.isPlaceHolder()) continue;
            min = Math.min(min, candle.getLowPrice());
            max = Math.max(max, candle.getHighPrice());
            maxVolume = Math.max(maxVolume, candle.getVolume());
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) { min = 0.0; max = 1.0; }
        double delta = max - min;
        if (!Double.isFinite(delta) || delta <= 0.0) delta = Math.max(Math.abs(max) * 0.01, 0.0001);
        double padding = delta * PRICE_PADDING_RATIO;
        visibleMinPrice = Math.max(0.0, min - padding);
        visibleMaxPrice = max + padding;
        visibleMaxVolume = Math.max(1.0, maxVolume);
        yAxis.setLowerBound(visibleMinPrice);
        yAxis.setUpperBound(visibleMaxPrice);
        extraAxis.setLowerBound(0.0);
        extraAxis.setUpperBound(visibleMaxVolume);
    }

    private void layoutChart() {
        if (canvas == null || chartStackPane == null) return;
        double totalW = Math.max(MIN_CHART_WIDTH, getWidth() <= 0 ? chartWidth : getWidth());
        double totalH = Math.max(MIN_CHART_HEIGHT, getHeight() <= 0 ? chartHeight : getHeight());
        chartWidth = totalW;
        chartHeight = totalH;
        double leftAxisWidth = chartOptions.isShowVolume() ? LEFT_AXIS_WIDTH : 0.0;
        canvasX = leftAxisWidth;
        double canvasW = Math.max(1.0, totalW - leftAxisWidth - RIGHT_AXIS_WIDTH);
        double canvasH = Math.max(1.0, totalH - TIME_AXIS_HEIGHT);
        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);
        chartStackPane.resizeRelocate(canvasX, 0, canvasW, canvasH);
        chartStackPane.setMinSize(canvasW, canvasH);
        chartStackPane.setPrefSize(canvasW, canvasH);
        chartStackPane.setMaxSize(canvasW, canvasH);
        xAxis.resizeRelocate(canvasX, canvasH, canvasW, TIME_AXIS_HEIGHT);
        yAxis.resizeRelocate(canvasX + canvasW, 0, RIGHT_AXIS_WIDTH, canvasH);
        double volumeH = canvasH * VOLUME_AREA_RATIO;
        double volumeY = canvasH - volumeH;
        extraAxis.resizeRelocate(0, volumeY, leftAxisWidth, volumeH);
        extraAxis.setVisible(chartOptions.isShowVolume());
        extraAxisExtension.setStartX(leftAxisWidth);
        extraAxisExtension.setEndX(leftAxisWidth);
        extraAxisExtension.setStartY(volumeY);
        extraAxisExtension.setEndY(canvasH);
        extraAxisExtension.setVisible(chartOptions.isShowVolume());
        layoutAxes();
        if (!data.isEmpty()) {
            clampVisibleWindow();
            recomputeVisiblePriceRange();
        }
    }

    private void layoutAxes() {
        xAxis.requestAxisLayout(); yAxis.requestAxisLayout(); extraAxis.requestAxisLayout();
        xAxis.layout(); yAxis.layout(); extraAxis.layout();
    }

    private void drawChartContents(boolean clear) {
        if (!Platform.isFxApplicationThread()) { runOnFx(() -> drawChartContents(clear)); return; }
        if (disposed || canvas == null || graphicsContext == null) return;
        if (clear) clearCanvas();
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) { drawNoDataOverlay(); return; }
        recomputeVisiblePriceRange();
        drawTradingBackground();
        drawGridLines();
        double volumeScale = (canvas.getHeight() * VOLUME_AREA_RATIO) / visibleMaxVolume;
        int highIndex = -1, lowIndex = -1;
        double high = Double.NEGATIVE_INFINITY, low = Double.POSITIVE_INFINITY;
        double lastClose = -1.0;
        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null) continue;
            if (!candle.isPlaceHolder()) {
                if (candle.getHighPrice() > high) { high = candle.getHighPrice(); highIndex = i; }
                if (candle.getLowPrice() < low) { low = candle.getLowPrice(); lowIndex = i; }
            }
            drawCandle(candle, i, volumeScale, lastClose);
            lastClose = candle.getClosePrice();
        }
        drawHighLowMarkers(high, low, highIndex, lowIndex);
        drawChartHeader(visible.getLast());
        if (showPriceLines) drawPriceLines();
        drawCurrentPriceBadge(visible.getLast());
        if (showCrosshair && crosshairMouseX >= 0 && crosshairMouseY >= 0) drawCrosshair();
    }

    private void clearCanvas() {
        graphicsContext.setFill(Color.web("#050b14"));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawTradingBackground() {
        graphicsContext.setFill(Color.rgb(10, 14, 23));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphicsContext.setFill(Color.rgb(15, 20, 31, 0.90));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), HEADER_HEIGHT);
        if (chartOptions.isShowVolume()) {
            double volumeTop = canvas.getHeight() * (1.0 - VOLUME_AREA_RATIO);
            graphicsContext.setFill(Color.rgb(6, 10, 18, 0.54));
            graphicsContext.fillRect(0, volumeTop, canvas.getWidth(), canvas.getHeight() - volumeTop);
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.70));
            graphicsContext.strokeLine(0, volumeTop, canvas.getWidth(), volumeTop);
        }
        graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.70));
        graphicsContext.strokeLine(0, HEADER_HEIGHT, canvas.getWidth(), HEADER_HEIGHT);
    }

    private void drawGridLines() {
        graphicsContext.setLineWidth(1.0);
        if (chartOptions.isHorizontalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.42));
            for (Axis.TickMark<Number> tick : yAxis.getTickMarks()) {
                double y = snapPixel(priceToY(tick.getValue().doubleValue()));
                graphicsContext.strokeLine(0, y, canvas.getWidth(), y);
            }
        }
        if (chartOptions.isVerticalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.25));
            int step = Math.max(1, visibleCandles / 8);
            for (int i = 0; i < visibleCandles; i += step) {
                double x = candleCenterX(i);
                graphicsContext.strokeLine(x, HEADER_HEIGHT, x, canvas.getHeight());
            }
        }
    }

    private void drawCandle(CandleData candle, int visibleIndex, double volumeScale, double lastClose) {
        double centerX = candleCenterX(visibleIndex);
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        double bodyWidth = Math.max(1.0, Math.min(candleBodyWidth, Math.max(1.0, slot - CANDLE_GAP)));
        double wickX = snapPixel(centerX);
        double bodyX = snapPixel(centerX - (bodyWidth / 2.0));
        if (bodyX > canvas.getWidth() || bodyX + bodyWidth < 0) return;
        double open = candle.getOpenPrice();
        if (chartOptions.isAlignOpenClose() && lastClose > 0) open = lastClose;
        if (candle.isPlaceHolder()) {
            double y = priceToY(open);
            graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
            graphicsContext.fillRect(bodyX, snapPixel(y), bodyWidth, 1);
            graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
            graphicsContext.strokeRect(bodyX, snapPixel(y), bodyWidth, 1);
            return;
        }
        double close = candle.getClosePrice();
        double high = Math.max(candle.getHighPrice(), Math.max(open, close));
        double low = Math.min(candle.getLowPrice(), Math.min(open, close));
        boolean bearish = close < open;
        Paint fill = bearish ? BEAR_CANDLE_FILL_COLOR : BULL_CANDLE_FILL_COLOR;
        Paint stroke = bearish ? BEAR_CANDLE_BORDER_COLOR : BULL_CANDLE_BORDER_COLOR;
        double openY = priceToY(open), closeY = priceToY(close), highY = priceToY(high), lowY = priceToY(low);
        double bodyTop = snapPixel(Math.min(openY, closeY));
        double bodyBottom = snapPixel(Math.max(openY, closeY));
        double bodyHeight = Math.max(1.0, bodyBottom - bodyTop);
        graphicsContext.setStroke(stroke);
        graphicsContext.setLineWidth(candleStrokeWidth());
        graphicsContext.strokeLine(wickX, snapPixel(highY), wickX, bodyTop);
        graphicsContext.strokeLine(wickX, bodyTop + bodyHeight, wickX, snapPixel(lowY));
        graphicsContext.setFill(fill);
        graphicsContext.fillRect(bodyX, bodyTop, bodyWidth, bodyHeight);
        graphicsContext.setStroke(stroke);
        graphicsContext.strokeRect(bodyX, bodyTop, bodyWidth, bodyHeight);
        if (chartOptions.isShowVolume()) drawVolumeBar(candle, bodyX, bodyWidth, fill, volumeScale);
    }

    private void drawVolumeBar(CandleData candle, double x, double width, Paint fill, double volumeScale) {
        double maxH = canvas.getHeight() * VOLUME_AREA_RATIO;
        double height = Math.min(maxH, Math.max(1.0, candle.getVolume() * volumeScale));
        double y = canvas.getHeight() - height;
        graphicsContext.setGlobalAlpha(0.55);
        graphicsContext.setFill(fill);
        graphicsContext.fillRect(x, y, width, height);
        graphicsContext.setGlobalAlpha(1.0);
    }

    private void drawHighLowMarkers(double high, double low, int highIndex, int lowIndex) {
        if (highIndex < 0 || lowIndex < 0) return;
        graphicsContext.setFont(canvasNumberFont);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(AXIS_TICK_LABEL_COLOR);
        graphicsContext.setFontSmoothingType(FontSmoothingType.LCD);
        double highX = candleCenterX(highIndex), highY = priceToY(high) - 10;
        double lowX = candleCenterX(lowIndex), lowY = priceToY(low) + 10;
        graphicsContext.setTextAlign(highX < canvas.getWidth() * 0.5 ? TextAlignment.LEFT : TextAlignment.RIGHT);
        graphicsContext.fillText(formatPrice(high), highX < canvas.getWidth() * 0.5 ? highX + 10 : highX - 10, highY);
        graphicsContext.setTextAlign(lowX < canvas.getWidth() * 0.5 ? TextAlignment.LEFT : TextAlignment.RIGHT);
        graphicsContext.fillText(formatPrice(low), lowX < canvas.getWidth() * 0.5 ? lowX + 10 : lowX - 10, lowY);
    }

    private void drawChartHeader(CandleData candle) {
        if (candle == null) return;
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), Math.max(26, Math.min(56, canvas.getWidth() / 12))));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(248, 250, 252, 0.07));
        graphicsContext.fillText(tradePair.toString('/'), canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
        boolean bullish = candle.getClosePrice() >= candle.getOpenPrice();
        Color closeColor = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(226, 232, 240));
        graphicsContext.fillText(tradePair.toString('/'), 14, 18);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(exchange.supportsTimeframe(secondsPerCandle), 112, 18);

        drawHeaderValue("O", candle.getOpenPrice(), 14, Color.rgb(203, 213, 225));
        drawHeaderValue("H", candle.getHighPrice(), 142, Color.rgb(38, 166, 154));
        drawHeaderValue("L", candle.getLowPrice(), 270, Color.rgb(239, 83, 80));
        drawHeaderValue("C", candle.getClosePrice(), 398, closeColor);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText("Vol %s".formatted(compactNumber(candle.getVolume())), 526, 40);
        graphicsContext.fillText("%sChange ".formatted(compactNumber(tradePair.getChange())), 626, 70);

        graphicsContext.setTextAlign(TextAlignment.LEFT);

    }

    private void drawHeaderValue(String label, double value, double x, Color color) {
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(label, x, 40);
        graphicsContext.setFill(color);
        graphicsContext.fillText(formatPrice(value), x + 18, 40);
    }

    private void drawPriceLines() {
        for (PriceLine line : List.copyOf(priceLines)) {
            if (!line.isVisible() || !line.isValid()) continue;
            double y = priceToY(line.getPrice());
            if (y < 0 || y > canvas.getHeight()) continue;
            graphicsContext.setStroke(line.getColor());
            graphicsContext.setLineWidth(line.getLineWidth());
            if (line.isDashed()) graphicsContext.setLineDashes(5, 5);
            graphicsContext.strokeLine(0, snapPixel(y), canvas.getWidth(), snapPixel(y));
            graphicsContext.setLineDashes();
            if (line.isLabelVisible()) {
                String text = (line.getLabel() == null || line.getLabel().isBlank())
                        ? formatPrice(line.getPrice()) : "%s %s".formatted(line.getLabel(), formatPrice(line.getPrice()));
                graphicsContext.setFill(line.getColor());
                graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                graphicsContext.fillText(text, 5, y - 4);
            }
        }
    }

    private void drawCurrentPriceBadge(CandleData candle) {
        double price = candle.getClosePrice();
        double y = priceToY(price);
        if (y < 0 || y > canvas.getHeight()) return;
        boolean bullish = candle.getClosePrice() >= candle.getOpenPrice();
        Color color = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);
        double x = canvas.getWidth() - PRICE_BADGE_WIDTH;
        double badgeY = clamp(y - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2, canvas.getHeight() - PRICE_BADGE_HEIGHT - 2);
        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.40));
        graphicsContext.setLineWidth(1);
        graphicsContext.setLineDashes(5, 5);
        graphicsContext.strokeLine(0, snapPixel(y), x - 4, snapPixel(y));
        graphicsContext.setLineDashes();
        graphicsContext.setFill(color);
        graphicsContext.fillRoundRect(x, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(formatPrice(price), x + PRICE_BADGE_WIDTH / 2.0, badgeY + PRICE_BADGE_HEIGHT / 2.0);
    }

    private void drawCrosshair() {
        graphicsContext.setStroke(Color.web("#94a3b8", 0.58));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setLineDashes(4, 4);
        graphicsContext.strokeLine(crosshairMouseX, 0, crosshairMouseX, canvas.getHeight());
        graphicsContext.strokeLine(0, crosshairMouseY, canvas.getWidth(), crosshairMouseY);
        graphicsContext.setLineDashes();
        double price = yToPrice(crosshairMouseY);
        hoveredPrice = price;
        int index = clampInt((int) Math.floor(crosshairMouseX / Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles))), 0, Math.max(0, visibleCandles - 1));
        List<CandleData> visible = visibleCandlesSnapshot();
        if (index < visible.size()) hoveredTime = visible.get(index).getOpenTime();
        drawLabelBadge(formatPrice(price), canvas.getWidth() - PRICE_BADGE_WIDTH, clamp(crosshairMouseY - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2, canvas.getHeight() - PRICE_BADGE_HEIGHT - 2), PRICE_BADGE_WIDTH);
        if (hoveredTime > 0) {
            String time = CROSSHAIR_TIME_FORMAT.format(Instant.ofEpochSecond(hoveredTime));
            double w = 150;
            drawLabelBadge(time, clamp(crosshairMouseX - w / 2.0, 2, canvas.getWidth() - w - 2), canvas.getHeight() - PRICE_BADGE_HEIGHT - 2, w);
        }
    }

    private void drawLabelBadge(String text, double x, double y, double width) {
        graphicsContext.setFill(Color.rgb(30, 41, 59, 0.98));
        graphicsContext.fillRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.35));
        graphicsContext.strokeRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setFill(Color.rgb(241, 245, 249));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(text, x + width / 2.0, y + PRICE_BADGE_HEIGHT / 2.0);
    }

    private void drawNoDataOverlay() {
        clearCanvas();
        graphicsContext.setFill(Color.web("#94a3b8"));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 16));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText("No visible candle data", canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
    }

    public void changeZoom(ZoomDirection direction) {
        runOnFx(() -> {
            if (data.isEmpty() || canvas == null || direction == null) return;
            int delta = direction == ZoomDirection.IN ? -8 : 8;
            int nextVisible = clampInt(visibleCandles + delta, Math.min(MIN_VISIBLE_CANDLES, data.size()), Math.min(MAX_VISIBLE_CANDLES, data.size()));
            CandleData anchor = latestVisibleCandle().orElse(lastValue());
            int anchorTime = anchor == null ? -1 : anchor.getOpenTime();
            setVisibleCandleCount(nextVisible);
            if (anchorTime > 0) {
                List<CandleData> all = new ArrayList<>(data.values());
                int anchorIndex = indexOfOpenTime(all, anchorTime);
                firstVisibleIndex = clampInt(anchorIndex - visibleCandles + 1, 0, Math.max(0, data.size() - visibleCandles));
            } else {
                firstVisibleIndex = Math.max(0, data.size() - visibleCandles);
            }
            updateXAxisBoundsFromVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        });
    }

    private Optional<CandleData> latestVisibleCandle() {
        List<CandleData> visible = visibleCandlesSnapshot();
        return visible.isEmpty() ? Optional.empty() : Optional.ofNullable(visible.getLast());
    }

    private CandleData lastValue() { return data.isEmpty() ? null : data.lastEntry().getValue(); }

    private int indexOfOpenTime(List<CandleData> candles, int openTime) {
        for (int i = 0; i < candles.size(); i++) if (candles.get(i).getOpenTime() == openTime) return i;
        return candles.size() - 1;
    }

    private void panByCandles(int candles) {
        if (data.isEmpty()) return;
        firstVisibleIndex = clampInt(firstVisibleIndex + candles, 0, Math.max(0, data.size() - visibleCandles));
        updateXAxisBoundsFromVisibleWindow();
        recomputeVisiblePriceRange();
        drawChartContents(true);
    }

    private void requestChartRedraw() {
        if (disposed) return;
        runOnFx(() -> {
            if (drawRequested.compareAndSet(false, true)) redrawDebouncer.playFromStart();
        });
    }

    private double priceToY(double price) {
        double range = Math.max(0.00000001, visibleMaxPrice - visibleMinPrice);
        return canvas.getHeight() - ((price - visibleMinPrice) / range * canvas.getHeight());
    }

    private double yToPrice(double y) {
        double range = Math.max(0.00000001, visibleMaxPrice - visibleMinPrice);
        return visibleMinPrice + ((canvas.getHeight() - y) / canvas.getHeight() * range);
    }

    private double candleStrokeWidth() { return candleBodyWidth <= 3 ? 1.0 : 1.2; }
    private double snapPixel(double value) { return Math.round(value) + 0.5; }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private int clampInt(int value, int min, int max) { return max < min ? min : Math.max(min, Math.min(max, value)); }
    private String formatPrice(double value) { return String.valueOf(value); }

    private String compactNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) return "%.2fB".formatted(value / 1_000_000_000.0);
        if (abs >= 1_000_000) return "%.2fM".formatted(value / 1_000_000.0);
        if (abs >= 1_000) return "%.2fK".formatted(value / 1_000.0);
        return "%.2f".formatted(value);
    }


    public void addPriceLine(double price, Color color, String label) {
        if (!Double.isFinite(price) || price <= 0) return;
        addPriceLine(new PriceLine(price, color == null ? Color.web("#f59e0b") : color, label == null ? "" : label.trim()));
    }

    public void addPriceLine(@Nullable PriceLine priceLine) {
        if (priceLine == null || !priceLine.isValid()) return;
        priceLines.add(priceLine.copy()); requestChartRedraw();
    }

    public void addSupportPriceLine(double price) { addPriceLine(PriceLine.support(price)); }
    public void addResistancePriceLine(double price) { addPriceLine(PriceLine.resistance(price)); }
    public void addStopLossPriceLine(double price) { addPriceLine(PriceLine.stopLoss(price)); }
    public void addTakeProfitPriceLine(double price) { addPriceLine(PriceLine.takeProfit(price)); }
    public void addEntryPriceLine(double price) { addPriceLine(PriceLine.entry(price)); }
    public void setBidPriceLine(double price) { replacePriceLineByLabel(PriceLine.bid(price)); }
    public void setAskPriceLine(double price) { replacePriceLineByLabel(PriceLine.ask(price)); }

    private void replacePriceLineByLabel(@NotNull PriceLine replacement) {
        priceLines.removeIf(line -> Objects.equals(line.getLabel(), replacement.getLabel()));
        priceLines.add(replacement.copy()); requestChartRedraw();
    }

    public List<PriceLine> getPriceLines() { return priceLines.stream().map(PriceLine::copy).toList(); }
    public void removePriceLine(double price) { priceLines.removeIf(line -> Double.compare(line.getPrice(), price) == 0); requestChartRedraw(); }
    public void clearPriceLines() { priceLines.clear(); requestChartRedraw(); }
    public void setPriceLinesVisible(boolean visible) { showPriceLines = visible; requestChartRedraw(); }
    public boolean isPriceLinesVisible() { return showPriceLines; }
    public void togglePriceLines() { setPriceLinesVisible(!showPriceLines); }

    public void setCrosshairVisible(boolean visible) {
        showCrosshair = visible;
        if (!visible) { crosshairMouseX = -1; crosshairMouseY = -1; }
        requestChartRedraw();
    }
    public boolean isCrosshairVisible() { return showCrosshair; }
    public void toggleCrosshair() { setCrosshairVisible(!showCrosshair); }
    public double getLatestClosePrice() { CandleData last = lastValue(); return last == null ? 0.0 : last.getClosePrice(); }

    public void autoTrade() { setAutoTradeEnabled(!autoTradeEnabled); }

    public void setAutoTradeEnabled(boolean enabled) { autoTradeEnabled = enabled; showTransientNotice(enabled ? "Auto-trade enabled" : "Auto-trade disabled"); requestChartRedraw(); }

    public void screenshot() {
        runOnFx(() -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save Chart Screenshot");
                chooser.setInitialFileName("%s-%s.png".formatted(tradePair.toString('-'), SCREENSHOT_FORMAT.format(LocalDateTime.now())));
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
                File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
                if (file == null) return;
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                WritableImage image = snapshot(parameters, null);
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                showTransientNotice("Screenshot saved: %s".formatted(file.getName()));
            } catch (IOException exception) {
                logger.error("Failed to save screenshot", exception);
                showErrorMessage("Screenshot failed: %s".formatted(rootMessage(exception)));
            }
        });
    }

    public void print() {
        runOnFx(() -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) { showErrorMessage("No printer available."); return; }
            boolean proceed = job.showPrintDialog(getScene() == null ? null : getScene().getWindow());
            if (!proceed) return;
            if (job.printPage(this)) { job.endJob(); showTransientNotice("Chart sent to printer."); }
            else showErrorMessage("Chart print failed.");
        });
    }

    private void showLoadingStatus(String message) {
        runOnFx(() -> { noticeClearTimer.stop(); progressIndicator.setVisible(true); loadingStatusText.setFill(Color.WHITE); loadingStatusText.setText(message == null ? "" : message); });
    }
    private void showTransientNotice(String message) {
        runOnFx(() -> { progressIndicator.setVisible(false); loadingStatusText.setFill(Color.web("#fbbf24")); loadingStatusText.setText(message == null ? "" : message); noticeClearTimer.playFromStart(); });
    }
    private void showErrorMessage(String message) {
        runOnFx(() -> { noticeClearTimer.stop(); progressIndicator.setVisible(false); loadingStatusText.setFill(Color.web("#ff6b6b")); loadingStatusText.setText("Error: %s".formatted(message == null ? "Unknown" : message)); });
    }
    private void hideLoadingIndicator() {
        runOnFx(() -> { progressIndicator.setVisible(false); if (noticeClearTimer.getStatus() != javafx.animation.Animation.Status.RUNNING) loadingStatusText.setText(""); });
    }
    private String rootMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error";
        Throwable current = throwable; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank() ? current.getClass().getSimpleName() : current.getMessage();
    }
    private void runOnFx(Runnable runnable) { if (runnable == null) return; if (Platform.isFxApplicationThread()) runnable.run(); else Platform.runLater(runnable); }

    @Override protected void layoutChildren() { layoutChart(); if (!data.isEmpty()) drawChartContents(true); }
    @Override protected double computeMinWidth(double height) { return MIN_CHART_WIDTH; }
    @Override protected double computeMinHeight(double width) { return MIN_CHART_HEIGHT; }
    @Override protected double computePrefWidth(double height) { return chartWidth; }
    @Override protected double computePrefHeight(double width) { return chartHeight; }

    public void dispose() {
        if (disposed) return;
        disposed = true; paging = false; loading.set(false);
        try { redrawDebouncer.stop(); noticeClearTimer.stop(); } catch (Exception ignored) {}
        try { if (exchange.getWebsocketClient() != null) exchange.getWebsocketClient().stopStreamLiveTrades(tradePair); } catch (Exception exception) { logger.debug("Unable to stop live stream for {}", tradePair, exception); }
        shutdownExecutor(updateInProgressCandleExecutor); shutdownExecutor(chartLoadingExecutor); shutdownExecutor(chartTimeoutExecutor);
        runOnFx(() -> { data.clear(); priceLines.clear(); getChildren().clear(); });
    }

    private void shutdownExecutor(ScheduledExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
        try { executor.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        @Override public void accept(List<CandleData> candleData) {
            if (!Platform.isFxApplicationThread()) { runOnFx(() -> accept(candleData)); return; }
            if (data.isEmpty()) setInitialState(candleData); else { mergeCandles(candleData); hideLoadingIndicator(); }
        }
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth, ObservableValue<Number> containerHeight) { super(250, 120, gotFirstSize, containerWidth, containerHeight); }
        @Override public void resize() {
            if (disposed || canvas == null) return;
            layoutChart();
            if (!data.isEmpty()) { updateCandleWidthFromVisibleCount(); clampVisibleWindow(); recomputeVisiblePriceRange(); drawChartContents(true); }
        }
    }

    private class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override public void handle(MouseEvent event) {
            if (disposed || paging || event.getButton() != MouseButton.PRIMARY) return;
            if (mousePrevX < 0) { mousePrevX = event.getScreenX(); mousePrevY = event.getScreenY(); return; }
            double dx = event.getScreenX() - mousePrevX;
            scrollDeltaXSum += dx;
            double slot = Math.max(4.0, canvas.getWidth() / Math.max(1, visibleCandles));
            if (Math.abs(scrollDeltaXSum) >= slot) { panByCandles((int) -Math.signum(scrollDeltaXSum)); scrollDeltaXSum = 0; }
            mousePrevX = event.getScreenX(); mousePrevY = event.getScreenY(); event.consume();
        }
    }

    private class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override public void handle(ScrollEvent event) {
            if (disposed || paging || event.isInertia()) return;
            if (event.isControlDown()) { changeZoom(event.getDeltaY() > 0 ? ZoomDirection.IN : ZoomDirection.OUT); event.consume(); return; }
            panByCandles(event.getDeltaY() > 0 ? -3 : 3); event.consume();
        }
    }

    private class KeyEventHandler implements EventHandler<KeyEvent> {
        @Override public void handle(KeyEvent event) {
            if (disposed) return;
            if (event.getCode() == KeyCode.LEFT) { panByCandles(-1); event.consume(); }
            else if (event.getCode() == KeyCode.RIGHT) { panByCandles(1); event.consume(); }
            else if (event.getCode() == KeyCode.HOME) { jumpToLatestCandle(); event.consume(); }
            else if (event.getCode() == KeyCode.F) { fitChart(); event.consume(); }
            else if (event.getCode() == KeyCode.C) { toggleCrosshair(); event.consume(); }
            else if (event.getCode() == KeyCode.F5) { refreshChart(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.PLUS) { changeZoom(ZoomDirection.IN); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.MINUS) { changeZoom(ZoomDirection.OUT); event.consume(); }
        }
    }

    private class MouseMovedHandler implements EventHandler<MouseEvent> {
        @Override public void handle(MouseEvent event) {
            if (disposed || canvas == null) return;
            crosshairMouseX = clamp(event.getX(), 0, canvas.getWidth());
            crosshairMouseY = clamp(event.getY(), 0, canvas.getHeight());
            requestChartRedraw();
        }
    }

    private class UpdateInProgressCandleTask implements LiveTradesConsumer, Runnable {
        private final BlockingQueue<Trade> liveTradesQueue = new LinkedBlockingQueue<>();
        @Setter private volatile boolean ready;
        @Override public boolean containsKey(TradePair tradePair) { return tradePair != null && tradePair.equals(CandleStickChart.this.tradePair); }
        @Override public void remove(TradePair tradePair) { logger.debug("Removing trade pair from chart consumer: {}", tradePair); }
        @Override public void put(TradePair tradePair) { logger.debug("Registering trade pair for chart consumer: {}", tradePair); }
        @Override public Trade get(TradePair tradePair) { return null; }
        @Override public void accept(Trade trade) { if (trade != null) liveTradesQueue.offer(trade); }
        @Override public void acceptTrades(Trade trade) { accept(trade); }
        @Override public void run() {
            if (!ready || disposed) return;
            List<Trade> trades = new ArrayList<>();
            liveTradesQueue.drainTo(trades);
            if (!trades.isEmpty()) requestChartRedraw();
        }
    }
}
